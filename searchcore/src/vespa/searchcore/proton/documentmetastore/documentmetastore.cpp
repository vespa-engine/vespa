// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastore.h"
#include "documentmetastoresaver.h"
#include "operation_listener.h"
#include "search_context.h"
#include "document_meta_store_versions.h"
#include <vespa/searchcore/proton/bucketdb/bucketsessionbase.h>
#include <vespa/searchcore/proton/bucketdb/joinbucketssession.h>
#include <vespa/searchcore/proton/bucketdb/remove_batch_entry.h>
#include <vespa/searchcore/proton/bucketdb/splitbucketsession.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore");

using document::BucketId;
using document::GlobalId;
using proton::bucketdb::BucketState;
using proton::bucketdb::RemoveBatchEntry;
using proton::documentmetastore::GidToLidMapKey;
using search::AttributeVector;
using search::FileReader;
using search::FileWithHeader;
using search::GrowStrategy;
using search::IAttributeSaveTarget;
using search::LidUsageStats;
using search::attribute::LoadUtils;
using search::attribute::SearchContext;
using search::attribute::SearchContextParams;
using search::fef::TermFieldMatchData;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using storage::spi::Timestamp;
using vespalib::GenerationHandler;
using vespalib::GenerationHeldBase;
using vespalib::IllegalStateException;
using vespalib::MemoryUsage;
using vespalib::btree::BTreeNoLeafData;
using vespalib::make_string;

namespace proton {

namespace documentmetastore {

vespalib::string DOCID_LIMIT("docIdLimit");
vespalib::string VERSION("version");

class Reader {
private:
    FileWithHeader _datFile;
    FileReader<uint32_t> _lidReader;
    FileReader<GlobalId> _gidReader;
    FileReader<uint8_t> _bucketUsedBitsReader;
    FileReader<Timestamp> _timestampReader;
    uint32_t _docIdLimit;
    uint32_t _version;

public:
    explicit Reader(std::unique_ptr<FastOS_FileInterface> datFile);
    ~Reader();

    uint32_t getDocIdLimit() const { return _docIdLimit; }

    uint32_t
    getNextLid() {
        return _lidReader.readHostOrder();
    }

    GlobalId
    getNextGid() {
        return _gidReader.readHostOrder();
    }

    uint8_t
    getNextBucketUsedBits() {
        return std::max(_bucketUsedBitsReader.readHostOrder(), storage::spi::BucketLimits::MinUsedBits);
    }

    Timestamp
    getNextTimestamp() {
        return _timestampReader.readHostOrder();
    }

    uint32_t getNextDocSize() {
        if (_version == NO_DOCUMENT_SIZE_TRACKING_VERSION) {
            return 1;
        }
        uint8_t sizeLow;
        uint16_t sizeHigh;
        _datFile.file().ReadBuf(&sizeLow, sizeof(sizeLow));
        _datFile.file().ReadBuf(&sizeHigh, sizeof(sizeHigh));
        return sizeLow + (static_cast<uint32_t>(sizeHigh) << 8);
    }

    size_t
    getNumElems() const {
        return _datFile.data_size() /
               (sizeof(uint32_t) + sizeof(GlobalId) +
                sizeof(uint8_t) + sizeof(Timestamp::Type) +
                ((_version == NO_DOCUMENT_SIZE_TRACKING_VERSION) ? 0 : 3));
    }
};

Reader::Reader(std::unique_ptr<FastOS_FileInterface> datFile)
    : _datFile(std::move(datFile)),
      _lidReader(&_datFile.file()),
      _gidReader(&_datFile.file()),
      _bucketUsedBitsReader(&_datFile.file()),
      _timestampReader(&_datFile.file()),
      _docIdLimit(0)
{
    _docIdLimit = _datFile.header().getTag(DOCID_LIMIT).asInteger();
    _version = _datFile.header().getTag(VERSION).asInteger();
}
Reader::~Reader() = default;

}

namespace {
class ShrinkBlockHeld : public GenerationHeldBase
{
    DocumentMetaStore &_dms;

public:
    explicit ShrinkBlockHeld(DocumentMetaStore &dms)
        : GenerationHeldBase(0),
          _dms(dms)
    { }

    ~ShrinkBlockHeld() override {
        _dms.unblockShrinkLidSpace();
    }
};

}  // namespace


DocumentMetaStore::DocId
DocumentMetaStore::getFreeLid()
{
    return _lidAlloc.getFreeLid(_metaDataStore.size());
}

DocumentMetaStore::DocId
DocumentMetaStore::peekFreeLid()
{
    return _lidAlloc.peekFreeLid(_metaDataStore.size());
}

void
DocumentMetaStore::ensureSpace(DocId lid)
{
    _metaDataStore.ensure_size(lid+1, RawDocumentMetaData());

    setNumDocs(_metaDataStore.size());
    unsigned int newSize = _metaDataStore.size();
    unsigned int newCapacity = _metaDataStore.capacity();
    _lidAlloc.ensureSpace(newSize, newCapacity);
}

void
DocumentMetaStore::insert(GidToLidMapKey key, const RawDocumentMetaData &metaData)
{
    auto lid = key.get_lid();
    ensureSpace(lid);
    _metaDataStore[lid] = metaData;
    _gidToLidMap.insert(_gid_to_lid_map_write_itr, key, BTreeNoLeafData());
    // flush writes to meta store rcu vector before new entry is visible
    // from frozen root or lid based scan
    std::atomic_thread_fence(std::memory_order_release);
    _lidAlloc.registerLid(lid);
    updateUncommittedDocIdLimit(lid);
    _changesSinceCommit++;
    const BucketState &state =
        _bucketDB->takeGuard()->add(metaData.getGid(),
                      metaData.getBucketId().stripUnused(),
                      metaData.getTimestamp(),
                      metaData.getDocSize(),
                      _subDbType);
    _lidAlloc.updateActiveLids(lid, state.isActive());
    updateCommittedDocIdLimit();
}

bool
DocumentMetaStore::consider_compact_gid_to_lid_map()
{
    if (_gidToLidMap.getAllocator().getNodeStore().has_held_buffers()) {
        return false;
    }
    return _should_compact_gid_to_lid_map;
}

void
DocumentMetaStore::onCommit()
{
    if (consider_compact_gid_to_lid_map()) {
        incGeneration();
        _changesSinceCommit = 0;
        _gidToLidMap.compact_worst(getConfig().getCompactionStrategy());
        _gid_to_lid_map_write_itr_prepare_serial_num = 0u;
        _gid_to_lid_map_write_itr.begin(_gidToLidMap.getRoot());
        incGeneration();
        updateStat(true);
    } else if (_changesSinceCommit > 0) {
        incGeneration();
        _changesSinceCommit = 0;
    }
}

void
DocumentMetaStore::onUpdateStat()
{
    auto &compaction_strategy = getConfig().getCompactionStrategy();
    vespalib::MemoryUsage usage = _metaDataStore.getMemoryUsage();
    usage.incAllocatedBytesOnHold(getGenerationHolder().get_held_bytes());
    size_t bvSize = _lidAlloc.getUsedLidsSize();
    usage.incAllocatedBytes(bvSize);
    usage.incUsedBytes(bvSize);
    auto gid_to_lid_map_memory_usage = _gidToLidMap.getMemoryUsage();
    _should_compact_gid_to_lid_map = compaction_strategy.should_compact_memory(gid_to_lid_map_memory_usage);
    usage.merge(gid_to_lid_map_memory_usage);
    // the free lists are not taken into account here
    updateStatistics(_metaDataStore.size(),
                     _metaDataStore.size(),
                     usage.allocatedBytes(),
                     usage.usedBytes(),
                     usage.deadBytes(),
                     usage.allocatedBytesOnHold());
}

void
DocumentMetaStore::before_inc_generation(generation_t current_gen)
{
    _gidToLidMap.getAllocator().freeze();
    _gidToLidMap.getAllocator().assign_generation(current_gen);
    getGenerationHolder().assign_generation(current_gen);
    updateStat(false);
}

void
DocumentMetaStore::reclaim_memory(generation_t oldest_used_gen)
{
    _gidToLidMap.getAllocator().reclaim_memory(oldest_used_gen);
    _lidAlloc.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

std::unique_ptr<search::AttributeSaver>
DocumentMetaStore::onInitSave(vespalib::stringref fileName)
{
    GenerationHandler::Guard guard(getGuard());
    return std::make_unique<DocumentMetaStoreSaver>
        (std::move(guard), createAttributeHeader(fileName),
         _gidToLidMap.getFrozenView().begin(),
         make_meta_data_view());
}

DocumentMetaStore::DocId
DocumentMetaStore::readNextDoc(documentmetastore::Reader & reader, TreeType::Builder & treeBuilder)
{
    uint32_t lid(reader.getNextLid());
    assert(lid < reader.getDocIdLimit());
    RawDocumentMetaData & meta = _metaDataStore[lid];
    meta.setGid(reader.getNextGid());
    meta.setBucketUsedBits(reader.getNextBucketUsedBits());
    meta.setDocSize(reader.getNextDocSize());
    meta.setTimestamp(reader.getNextTimestamp());
    treeBuilder.insert(GidToLidMapKey(lid, meta.getGid()), BTreeNoLeafData());
    assert(!validLid(lid));
    _lidAlloc.registerLid(lid);
    return lid;
}

bool
DocumentMetaStore::onLoad(vespalib::Executor *)
{
    documentmetastore::Reader reader(LoadUtils::openDAT(*this));
    unload();
    size_t numElems = reader.getNumElems();
    size_t docIdLimit = reader.getDocIdLimit();
    _metaDataStore.unsafe_reserve(std::max(numElems, docIdLimit));
    TreeType::Builder treeBuilder(_gidToLidMap.getAllocator());
    assert(docIdLimit > 0); // lid 0 is reserved
    ensureSpace(docIdLimit - 1);

    // insert gids (already sorted)
    if (numElems > 0) {
        DocId lid = readNextDoc(reader, treeBuilder);
        const RawDocumentMetaData * meta = &_metaDataStore[lid];
        BucketId prevId(meta->getBucketId());
        BucketState state;
        state.add(meta->getGid(), meta->getTimestamp(), meta->getDocSize(), _subDbType);
        for (size_t i = 1; i < numElems; ++i) {
            lid = readNextDoc(reader, treeBuilder);
            meta = &_metaDataStore[lid];
            BucketId bucketId = meta->getBucketId();
            if (prevId != bucketId) {
                _bucketDB->takeGuard()->add(prevId, state);
                state = BucketState();
                prevId = bucketId;
            }
            state.add(meta->getGid(), meta->getTimestamp(), meta->getDocSize(), _subDbType);
        }
        _bucketDB->takeGuard()->add(prevId, state);
    }
    _gidToLidMap.assign(treeBuilder);
    _gidToLidMap.getAllocator().freeze(); // create initial frozen tree
    generation_t generation = getGenerationHandler().getCurrentGeneration();
    _gidToLidMap.getAllocator().assign_generation(generation);

    setNumDocs(_metaDataStore.size());
    setCommittedDocIdLimit(_metaDataStore.size());

    return true;
}

template <typename TreeView>
typename TreeView::Iterator
DocumentMetaStore::lowerBound(const BucketId &bucketId,
                              const TreeView &treeView) const
{
    document::GlobalId first(document::GlobalId::calculateFirstInBucket(bucketId));
    KeyComp lowerComp(first, acquire_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(first);
    return treeView.lowerBound(find_key, lowerComp);
}

template <typename TreeView>
typename TreeView::Iterator
DocumentMetaStore::upperBound(const BucketId &bucketId,
                              const TreeView &treeView) const
{
    document::GlobalId last(document::GlobalId::calculateLastInBucket(bucketId));
    KeyComp upperComp(last, acquire_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(last);
    return treeView.upperBound(find_key, upperComp);
}

void
DocumentMetaStore::updateMetaDataAndBucketDB(const GlobalId &gid,
                                             DocId lid,
                                             const RawDocumentMetaData &newMetaData)
{
    RawDocumentMetaData &oldMetaData = _metaDataStore[lid];
    _bucketDB->takeGuard()->modify(gid,
                     oldMetaData.getBucketId().stripUnused(),
                     oldMetaData.getTimestamp(), oldMetaData.getDocSize(),
                     newMetaData.getBucketId().stripUnused(),
                     newMetaData.getTimestamp(), newMetaData.getDocSize(),
                     _subDbType);
    oldMetaData.setBucketId(newMetaData.getBucketId());
    oldMetaData.setDocSize(newMetaData.getDocSize());
    std::atomic_thread_fence(std::memory_order_release);
    oldMetaData.setTimestamp(newMetaData.getTimestamp());
}


namespace {

void
unloadBucket(bucketdb::BucketDBOwner &db, const BucketId &id, const BucketState &delta)
{
    if (!id.valid()) {
        assert(delta.empty());
        return;
    }
    assert(!delta.empty());
    db.takeGuard()->unloadBucket(id, delta);
}

}

void
DocumentMetaStore::unload()
{
    TreeType::Iterator itr = _gidToLidMap.begin();
    if ( ! itr.valid() ) return;
    BucketId prev;
    BucketState prevDelta;
    for (; itr.valid(); ++itr) {
        uint32_t lid = itr.getKey().get_lid();
        assert(validLid(lid));
        RawDocumentMetaData &metaData = _metaDataStore[lid];
        BucketId bucketId = metaData.getBucketId();
        if (prev != bucketId) {
            unloadBucket(*_bucketDB, prev, prevDelta);
            prevDelta = BucketState();
            prev = bucketId;
        }
        prevDelta.add(metaData.getGid(), metaData.getTimestamp(), metaData.getDocSize(), _subDbType);
    }
    unloadBucket(*_bucketDB, prev, prevDelta);
}

DocumentMetaStore::DocumentMetaStore(BucketDBOwnerSP bucketDB)
    : DocumentMetaStore(std::move(bucketDB), getFixedName())
{}

DocumentMetaStore::DocumentMetaStore(BucketDBOwnerSP bucketDB, const vespalib::string &name)
    : DocumentMetaStore(std::move(bucketDB), name, search::GrowStrategy())
{}
DocumentMetaStore::DocumentMetaStore(BucketDBOwnerSP bucketDB,
                                     const vespalib::string &name,
                                     const GrowStrategy &grow,
                                     SubDbType subDbType)
    : DocumentMetaStoreAttribute(name),
      _metaDataStore(grow, getGenerationHolder()),
      _gidToLidMap(),
      _gid_to_lid_map_write_itr(vespalib::datastore::EntryRef(), _gidToLidMap.getAllocator()),
      _gid_to_lid_map_write_itr_prepare_serial_num(0u),
      _lidAlloc(_metaDataStore.size(), _metaDataStore.capacity(), getGenerationHolder()),
      _bucketDB(std::move(bucketDB)),
      _shrinkLidSpaceBlockers(0),
      _subDbType(subDbType),
      _trackDocumentSizes(true),
      _changesSinceCommit(0),
      _op_listener(),
      _should_compact_gid_to_lid_map(false)
{
    ensureSpace(0);         // lid 0 is reserved
    setCommittedDocIdLimit(1u);         // lid 0 is reserved
    _gidToLidMap.getAllocator().freeze(); // create initial frozen tree
    generation_t generation = getGenerationHandler().getCurrentGeneration();
    _gidToLidMap.getAllocator().assign_generation(generation);
    updateStat(true);
}

DocumentMetaStore::~DocumentMetaStore()
{
    // TODO: Properly notify about modified buckets when using shared bucket db
    // between document types
    unload();
    getGenerationHolder().reclaim_all();
    assert(get_shrink_lid_space_blockers() == 0);
}

DocumentMetaStore::Result
DocumentMetaStore::inspectExisting(const GlobalId &gid, uint64_t prepare_serial_num)
{
    Result res;
    KeyComp comp(gid, get_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    auto& itr = _gid_to_lid_map_write_itr;
    itr.lower_bound(_gidToLidMap.getRoot(), find_key, comp);
    _gid_to_lid_map_write_itr_prepare_serial_num = prepare_serial_num;
    bool found = itr.valid() && !comp(find_key, itr.getKey());
    if (found) {
        res.setLid(itr.getKey().get_lid());
        res.fillPrev(_metaDataStore[res.getLid()].getTimestamp());
        res.markSuccess();
    }
    return res;
}

DocumentMetaStore::Result
DocumentMetaStore::inspect(const GlobalId &gid, uint64_t prepare_serial_num)
{
    assert(_lidAlloc.isFreeListConstructed());
    Result res;
    KeyComp comp(gid, get_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    auto& itr = _gid_to_lid_map_write_itr;
    itr.lower_bound(_gidToLidMap.getRoot(), find_key, comp);
    _gid_to_lid_map_write_itr_prepare_serial_num = prepare_serial_num;
    bool found = itr.valid() && !comp(find_key, itr.getKey());
    if (!found) {
        DocId myLid = peekFreeLid();
        res.setLid(myLid);
        res.markSuccess();
    } else {
        res.setLid(itr.getKey().get_lid());
        res.fillPrev(_metaDataStore[res.getLid()].getTimestamp());
        res.markSuccess();
    }
    return res;
}

DocumentMetaStore::Result
DocumentMetaStore::put(const GlobalId &gid, const BucketId &bucketId, Timestamp timestamp,
                       uint32_t docSize, DocId lid, uint64_t prepare_serial_num)
{
    Result res;
    RawDocumentMetaData metaData(gid, bucketId, storage::spi::Timestamp(timestamp), docSize);
    KeyComp comp(metaData, get_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    auto& itr = _gid_to_lid_map_write_itr;
    if (prepare_serial_num == 0u || _gid_to_lid_map_write_itr_prepare_serial_num != prepare_serial_num) {
        itr.lower_bound(_gidToLidMap.getRoot(), find_key, comp);
    }
    bool found = itr.valid() && !comp(find_key, itr.getKey());
    if (!found) {
        if (validLid(lid)) {
            throw IllegalStateException(
                    make_string(
                            "document meta data store"
                            " or transaction log is corrupt,"
                            " cannot put"
                            " document with lid '%u' and gid '%s',"
                            " gid not found, but lid is used"
                            " by another gid '%s'",
                            lid,
                            gid.toString().c_str(),
                            _metaDataStore[lid].getGid().toString().c_str()));
        }
        if (_lidAlloc.isFreeListConstructed()) {
            DocId freeLid = getFreeLid();
            assert(freeLid == lid);
            (void) freeLid;
        }
        insert(GidToLidMapKey(lid, find_key.get_gid_key()), metaData);
        res.setLid(lid);
        res.markSuccess();
    } else if (lid != itr.getKey().get_lid()) {
        throw IllegalStateException(
                make_string(
                        "document meta data store"
                        " or transaction log is corrupt,"
                        " cannot put"
                        " document with lid '%u' and gid '%s',"
                        " gid found, but using another lid '%u'",
                        lid,
                        gid.toString().c_str(),
                        itr.getKey().get_lid()));
    } else {
        res.setLid(lid);
        res.fillPrev(_metaDataStore[lid].getTimestamp());
        updateMetaDataAndBucketDB(gid, lid, metaData);
        res.markSuccess();
    }
    return res;
}

bool
DocumentMetaStore::updateMetaData(DocId lid, const BucketId &bucketId, Timestamp timestamp)
{
    if (!validLid(lid)) {
        return false;
    }
    RawDocumentMetaData &metaData = _metaDataStore[lid];
    _bucketDB->takeGuard()->modify(metaData.getGid(),
                     metaData.getBucketId().stripUnused(),
                     metaData.getTimestamp(),
                     metaData.getDocSize(),
                     bucketId.stripUnused(),
                     storage::spi::Timestamp(timestamp),
                     metaData.getDocSize(),
                     _subDbType);
    metaData.setBucketId(bucketId);
    std::atomic_thread_fence(std::memory_order_release);
    metaData.setTimestamp(storage::spi::Timestamp(timestamp));
    return true;
}

RawDocumentMetaData
DocumentMetaStore::removeInternal(DocId lid, uint64_t prepare_serial_num)
{
    const GlobalId & gid = getRawGid(lid);
    KeyComp comp(gid, get_unbound_meta_data_view());
    GidToLidMapKey find_key(lid, gid);
    auto& itr = _gid_to_lid_map_write_itr;
    if (prepare_serial_num == 0u || _gid_to_lid_map_write_itr_prepare_serial_num != prepare_serial_num) {
        itr.lower_bound(_gidToLidMap.getRoot(), find_key, comp);
    }
    if (!itr.valid() || comp(find_key, itr.getKey())) {
        throw IllegalStateException(make_string(
                        "document meta data store corrupted,"
                        " cannot remove"
                        " document with lid '%u' and gid '%s'",
                        lid, gid.toString().c_str()));
    }
    _gidToLidMap.remove(itr);
    _lidAlloc.unregisterLid(lid);
    return _metaDataStore[lid];
}

bool
DocumentMetaStore::remove(DocId lid, uint64_t prepare_serial_num)
{
    if (!validLid(lid)) {
        return false;
    }
    RawDocumentMetaData meta = removeInternal(lid, prepare_serial_num);
    _bucketDB->takeGuard()->remove(meta.getGid(), meta.getBucketId().stripUnused(),
                                   meta.getTimestamp(), meta.getDocSize(), _subDbType);
    _changesSinceCommit++;
    if (_op_listener) {
        _op_listener->notify_remove();
    }
    return true;
}

void
DocumentMetaStore::removes_complete(const std::vector<DocId>& lids)
{
    _lidAlloc.holdLids(lids, _metaDataStore.size(), getCurrentGeneration());
    incGeneration();
}

void
DocumentMetaStore::move(DocId fromLid, DocId toLid, uint64_t prepare_serial_num)
{
    assert(fromLid != 0);
    assert(toLid != 0);
    assert(fromLid > toLid);
    assert(fromLid < getCommittedDocIdLimit());
    assert(!validLid(toLid));
    assert(validLid(fromLid));
    _lidAlloc.moveLidBegin(fromLid, toLid);
    _metaDataStore[toLid] = _metaDataStore[fromLid];
    const GlobalId & gid = getRawGid(fromLid);
    KeyComp comp(gid, get_unbound_meta_data_view());
    GidToLidMapKey find_key(fromLid, gid);
    auto& itr = _gid_to_lid_map_write_itr;
    if (prepare_serial_num == 0u || _gid_to_lid_map_write_itr_prepare_serial_num != prepare_serial_num) {
        itr.lower_bound(_gidToLidMap.getRoot(), find_key, comp);
    }
    assert(itr.valid());
    assert(itr.getKey().get_lid() == fromLid);
    _gidToLidMap.thaw(itr);
    itr.writeKey(GidToLidMapKey(toLid, find_key.get_gid_key()));
    _lidAlloc.moveLidEnd(fromLid, toLid);
    _changesSinceCommit++;
}

void
DocumentMetaStore::remove_batch_internal_btree(std::vector<LidAndRawDocumentMetaData>& removed)
{
    // Sort removed array to same order as entries in gid to lid map b-tree
    GlobalId::BucketOrderCmp cmp;
    std::sort(removed.begin(), removed.end(), [cmp](auto& lhs, auto& rhs) { return cmp(lhs.second.getGid(), rhs.second.getGid()); });

    _gid_to_lid_map_write_itr_prepare_serial_num = 0u;
    auto& itr = _gid_to_lid_map_write_itr;
    itr.begin(_gidToLidMap.getRoot());
    for (const auto& lid_and_meta : removed) {
        auto lid = lid_and_meta.first;
        auto& meta = lid_and_meta.second;
        const GlobalId& gid = meta.getGid();
        KeyComp comp(gid, get_unbound_meta_data_view());
        GidToLidMapKey find_key(lid, gid);
        if (itr.valid() && comp(itr.getKey(), find_key)) {
            itr.binarySeek(find_key, comp);
        }
        if (!itr.valid() || comp(find_key, itr.getKey())) {
            throw IllegalStateException(make_string(
                            "document meta data store corrupted,"
                            " cannot remove"
                            " document with lid '%u' and gid '%s'",
                            lid, gid.toString().c_str()));
        }
        _gidToLidMap.remove(itr);
    }
}

void
DocumentMetaStore::removeBatch(const std::vector<DocId> &lidsToRemove, const uint32_t docIdLimit)
{
    std::vector<LidAndRawDocumentMetaData> removed;
    removed.reserve(lidsToRemove.size());
    for (const auto &lid : lidsToRemove) {
        assert(lid > 0 && lid < docIdLimit);
        (void) docIdLimit;

        assert(validLid(lid));
        removed.emplace_back(lid, _metaDataStore[lid]);
    }
    remove_batch_internal_btree(removed);
    _lidAlloc.unregister_lids(lidsToRemove);
    {
        std::vector<RemoveBatchEntry> bdb_removed;
        bdb_removed.reserve(removed.size());
        for (const auto& lid_and_meta : removed) {
            auto& meta = lid_and_meta.second;
            bdb_removed.emplace_back(meta.getGid(), meta.getBucketId().stripUnused(),
                                     meta.getTimestamp(), meta.getDocSize());
        }
        bucketdb::Guard bucketGuard = _bucketDB->takeGuard();
        bucketGuard->remove_batch(bdb_removed, _subDbType);
    }
    ++_changesSinceCommit;
    if (_op_listener) {
        _op_listener->notify_remove_batch();
    }
}

bool
DocumentMetaStore::getGid(DocId lid, GlobalId &gid) const
{
    if (!validLid(lid)) {
        return false;
    }
    gid = getRawGid(lid);
    return true;
}

bool
DocumentMetaStore::getGidEvenIfMoved(DocId lid, GlobalId &gid) const
{
    if (!validButMaybeUnusedLid(lid)) {
        return false;
    }
    gid = getRawGid(lid);
    if ( ! validLid(lid) ) {
        uint32_t newLid(0);
        if (!getLid(gid, newLid)) {
            return false;
        }
    }
    return true;
}

bool
DocumentMetaStore::getLid(const GlobalId &gid, DocId &lid) const
{
    GlobalId value(gid);
    KeyComp comp(value, acquire_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    TreeType::ConstIterator itr = _gidToLidMap.getFrozenView().find(find_key, comp);
    if (!itr.valid()) {
        return false;
    }
    lid = itr.getKey().get_lid();
    return true;
}

void
DocumentMetaStore::constructFreeList()
{
    _lidAlloc.constructFreeList(_metaDataStore.size());
    incGeneration();
    _lidAlloc.setFreeListConstructed();
}

search::DocumentMetaData
DocumentMetaStore::getMetaData(const GlobalId &gid) const
{
    DocId lid = 0;
    if (!getLid(gid, lid) || !validLid(lid)) {
        return {};
    }
    const RawDocumentMetaData &raw = getRawMetaData(lid);
    Timestamp timestamp(raw.getTimestamp());
    std::atomic_thread_fence(std::memory_order_acquire);
    return {lid, timestamp, raw.getBucketId(), raw.getGid(), _subDbType == SubDbType::REMOVED};
}

void
DocumentMetaStore::getMetaData(const BucketId &bucketId,
                               search::DocumentMetaData::Vector &result) const
{
    TreeType::FrozenView frozenTreeView = _gidToLidMap.getFrozenView();
    TreeType::ConstIterator itr = lowerBound(bucketId, frozenTreeView);
    TreeType::ConstIterator end = upperBound(bucketId, frozenTreeView);
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey().get_lid();
        if (validLid(lid)) {
            const RawDocumentMetaData &rawData = getRawMetaData(lid);
            if (bucketId.getUsedBits() != rawData.getBucketUsedBits())
                continue; // Wrong bucket (due to overlapping buckets)
            Timestamp timestamp(rawData.getTimestamp());
            std::atomic_thread_fence(std::memory_order_acquire);
            result.emplace_back(lid, timestamp, rawData.getBucketId(), rawData.getGid(),_subDbType == SubDbType::REMOVED);
        }
    }
}

LidUsageStats
DocumentMetaStore::getLidUsageStats() const
{
    return {getCommittedDocIdLimit(), getNumUsedLids(), _lidAlloc.getLowestFreeLid(), _lidAlloc.getHighestUsedLid()};
}

Blueprint::UP
DocumentMetaStore::createWhiteListBlueprint() const
{
    return _lidAlloc.createWhiteListBlueprint();
}

std::unique_ptr<SearchContext>
DocumentMetaStore::getSearch(std::unique_ptr<search::QueryTermSimple> qTerm, const SearchContextParams &) const
{
    return std::make_unique<documentmetastore::SearchContext>(std::move(qTerm), *this);
}

DocumentMetaStore::ConstIterator
DocumentMetaStore::beginFrozen() const
{
    return _gidToLidMap.getFrozenView().begin();
}

DocumentMetaStore::Iterator
DocumentMetaStore::begin() const
{
    // Called by writer thread
    return _gidToLidMap.begin();
}

DocumentMetaStore::Iterator
DocumentMetaStore::lowerBound(const BucketId &bucketId) const
{
    // Called by writer thread
    return lowerBound(bucketId, _gidToLidMap);
}

DocumentMetaStore::Iterator
DocumentMetaStore::upperBound(const BucketId &bucketId) const
{
    // Called by writer thread
    return upperBound(bucketId, _gidToLidMap);
}

DocumentMetaStore::Iterator
DocumentMetaStore::lowerBound(const GlobalId &gid) const
{
    // Called by writer thread
    KeyComp comp(gid, get_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    return _gidToLidMap.lowerBound(find_key, comp);
}

DocumentMetaStore::Iterator
DocumentMetaStore::upperBound(const GlobalId &gid) const
{
    // Called by writer thread
    KeyComp comp(gid, get_unbound_meta_data_view());
    auto find_key = GidToLidMapKey::make_find_key(gid);
    return _gidToLidMap.upperBound(find_key, comp);
}

void
DocumentMetaStore::getLids(const BucketId &bucketId, std::vector<DocId> &lids)
{
    // Called by writer thread
    TreeType::Iterator itr = lowerBound(bucketId);
    TreeType::Iterator end = upperBound(bucketId);
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey().get_lid();
        assert(validLid(lid));
        const RawDocumentMetaData &metaData = getRawMetaData(lid);
        uint8_t bucketUsedBits = metaData.getBucketUsedBits();
        assert(BucketId::validUsedBits(bucketUsedBits));
        if (bucketUsedBits != bucketId.getUsedBits())
            continue;   // Skip document belonging to overlapping bucket
        lids.push_back(lid);
    }
}

bucketdb::BucketDeltaPair
DocumentMetaStore::handleSplit(const bucketdb::SplitBucketSession &session)
{
    const BucketId &source(session.getSource());
    const BucketId &target1(session.getTarget1());
    const BucketId &target2(session.getTarget2());

    if (_subDbType == SubDbType::READY) {
        if (session.mustFixupTarget1ActiveLids()) {
            updateActiveLids(target1, session.getSourceActive());
        }
        if (session.mustFixupTarget2ActiveLids()) {
            updateActiveLids(target2, session.getSourceActive());
        }
    }

    TreeType::Iterator itr = lowerBound(source);
    TreeType::Iterator end = upperBound(source);
    bucketdb::BucketDeltaPair deltas;
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey().get_lid();
        assert(validLid(lid));
        RawDocumentMetaData &metaData = _metaDataStore[lid];
        uint8_t bucketUsedBits = metaData.getBucketUsedBits();
        assert(BucketId::validUsedBits(bucketUsedBits));
        if (bucketUsedBits == source.getUsedBits()) {
            BucketId t1(metaData.getGid().convertToBucketId());
            BucketId t2(t1);
            if (target1.valid()) {
                t1.setUsedBits(target1.getUsedBits());
            }
            if (target2.valid()) {
                t2.setUsedBits(target2.getUsedBits());
            }
            if (target1.valid() && t1 == target1) {
                metaData.setBucketUsedBits(target1.getUsedBits());
                deltas._delta1.add(metaData.getGid(),
                                   metaData.getTimestamp(),
                                   metaData.getDocSize(),
                                   _subDbType);
            } else if (target2.valid() && t2 == target2) {
                metaData.setBucketUsedBits(target2.getUsedBits());
                deltas._delta2.add(metaData.getGid(),
                                   metaData.getTimestamp(),
                                   metaData.getDocSize(),
                                   _subDbType);
            }
        }
    }
    return deltas;
    // Caller can remove source bucket if empty
}


bucketdb::BucketDeltaPair
DocumentMetaStore::handleJoin(const bucketdb::JoinBucketsSession &session)
{
    const BucketId &source1(session.getSource1());
    const BucketId &source2(session.getSource2());
    const BucketId &target(session.getTarget());

    TreeType::Iterator itr = lowerBound(target);
    TreeType::Iterator end = upperBound(target);
    bucketdb::BucketDeltaPair deltas;
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey().get_lid();
        assert(validLid(lid));
        RawDocumentMetaData &metaData = _metaDataStore[lid];
        assert(BucketId::validUsedBits(metaData.getBucketUsedBits()));
        BucketId s(metaData.getBucketId());
        if (source1.valid() && s == source1) {
            metaData.setBucketUsedBits(target.getUsedBits());
            deltas._delta1.add(metaData.getGid(), metaData.getTimestamp(), metaData.getDocSize(), _subDbType);
        } else if (source2.valid() && s == source2) {
            metaData.setBucketUsedBits(target.getUsedBits());
            deltas._delta2.add(metaData.getGid(), metaData.getTimestamp(), metaData.getDocSize(), _subDbType);
        }
    }
    if (_subDbType == SubDbType::READY) {
        bool movedSource1Docs = deltas._delta1.getReadyCount() != 0;
        bool movedSource2Docs = deltas._delta2.getReadyCount() != 0;
        if (session.mustFixupTargetActiveLids(movedSource1Docs,
                                              movedSource2Docs)) {
            updateActiveLids(target, session.getWantTargetActive());
        }
    }
    return deltas;
    // Caller can remove source buckets if they are empty
}


void
DocumentMetaStore::setBucketState(const BucketId &bucketId, bool active)
{
    updateActiveLids(bucketId, active);
    _bucketDB->takeGuard()->setBucketState(bucketId, active);
}

void
DocumentMetaStore::updateActiveLids(const BucketId &bucketId, bool active)
{
    TreeType::Iterator itr = lowerBound(bucketId);
    TreeType::Iterator end = upperBound(bucketId);
    uint8_t bucketUsedBits = bucketId.getUsedBits();
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey().get_lid();
        assert(validLid(lid));
        RawDocumentMetaData &metaData = _metaDataStore[lid];
        if (metaData.getBucketUsedBits() != bucketUsedBits) {
            continue;
        }
        _lidAlloc.updateActiveLids(lid, active);
    }
}

void
DocumentMetaStore::populateActiveBuckets(BucketId::List buckets)
{
    BucketId::List fixupBuckets = _bucketDB->takeGuard()->populateActiveBuckets(std::move(buckets));

    for (const auto &bucketId : fixupBuckets) {
        updateActiveLids(bucketId, true);
    }
}

void
DocumentMetaStore::clearDocs(DocId lidLow, DocId lidLimit, bool)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    _lidAlloc.clearDocs(lidLow, lidLimit);
}

void
DocumentMetaStore::compactLidSpace(uint32_t wantedLidLimit)
{
    AttributeVector::compactLidSpace(wantedLidLimit);
    set_shrink_lid_space_blockers(get_shrink_lid_space_blockers() + 1);
}

void
DocumentMetaStore::holdUnblockShrinkLidSpace()
{
    assert(get_shrink_lid_space_blockers() > 0);
    auto hold = std::make_unique<ShrinkBlockHeld>(*this);
    getGenerationHolder().insert(std::move(hold));
    incGeneration();
}

void
DocumentMetaStore::unblockShrinkLidSpace()
{
    auto shrink_lid_space_blockers = get_shrink_lid_space_blockers();
    assert(shrink_lid_space_blockers > 0);
    set_shrink_lid_space_blockers(shrink_lid_space_blockers - 1);
}

bool
DocumentMetaStore::canShrinkLidSpace() const
{
    return AttributeVector::canShrinkLidSpace() &&
        get_shrink_lid_space_blockers() == 0;
}

void
DocumentMetaStore::set_operation_listener(documentmetastore::OperationListener::SP op_listener)
{
    _op_listener = std::move(op_listener);
}

void
DocumentMetaStore::onShrinkLidSpace()
{
    uint32_t committedDocIdLimit = this->getCommittedDocIdLimit();
    _lidAlloc.shrinkLidSpace(committedDocIdLimit);
    _metaDataStore.shrink(committedDocIdLimit);
    setNumDocs(committedDocIdLimit);
}

size_t
DocumentMetaStore::getEstimatedShrinkLidSpaceGain() const
{
    size_t canFree = 0;
    if (canShrinkLidSpace()) {
        uint32_t committedDocIdLimit = getCommittedDocIdLimit();
        uint32_t numDocs = getNumDocs();
        if (committedDocIdLimit < numDocs) {
            canFree = sizeof(RawDocumentMetaData) *
                      (numDocs - committedDocIdLimit);
        }
    }
    return canFree;
}

BucketId
DocumentMetaStore::getBucketOf(const vespalib::GenerationHandler::Guard &, uint32_t lid) const
{
    if (__builtin_expect(validLidFast(lid, getCommittedDocIdLimit()), true)) {
        return getRawMetaData(lid).getBucketId();
    }
    return {};
}

vespalib::GenerationHandler::Guard
DocumentMetaStore::getGuard() const
{
    const vespalib::GenerationHandler & genHandler = getGenerationHandler();
    return genHandler.takeGuard();
}

uint64_t
DocumentMetaStore::getEstimatedSaveByteSize() const
{
    uint32_t numDocs = getNumUsedLids();
    return minHeaderLen + numDocs * entrySize;
}

uint32_t
DocumentMetaStore::getVersion() const
{
    return _trackDocumentSizes ? documentmetastore::DOCUMENT_SIZE_TRACKING_VERSION : documentmetastore::NO_DOCUMENT_SIZE_TRACKING_VERSION;
}

void
DocumentMetaStore::foreach(const search::IGidToLidMapperVisitor &visitor) const
{
    beginFrozen().foreach_key([this,&visitor](GidToLidMapKey key)
                              { visitor.visit(getRawMetaData(key.get_lid()).getGid(), key.get_lid()); });
}

long
DocumentMetaStore::onSerializeForAscendingSort(DocId lid, void * serTo, long available, const search::common::BlobConverter *) const {
    if ( ! validLid(lid)) return 0;
    if (available < document::GlobalId::LENGTH) return -1;
    memcpy(serTo, getRawMetaData(lid).getGid().get(), document::GlobalId::LENGTH);
    return document::GlobalId::LENGTH;
}

long
DocumentMetaStore::onSerializeForDescendingSort(DocId lid, void * serTo, long available, const search::common::BlobConverter *) const {
    if ( ! validLid(lid)) return 0;
    if (available < document::GlobalId::LENGTH) return -1;
    const auto * src(static_cast<const uint8_t *>(getRawMetaData(lid).getGid().get()));
    auto * dst = static_cast<uint8_t *>(serTo);
    for (size_t i(0); i < document::GlobalId::LENGTH; ++i) {
        dst[i] = 0xff - src[i];
    }
    return document::GlobalId::LENGTH;
}

}  // namespace proton

namespace vespalib::btree {

template class BTreeIteratorBase<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS, BTreeDefaultTraits::LEAF_SLOTS, BTreeDefaultTraits::PATH_SIZE>;

template class BTreeConstIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

template class BTreeIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

}
