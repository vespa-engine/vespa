// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP(".proton.documentmetastore");

#include "documentmetastore.h"
#include "search_context.h"
#include "documentmetastoresaver.h"
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/readerbase.h>
#include <vespa/searchlib/btree/btree.hpp>
#include <vespa/searchlib/btree/btreenodestore.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreebuilder.hpp>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/searchcore/proton/bucketdb/bucketsessionbase.h>
#include <vespa/searchcore/proton/bucketdb/joinbucketssession.h>
#include <vespa/searchcore/proton/bucketdb/splitbucketsession.h>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/common/rcuvector.hpp>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/fastos/file.h>
#include "document_meta_store_versions.h"


using document::BucketId;
using document::GlobalId;
using proton::bucketdb::BucketState;
using search::AttributeVector;
using search::FileReader;
using search::GrowStrategy;
using search::IAttributeSaveTarget;
using search::LidUsageStats;
using search::MemoryUsage;
using search::attribute::SearchContextParams;
using search::btree::BTreeNoLeafData;
using search::fef::TermFieldMatchData;
using search::queryeval::Blueprint;
using search::queryeval::SearchIterator;
using storage::spi::Timestamp;
using vespalib::GenerationHandler;
using vespalib::GenerationHeldBase;
using vespalib::IllegalStateException;
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
        return _bucketUsedBitsReader.readHostOrder();
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

bool
DocumentMetaStore::insert(DocId lid, const RawDocumentMetaData &metaData)
{
    ensureSpace(lid);
    _metaDataStore[lid] = metaData;
    KeyComp comp(metaData, _metaDataStore, *_gidCompare);
    if (!_gidToLidMap.insert(lid, BTreeNoLeafData(), comp)) {
        return false;
    }
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
    return true;
}

void
DocumentMetaStore::onUpdateStat()
{
    MemoryUsage usage = _metaDataStore.getMemoryUsage();
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
DocumentMetaStore::onInitSave()
{
    GenerationHandler::Guard guard(getGuard());
    return std::make_unique<DocumentMetaStoreSaver>
        (std::move(guard), createAttributeHeader(),
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
    treeBuilder.insert(lid, BTreeNoLeafData());
    assert(!validLid(lid));
    _lidAlloc.registerLid(lid);
    return lid;
}

bool
DocumentMetaStore::onLoad()
{
    documentmetastore::Reader reader(openDAT());
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

bool
DocumentMetaStore::checkBuckets(const GlobalId &gid,
                                const BucketId &bucketId,
                                const TreeType::Iterator &itr,
                                bool found)
{
    bool success = true;
#if 0
    TreeType::Iterator p = itr;
    --p;
    if (p.valid()) {
        DocId prevLid = p.getKey();
        RawDocumentMetaData &prevMetaData = _metaDataStore[prevLid];
        BucketId prevBucketId = prevMetaData.getBucketId();
        if (bucketId != prevBucketId &&
            (bucketId.contains(prevBucketId) ||
             prevBucketId.contains(bucketId))) {
            LOG(error,
                "Bucket overlap, gid %s bucketId %s and prev gid %s bucket %s",
                gid.toString().c_str(),
                bucketId.toString().c_str(),
                prevMetaData.getGid().toString().c_str(),
                prevBucketId.toString().c_str());
            success = false;
        }
    }
    TreeType::Iterator n = itr;
    if (found)
        ++n;
    if (n.valid()) {
        DocId nextLid = n.getKey();
        RawDocumentMetaData &nextMetaData = _metaDataStore[nextLid];
        BucketId nextBucketId = nextMetaData.getBucketId();
        if (bucketId != nextBucketId &&
            (bucketId.contains(nextBucketId) ||
             nextBucketId.contains(bucketId))) {
            LOG(error,
                "Bucket overlap, gid %s bucketId %s and next gid %s bucket %s",
                gid.toString().c_str(),
                bucketId.toString().c_str(),
                nextMetaData.getGid().toString().c_str(),
                nextBucketId.toString().c_str());
            success = false;
        }
    }
#else
    (void) gid;
    (void) bucketId;
    (void) itr;
    (void) found;
#endif
    return success;
}


template <typename TreeView>
typename TreeView::Iterator
DocumentMetaStore::lowerBound(const BucketId &bucketId,
                              const TreeView &treeView) const
{
    document::GlobalId first(document::GlobalId::calculateFirstInBucket(bucketId));
    KeyComp lowerComp(first, _metaDataStore, *_gidCompare);
    return treeView.lowerBound(KeyComp::FIND_DOC_ID, lowerComp);
}

template <typename TreeView>
typename TreeView::Iterator
DocumentMetaStore::upperBound(const BucketId &bucketId,
                              const TreeView &treeView) const
{
    document::GlobalId last(document::GlobalId::calculateLastInBucket(bucketId));
    KeyComp upperComp(last, _metaDataStore, *_gidCompare);
    return treeView.upperBound(KeyComp::FIND_DOC_ID, upperComp);
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


namespace
{

void
unloadBucket(BucketDBOwner &db, const BucketId &id, const BucketState &delta)
{
    if (!id.valid()) {
        assert(delta.empty());
        return;
    }
    assert(!delta.empty());
    BucketDBOwner::Guard guard(db.takeGuard());
    guard->unloadBucket(id, delta);
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
        uint32_t lid = itr.getKey();
        assert(validLid(lid));
        RawDocumentMetaData &metaData = _metaDataStore[lid];
        BucketId bucketId = metaData.getBucketId();
        if (prev != bucketId) {
            unloadBucket(*_bucketDB, prev, prevDelta);
            prevDelta = BucketState();
            prev = bucketId;
        }
        prevDelta.add(metaData.getGid(), metaData.getTimestamp(), metaData.getDocSize(),
                      _subDbType);
    }
    unloadBucket(*_bucketDB, prev, prevDelta);
}


DocumentMetaStore::DocumentMetaStore(BucketDBOwner::SP bucketDB,
                                     const vespalib::string &name,
                                     const GrowStrategy &grow,
                                     const IGidCompare::SP &gidCompare,
                                     SubDbType subDbType)
    : DocumentMetaStoreAttribute(name),
      _metaDataStore(grow.getDocsInitialCapacity(),
                     grow.getDocsGrowPercent(),
                     grow.getDocsGrowDelta(),
                     getGenerationHolder()),
      _gidToLidMap(),
      _lidAlloc(_metaDataStore.size(),
                _metaDataStore.capacity(),
                getGenerationHolder()),
      _gidCompare(gidCompare),
      _bucketDB(bucketDB),
      _shrinkLidSpaceBlockers(0),
      _subDbType(subDbType),
      _trackDocumentSizes(true)
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
DocumentMetaStore::inspectExisting(const GlobalId &gid) const
{
    assert(_lidAlloc.isFreeListConstructed());
    Result res;
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    TreeType::Iterator itr = _gidToLidMap.lowerBound(KeyComp::FIND_DOC_ID,
            comp);
    bool found = itr.valid() && !comp(KeyComp::FIND_DOC_ID, itr.getKey());
    if (found) {
        res.setLid(itr.getKey());
        res.fillPrev(_metaDataStore[res.getLid()].getTimestamp());
        res.markSuccess();
    }
    return res;
}

DocumentMetaStore::Result
DocumentMetaStore::inspect(const GlobalId &gid)
{
    assert(_lidAlloc.isFreeListConstructed());
    Result res;
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    TreeType::Iterator itr = _gidToLidMap.lowerBound(KeyComp::FIND_DOC_ID,
            comp);
    bool found = itr.valid() && !comp(KeyComp::FIND_DOC_ID, itr.getKey());
    if (!found) {
        DocId myLid = peekFreeLid();
        res.setLid(myLid);
        res.markSuccess();
    } else {
        res.setLid(itr.getKey());
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
                       DocId lid)
{
    Result res;
    RawDocumentMetaData metaData(gid, bucketId, timestamp, docSize);
    KeyComp comp(metaData, _metaDataStore, *_gidCompare);
    TreeType::Iterator itr = _gidToLidMap.lowerBound(KeyComp::FIND_DOC_ID,
            comp);
    bool found = itr.valid() && !comp(KeyComp::FIND_DOC_ID, itr.getKey());
    if (!checkBuckets(gid, bucketId, itr, found)) {
        // Failure
    } else if (!found) {
        if (validLid(lid)) {
            throw IllegalStateException(
                    make_string(
                            "document meta data store"
                            " or transaction log is corrupted,"
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
        if (insert(lid, metaData)) {
            res.setLid(lid);
            res.markSuccess();
        }
    } else if (lid != itr.getKey()) {
        throw IllegalStateException(
                make_string(
                        "document meta data store"
                        " or transaction log is corrupted,"
                        " cannot put"
                        " document with lid '%u' and gid '%s',"
                        " gid found, but using another lid '%u'",
                        lid,
                        gid.toString().c_str(),
                        itr.getKey()));
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

bool
DocumentMetaStore::remove(DocId lid, BucketDBOwner::Guard &bucketGuard)
{
    if (!validLid(lid)) {
        return false;
    }
    const GlobalId & gid = getRawGid(lid);
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    if (!_gidToLidMap.remove(lid, comp)) {
        throw IllegalStateException(make_string(
                        "document meta data store corrupted,"
                        " cannot remove"
                        " document with lid '%u' and gid '%s'",
                        lid, gid.toString().c_str()));
    }
    _lidAlloc.unregisterLid(lid);
    RawDocumentMetaData &oldMetaData = _metaDataStore[lid];
    bucketGuard->remove(oldMetaData.getGid(),
                        oldMetaData.getBucketId().stripUnused(),
                        oldMetaData.getTimestamp(), oldMetaData.getDocSize(),
                        _subDbType);
    return true;
}

bool
DocumentMetaStore::remove(DocId lid)
{
    BucketDBOwner::Guard bucketGuard = _bucketDB->takeGuard();
    bool result = remove(lid, bucketGuard);
    incGeneration();
    return result;
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
DocumentMetaStore::move(DocId fromLid, DocId toLid)
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
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    TreeType::Iterator it(_gidToLidMap.lowerBound(fromLid, comp));
    assert(it.valid());
    assert(it.getKey() == fromLid);
    _gidToLidMap.thaw(it);
    it.writeKey(toLid);
    _lidAlloc.moveLidEnd(fromLid, toLid);
    incGeneration();
}

void
DocumentMetaStore::removeBatch(const std::vector<DocId> &lidsToRemove, const uint32_t docIdLimit)
{
    BucketDBOwner::Guard bucketGuard = _bucketDB->takeGuard();
    for (const auto &lid : lidsToRemove) {
        assert(lid > 0 && lid < docIdLimit);
        (void) docIdLimit;

        bool removed = remove(lid, bucketGuard);
        assert(removed);
        (void) removed;
    }
    incGeneration();
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
    KeyComp comp(value, _metaDataStore, *_gidCompare);
    TreeType::ConstIterator itr =
        _gidToLidMap.getFrozenView().find(KeyComp::FIND_DOC_ID, comp);
    if (!itr.valid()) {
        return false;
    }
    lid = itr.getKey();
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
        DocId lid = itr.getKey();
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
    return _lidAlloc.createWhiteListBlueprint(getCommittedDocIdLimit());
}

AttributeVector::SearchContext::UP
DocumentMetaStore::getSearch(std::unique_ptr<search::QueryTermSimple> qTerm, const SearchContextParams &) const
{
    return AttributeVector::SearchContext::UP
            (new documentmetastore::SearchContext(std::move(qTerm), *this));
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
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    return _gidToLidMap.lowerBound(KeyComp::FIND_DOC_ID, comp);
}

DocumentMetaStore::Iterator
DocumentMetaStore::upperBound(const GlobalId &gid) const
{
    // Called by writer thread
    KeyComp comp(gid, _metaDataStore, *_gidCompare);
    return _gidToLidMap.upperBound(KeyComp::FIND_DOC_ID, comp);
}

void
DocumentMetaStore::getLids(const BucketId &bucketId, std::vector<DocId> &lids)
{
    // Called by writer thread
    TreeType::Iterator itr = lowerBound(bucketId);
    TreeType::Iterator end = upperBound(bucketId);
    for (; itr != end; ++itr) {
        DocId lid = itr.getKey();
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
        DocId lid = itr.getKey();
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
        DocId lid = itr.getKey();
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
        DocId lid = itr.getKey();
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
    GenerationHeldBase::UP hold(new ShrinkBlockHeld(*this));
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
    beginFrozen().foreach_key([this,&visitor](uint32_t lid)
                              { visitor.visit(getRawMetaData(lid).getGid(), lid); });
}

}  // namespace proton

template class search::btree::
BTreeIterator<proton::DocumentMetaStore::DocId,
              search::btree::BTreeNoLeafData,
              search::btree::NoAggregated,
              const proton::DocumentMetaStore::KeyComp &>;
