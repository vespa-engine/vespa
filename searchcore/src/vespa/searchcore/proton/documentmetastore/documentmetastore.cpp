// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastore.h"
#include "documentmetastoresaver.h"
#include "operation_listener.h"
#include "search_context.h"
#include "document_meta_store_versions.h"
#include <vespa/fastos/file.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/searchcore/proton/bucketdb/bucketsessionbase.h>
#include <vespa/searchcore/proton/bucketdb/joinbucketssession.h>
#include <vespa/searchcore/proton/bucketdb/splitbucketsession.h>
#include <vespa/searchlib/attribute/load_utils.h>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore");

using document::BucketId;
using document::GlobalId;
using proton::bucketdb::BucketState;
using proton::documentmetastore::GidToLidMapKey;
using search::AttributeVector;
using search::FileReader;
using search::GrowStrategy;
using search::IAttributeSaveTarget;
using search::LidUsageStats;
using search::attribute::LoadUtils;
using search::attribute::SearchContextParams;
using vespalib::btree::BTreeNoLeafData;
using search::fef::TermFieldMatchData;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using storage::spi::Timestamp;
using vespalib::GenerationHandler;
using vespalib::GenerationHeldBase;
using vespalib::IllegalStateException;
using vespalib::MemoryUsage;
using vespalib::make_string;

namespace proton {

namespace documentmetastore {

vespalib::string DOCID_LIMIT("docIdLimit");
vespalib::string VERSION("version");

class Reader {
private:
    std::unique_ptr<FastOS_FileInterface> _datFile;
    FileReader<uint32_t> _lidReader;
    FileReader<GlobalId> _gidReader;
    FileReader<uint8_t> _bucketUsedBitsReader;
    FileReader<Timestamp> _timestampReader;
    vespalib::FileHeader _header;
    uint32_t _headerLen;
    uint32_t _docIdLimit;
    uint32_t _version;
    uint64_t _datFileSize;

public:
    Reader(std::unique_ptr<FastOS_FileInterface> datFile)
        : _datFile(std::move(datFile)),
          _lidReader(*_datFile),
          _gidReader(*_datFile),
          _bucketUsedBitsReader(*_datFile),
          _timestampReader(*_datFile),
          _header(),
          _headerLen(0u),
          _docIdLimit(0),
          _datFileSize(0u)
    {
        _headerLen = _header.readFile(*_datFile);
        _datFile->SetPosition(_headerLen);
        if (!search::ReaderBase::extractFileSize(_header, *_datFile, _datFileSize)) {
            LOG_ABORT("should not be reached");
        }
        _docIdLimit = _header.getTag(DOCID_LIMIT).asInteger();
        _version = _header.getTag(VERSION).asInteger();
    }

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
        _datFile->ReadBuf(&sizeLow, sizeof(sizeLow));
        _datFile->ReadBuf(&sizeHigh, sizeof(sizeHigh));
        return sizeLow + (static_cast<uint32_t>(sizeHigh) << 8);
    }

    size_t
    getNumElems() const {
        return (_datFileSize - _headerLen) /
               (sizeof(uint32_t) + sizeof(GlobalId) +
                sizeof(uint8_t) + sizeof(Timestamp::Type) +
                ((_version == NO_DOCUMENT_SIZE_TRACKING_VERSION) ? 0 : 3));
    }
};

}

namespace {
class ShrinkBlockHeld : public GenerationHeldBase
{
    DocumentMetaStore &_dms;

public:
    ShrinkBlockHeld(DocumentMetaStore &dms)
        : GenerationHeldBase(0),
          _dms(dms)
    { }

    ~ShrinkBlockHeld() {
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
    incGeneration();
    const BucketState &state =
        _bucketDB->takeGuard()->add(metaData.getGid(),
                      metaData.getBucketId().stripUnused(),
                      metaData.getTimestamp(),
                      metaData.getDocSize(),
                      _subDbType);
    _lidAlloc.updateActiveLids(lid, state.isActive());
    updateCommittedDocIdLimit();
}

void
DocumentMetaStore::onUpdateStat()
{
    vespalib::MemoryUsage usage = _metaDataStore.getMemoryUsage();
    usage.incAllocatedBytesOnHold(getGenerationHolder().getHeldBytes());
    size_t bvSize = _lidAlloc.getUsedLidsSize();
    usage.incAllocatedBytes(bvSize);
    usage.incUsedBytes(bvSize);
    usage.merge(_gidToLidMap.getMemoryUsage());
    // the free lists are not taken into account here
    updateStatistics(_metaDataStore.size(),
                     _metaDataStore.size(),
                     usage.allocatedBytes(),
                     usage.usedBytes(),
                     usage.deadBytes(),
                     usage.allocatedBytesOnHold());
}

void
DocumentMetaStore::onGenerationChange(generation_t generation)
{
    _gidToLidMap.getAllocator().freeze();
    _gidToLidMap.getAllocator().transferHoldLists(generation - 1);
    getGenerationHolder().transferHoldLists(generation - 1);
    updateStat(false);
}

void
DocumentMetaStore::removeOldGenerations(generation_t firstUsed)
{
    _gidToLidMap.getAllocator().trimHoldLists(firstUsed);
    _lidAlloc.trimHoldLists(firstUsed);
    getGenerationHolder().trimHoldLists(firstUsed);
}

std::unique_ptr<search::AttributeSaver>
DocumentMetaStore::onInitSave(vespalib::stringref fileName)
{
    GenerationHandler::Guard guard(getGuard());
    return std::make_unique<DocumentMetaStoreSaver>
        (std::move(guard), createAttributeHeader(fileName),
         _gidToLidMap.getFrozenView().begin(), _metaDataStore);
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
DocumentMetaStore::onLoad()
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
    _gidToLidMap.getAllocator().transferHoldLists(generation);

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
    KeyComp lowerComp(first, _metaDataStore);
    auto find_key = GidToLidMapKey::make_find_key(first);
    return treeView.lowerBound(find_key, lowerComp);
}

template <typename TreeView>
typename TreeView::Iterator
DocumentMetaStore::upperBound(const BucketId &bucketId,
                              const TreeView &treeView) const
{
    document::GlobalId last(document::GlobalId::calculateLastInBucket(bucketId));
    KeyComp upperComp(last, _metaDataStore);
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


DocumentMetaStore::DocumentMetaStore(BucketDBOwnerSP bucketDB,
                                     const vespalib::string &name,
                                     const GrowStrategy &grow,
                                     SubDbType subDbType)
    : DocumentMetaStoreAttribute(name),
      _metaDataStore(grow.getDocsInitialCapacity(),
                     grow.getDocsGrowPercent(),
                     grow.getDocsGrowDelta(),
                     getGenerationHolder()),
      _gidToLidMap(),
      _gid_to_lid_map_write_itr(vespalib::datastore::EntryRef(), _gidToLidMap.getAllocator()),
      _gid_to_lid_map_write_itr_prepare_serial_num(0u),
      _lidAlloc(_metaDataStore.size(),
                _metaDataStore.capacity(),
                getGenerationHolder()),
      _bucketDB(std::move(bucketDB)),
      _shrinkLidSpaceBlockers(0),
      _subDbType(subDbType),
      _trackDocumentSizes(true),
      _op_listener()
{
    ensureSpace(0);         // lid 0 is reserved
    setCommittedDocIdLimit(1u);         // lid 0 is reserved
    _gidToLidMap.getAllocator().freeze(); // create initial frozen tree
    generation_t generation = getGenerationHandler().getCurrentGeneration();
    _gidToLidMap.getAllocator().transferHoldLists(generation);
    updateStat(true);
}

DocumentMetaStore::~DocumentMetaStore()
{
    // TODO: Properly notify about modified buckets when using shared bucket db
    // between document types
    unload();
    getGenerationHolder().clearHoldLists();
    assert(_shrinkLidSpaceBlockers == 0);
}

DocumentMetaStore::Result
DocumentMetaStore::inspectExisting(const GlobalId &gid, uint64_t prepare_serial_num)
{
    assert(_lidAlloc.isFreeListConstructed());
    Result res;
    KeyComp comp(gid, _metaDataStore);
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
    KeyComp comp(gid, _metaDataStore);
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
DocumentMetaStore::put(const GlobalId &gid,
                       const BucketId &bucketId,
                       const Timestamp &timestamp,
                       uint32_t docSize,
                       DocId lid,
                       uint64_t prepare_serial_num)
{
    Result res;
    RawDocumentMetaData metaData(gid, bucketId, timestamp, docSize);
    KeyComp comp(metaData, _metaDataStore);
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
DocumentMetaStore::updateMetaData(DocId lid,
                                  const BucketId &bucketId,
                                  const Timestamp &timestamp)
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
                     timestamp,
                     metaData.getDocSize(),
                     _subDbType);
    metaData.setBucketId(bucketId);
    std::atomic_thread_fence(std::memory_order_release);
    metaData.setTimestamp(timestamp);
    return true;
}

void
DocumentMetaStore::remove(DocId lid, uint64_t prepare_serial_num, bucketdb::Guard &bucketGuard)
{
    const GlobalId & gid = getRawGid(lid);
    KeyComp comp(gid, _metaDataStore);
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
    RawDocumentMetaData &oldMetaData = _metaDataStore[lid];
    bucketGuard->remove(oldMetaData.getGid(),
                        oldMetaData.getBucketId().stripUnused(),
                        oldMetaData.getTimestamp(), oldMetaData.getDocSize(),
                        _subDbType);
}

bool
DocumentMetaStore::remove(DocId lid, uint64_t prepare_serial_num)
{
    if (!validLid(lid)) {
        return false;
    }
    bucketdb::Guard bucketGuard = _bucketDB->takeGuard();
    remove(lid, prepare_serial_num, bucketGuard);
    incGeneration();
    if (_op_listener) {
        _op_listener->notify_remove();
    }
    return true;
}

void
DocumentMetaStore::removeComplete(DocId lid)
{
    assert(lid != 0);
    assert(lid < _metaDataStore.size());
    _lidAlloc.holdLid(lid, _metaDataStore.size(), getCurrentGeneration());
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
    KeyComp comp(gid, _metaDataStore);
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
    incGeneration();
}

void
DocumentMetaStore::removeBatch(const std::vector<DocId> &lidsToRemove, const uint32_t docIdLimit)
{
    bucketdb::Guard bucketGuard = _bucketDB->takeGuard();
    for (const auto &lid : lidsToRemove) {
        assert(lid > 0 && lid < docIdLimit);
        (void) docIdLimit;

        assert(validLid(lid));
        remove(lid, 0u, bucketGuard);
    }
    incGeneration();
    if (_op_listener) {
        _op_listener->notify_remove_batch();
    }
}

void
DocumentMetaStore::removeBatchComplete(const std::vector<DocId> &lidsToRemove)
{
    _lidAlloc.holdLids(lidsToRemove, _metaDataStore.size(), getCurrentGeneration());
    incGeneration();
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
    KeyComp comp(value, _metaDataStore);
    auto find_key = GidToLidMapKey::make_find_key(gid);
    TreeType::ConstIterator itr =
        _gidToLidMap.getFrozenView().find(find_key, comp);
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
        return search::DocumentMetaData();
    }
    const RawDocumentMetaData &raw = getRawMetaData(lid);
    Timestamp timestamp(raw.getTimestamp());
    std::atomic_thread_fence(std::memory_order_acquire);
    return search::DocumentMetaData(lid,
                                    timestamp,
                                    raw.getBucketId(),
                                    raw.getGid(),
                                    _subDbType == SubDbType::REMOVED);
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
            result.push_back(search::DocumentMetaData(lid, timestamp,
                            rawData.getBucketId(),
                            rawData.getGid(),
                            _subDbType == SubDbType::REMOVED));
        }
    }
}

LidUsageStats
DocumentMetaStore::getLidUsageStats() const
{
    uint32_t docIdLimit = getCommittedDocIdLimit();
    uint32_t numDocs = getNumUsedLids();
    uint32_t lowestFreeLid = _lidAlloc.getLowestFreeLid();
    uint32_t highestUsedLid = _lidAlloc.getHighestUsedLid();
    return LidUsageStats(docIdLimit,
                         numDocs,
                         lowestFreeLid,
                         highestUsedLid);
}

Blueprint::UP
DocumentMetaStore::createWhiteListBlueprint() const
{
    return _lidAlloc.createWhiteListBlueprint();
}

AttributeVector::SearchContext::UP
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
    KeyComp comp(gid, _metaDataStore);
    auto find_key = GidToLidMapKey::make_find_key(gid);
    return _gidToLidMap.lowerBound(find_key, comp);
}

DocumentMetaStore::Iterator
DocumentMetaStore::upperBound(const GlobalId &gid) const
{
    // Called by writer thread
    KeyComp comp(gid, _metaDataStore);
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
DocumentMetaStore::populateActiveBuckets(const BucketId::List &buckets)
{
    typedef BucketId::List BIV;
    BIV fixupBuckets;

    _bucketDB->takeGuard()->populateActiveBuckets(buckets, fixupBuckets);

    for (const auto &bucketId : fixupBuckets) {
        updateActiveLids(bucketId, true);
    }
}

void
DocumentMetaStore::clearDocs(DocId lidLow, DocId lidLimit)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    _lidAlloc.clearDocs(lidLow, lidLimit);
}

void
DocumentMetaStore::compactLidSpace(uint32_t wantedLidLimit)
{
    AttributeVector::compactLidSpace(wantedLidLimit);
    ++_shrinkLidSpaceBlockers;
}

void
DocumentMetaStore::holdUnblockShrinkLidSpace()
{
    assert(_shrinkLidSpaceBlockers > 0);
    auto hold = std::make_unique<ShrinkBlockHeld>(*this);
    getGenerationHolder().hold(std::move(hold));
    incGeneration();
}

void
DocumentMetaStore::unblockShrinkLidSpace()
{
    assert(_shrinkLidSpaceBlockers > 0);
    --_shrinkLidSpaceBlockers;
}

bool
DocumentMetaStore::canShrinkLidSpace() const
{
    return AttributeVector::canShrinkLidSpace() &&
        _shrinkLidSpaceBlockers == 0;
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
    if (__builtin_expect(lid < getCommittedDocIdLimit(), true)) {
        if (__builtin_expect(validLidFast(lid), true)) {
            return getRawMetaData(lid).getBucketId();
        }
    }
    return BucketId();
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

}  // namespace proton

namespace vespalib::btree {

template class BTreeIteratorBase<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS, BTreeDefaultTraits::LEAF_SLOTS, BTreeDefaultTraits::PATH_SIZE>;

template class BTreeConstIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

template class BTreeIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

}
