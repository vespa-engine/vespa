// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceengine.h"
#include "ipersistenceengineowner.h"
#include "transport_latch.h"
#include <vespa/metrics/loadmetric.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>


#include <vespa/log/log.h>
LOG_SETUP(".proton.persistenceengine.persistenceengine");

using document::Document;
using document::DocumentId;
using storage::spi::BucketChecksum;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfo;
using storage::spi::BucketInfoResult;
using storage::spi::IncludedVersions;
using storage::spi::PartitionState;
using storage::spi::PartitionStateList;
using storage::spi::Result;
using vespalib::IllegalStateException;
using vespalib::Sequence;
using vespalib::make_string;

using namespace std::chrono_literals;

namespace proton {

namespace {

class ResultHandlerBase {
protected:
    std::mutex               _lock;
    vespalib::CountDownLatch _latch;
public:
    ResultHandlerBase(uint32_t waitCnt);
    ~ResultHandlerBase();
    void await() { _latch.await(); }
};

ResultHandlerBase::ResultHandlerBase(uint32_t waitCnt)
    : _lock(),
      _latch(waitCnt)
{}
ResultHandlerBase::~ResultHandlerBase() = default;

class GenericResultHandler : public ResultHandlerBase, public IGenericResultHandler {
private:
    Result _result;
public:
    GenericResultHandler(uint32_t waitCnt) :
        ResultHandlerBase(waitCnt),
        _result()
    { }
    ~GenericResultHandler();
    void handle(const Result &result) override {
        if (result.hasError()) {
            std::lock_guard<std::mutex> guard(_lock);
            if (_result.hasError()) {
                _result = TransportLatch::mergeErrorResults(_result, result);
            } else {
                _result = result;
            }
        }
        _latch.countDown();
    }
    const Result &getResult() const { return _result; }
};

GenericResultHandler::~GenericResultHandler() = default;

class BucketIdListResultHandler : public IBucketIdListResultHandler
{
private:
    typedef vespalib::hash_set<document::BucketId, document::BucketId::hash> BucketIdSet;
    BucketIdSet _bucketSet;
public:
    BucketIdListResultHandler()
        : _bucketSet()
    { }
    ~BucketIdListResultHandler() override;
    void handle(const BucketIdListResult &result) override {
        const BucketIdListResult::List &buckets = result.getList();
        for (size_t i = 0; i < buckets.size(); ++i) {
            _bucketSet.insert(buckets[i]);
        }
    }
    BucketIdListResult getResult() const {
        BucketIdListResult::List buckets;
        buckets.reserve(_bucketSet.size());
        for (document::BucketId bucketId : _bucketSet) {
            buckets.push_back(bucketId);
        }
        return BucketIdListResult(buckets);
    }
};


BucketIdListResultHandler::~BucketIdListResultHandler() = default;

class SynchronizedBucketIdListResultHandler : public ResultHandlerBase,
                                              public BucketIdListResultHandler
{
public:
    SynchronizedBucketIdListResultHandler(uint32_t waitCnt)
        : ResultHandlerBase(waitCnt),
          BucketIdListResultHandler()
    { }
    ~SynchronizedBucketIdListResultHandler() override;
    void handle(const BucketIdListResult &result) override {
        {
            std::lock_guard<std::mutex> guard(_lock);
            BucketIdListResultHandler::handle(result);
        }
        _latch.countDown();
    }
};

SynchronizedBucketIdListResultHandler::~SynchronizedBucketIdListResultHandler() = default;

class BucketInfoResultHandler : public IBucketInfoResultHandler {
private:
    BucketInfoResult _result;
    bool             _first;
public:
    BucketInfoResultHandler() :
        _result(BucketInfo()),
        _first(true)
    {
    }
    ~BucketInfoResultHandler() override;
    void handle(const BucketInfoResult &result) override {
        if (_first) {
            _result = result;
            _first = false;
        } else {
            BucketInfo b1 = _result.getBucketInfo();
            BucketInfo b2 = result.getBucketInfo();
            BucketInfo::ReadyState ready =
                    (b1.getReady() == b2.getReady() ? b1.getReady() : BucketInfo::NOT_READY);
            BucketInfo::ActiveState active =
                    (b1.getActive() == b2.getActive() ? b1.getActive() : BucketInfo::NOT_ACTIVE);
            _result = BucketInfoResult(
                    BucketInfo(BucketChecksum(b1.getChecksum() + b2.getChecksum()),
                            b1.getDocumentCount() + b2.getDocumentCount(),
                            b1.getDocumentSize() + b2.getDocumentSize(),
                            b1.getEntryCount() + b2.getEntryCount(),
                            b1.getUsedSize() + b2.getUsedSize(),
                            ready, active));
        }
    }
    const BucketInfoResult &getResult() const { return _result; }
};

BucketInfoResultHandler::~BucketInfoResultHandler() = default;

}

PersistenceEngine::HandlerSnapshot::UP
PersistenceEngine::getHandlerSnapshot() const
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.getHandlerSnapshot();
}

PersistenceEngine::HandlerSnapshot::UP
PersistenceEngine::getHandlerSnapshot(document::BucketSpace bucketSpace) const
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.getHandlerSnapshot(bucketSpace);
}

PersistenceEngine::PersistenceEngine(IPersistenceEngineOwner &owner, const IResourceWriteFilter &writeFilter,
                                     ssize_t defaultSerializedSize, bool ignoreMaxBytes)
    : AbstractPersistenceProvider(),
      _defaultSerializedSize(defaultSerializedSize),
      _ignoreMaxBytes(ignoreMaxBytes),
      _handlers(),
      _lock(),
      _iterators(),
      _iterators_lock(),
      _owner(owner),
      _writeFilter(writeFilter),
      _clusterStates(),
      _extraModifiedBuckets(),
      _rwMutex()
{
}


PersistenceEngine::~PersistenceEngine()
{
    destroyIterators();
}


IPersistenceHandler::SP
PersistenceEngine::putHandler(document::BucketSpace bucketSpace, const DocTypeName &docType,
                              const IPersistenceHandler::SP &handler)
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.putHandler(bucketSpace, docType, handler);
}


IPersistenceHandler::SP
PersistenceEngine::getHandler(document::BucketSpace bucketSpace, const DocTypeName &docType) const
{
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.getHandler(bucketSpace, docType);
}


IPersistenceHandler::SP
PersistenceEngine::removeHandler(document::BucketSpace bucketSpace, const DocTypeName &docType)
{
    // TODO: Grab bucket list and treat them as modified
    std::lock_guard<std::mutex> guard(_lock);
    return _handlers.removeHandler(bucketSpace, docType);
}


Result
PersistenceEngine::initialize()
{
    std::unique_lock<std::shared_timed_mutex> wguard(getWLock());
    LOG(debug, "Begin initializing persistence handlers");
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->initialize();
    }
    LOG(debug, "Done initializing persistence handlers");
    return Result();
}


PersistenceEngine::PartitionStateListResult
PersistenceEngine::getPartitionStates() const
{
    PartitionStateList list(1);
    return PartitionStateListResult(list);
}


BucketIdListResult
PersistenceEngine::listBuckets(BucketSpace bucketSpace, PartitionId id) const
{
    // Runs in SPI thread.
    // No handover to write threads in persistence handlers.
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    if (id != 0) {
        BucketIdListResult::List emptyList;
        return BucketIdListResult(emptyList);
    }
    HandlerSnapshot::UP snap = getHandlerSnapshot(bucketSpace);
    BucketIdListResultHandler resultHandler;
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleListBuckets(resultHandler);
    }
    return resultHandler.getResult();
}


Result
PersistenceEngine::setClusterState(BucketSpace bucketSpace, const ClusterState &calc)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    saveClusterState(bucketSpace, calc);
    HandlerSnapshot::UP snap = getHandlerSnapshot(bucketSpace);
    GenericResultHandler resultHandler(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleSetClusterState(calc, resultHandler);
    }
    resultHandler.await();
    _owner.setClusterState(bucketSpace, calc);
    return resultHandler.getResult();
}


Result
PersistenceEngine::setActiveState(const Bucket& bucket,
                                  storage::spi::BucketInfo::ActiveState newState)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    HandlerSnapshot::UP snap = getHandlerSnapshot(bucket.getBucketSpace());
    GenericResultHandler resultHandler(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleSetActiveState(bucket, newState, resultHandler);
    }
    resultHandler.await();
    return resultHandler.getResult();
}


BucketInfoResult
PersistenceEngine::getBucketInfo(const Bucket& b) const
{
    // Runs in SPI thread.
    // No handover to write threads in persistence handlers.
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    HandlerSnapshot::UP snap = getHandlerSnapshot(b.getBucketSpace());
    BucketInfoResultHandler resultHandler;
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleGetBucketInfo(b, resultHandler);
    }
    return resultHandler.getResult();
}


Result
PersistenceEngine::put(const Bucket& b, Timestamp t, const document::Document::SP& doc, Context&)
{
    if (!_writeFilter.acceptWriteOperation()) {
        IResourceWriteFilter::State state = _writeFilter.getAcceptState();
        if (!state.acceptWriteOperation()) {
            return Result(Result::RESOURCE_EXHAUSTED,
                          make_string("Put operation rejected for document '%s': '%s'",
                                      doc->getId().toString().c_str(), state.message().c_str()));
        }
    }
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    DocTypeName docType(doc->getType());
    LOG(spam, "put(%s, %" PRIu64 ", (\"%s\", \"%s\"))", b.toString().c_str(), static_cast<uint64_t>(t.getValue()),
        docType.toString().c_str(), doc->getId().toString().c_str());
    if (!doc->getId().hasDocType()) {
        return Result(Result::PERMANENT_ERROR,
                      make_string("Old id scheme not supported in elastic mode (%s)", doc->getId().toString().c_str()));
    }
    IPersistenceHandler::SP handler = getHandler(b.getBucketSpace(), docType);
    if (!handler) {
        return Result(Result::PERMANENT_ERROR,
                      make_string("No handler for document type '%s'", docType.toString().c_str()));
    }
    TransportLatch latch(1);
    handler->handlePut(feedtoken::make(latch), b, t, doc);
    latch.await();
    return latch.getResult();
}

PersistenceEngine::RemoveResult
PersistenceEngine::remove(const Bucket& b, Timestamp t, const DocumentId& did, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    assert(did.hasDocType());
    DocTypeName docType(did.getDocType());
    LOG(spam, "remove(%s, %" PRIu64 ", \"%s\")", b.toString().c_str(),
        static_cast<uint64_t>(t.getValue()), did.toString().c_str());
    IPersistenceHandler::SP handler = getHandler(b.getBucketSpace(), docType);
    if (!handler) {
        return RemoveResult(Result::PERMANENT_ERROR,
                            make_string("No handler for document type '%s'", docType.toString().c_str()));
    }
    TransportLatch latch(1);
    handler->handleRemove(feedtoken::make(latch), b, t, did);
    latch.await();
    return latch.getRemoveResult();
}


PersistenceEngine::UpdateResult
PersistenceEngine::update(const Bucket& b, Timestamp t, const DocumentUpdate::SP& upd, Context&)
{
    if (!_writeFilter.acceptWriteOperation()) {
        IResourceWriteFilter::State state = _writeFilter.getAcceptState();
        if (!state.acceptWriteOperation()) {
            return UpdateResult(Result::RESOURCE_EXHAUSTED,
                                make_string("Update operation rejected for document '%s': '%s'",
                                            upd->getId().toString().c_str(), state.message().c_str()));
        }
    }
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    DocTypeName docType(upd->getType());
    LOG(spam, "update(%s, %" PRIu64 ", (\"%s\", \"%s\"), createIfNonExistent='%s')",
        b.toString().c_str(), static_cast<uint64_t>(t.getValue()), docType.toString().c_str(),
        upd->getId().toString().c_str(), (upd->getCreateIfNonExistent() ? "true" : "false"));
    IPersistenceHandler::SP handler = getHandler(b.getBucketSpace(), docType);

    if (handler) {
        TransportLatch latch(1);
        LOG(debug, "update = %s", upd->toXml().c_str());
        handler->handleUpdate(feedtoken::make(latch), b, t, upd);
        latch.await();
        return latch.getUpdateResult();
    } else {
        return UpdateResult(Result::PERMANENT_ERROR, make_string("No handler for document type '%s'", docType.toString().c_str()));
    }
}


PersistenceEngine::GetResult
PersistenceEngine::get(const Bucket& b, const document::FieldSet& fields, const DocumentId& did, Context& context) const
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    HandlerSnapshot::UP snapshot = getHandlerSnapshot(b.getBucketSpace());

    for (PersistenceHandlerSequence & handlers = snapshot->handlers(); handlers.valid(); handlers.next()) {
        BucketGuard::UP bucket_guard = handlers.get()->lockBucket(b);
        IPersistenceHandler::RetrieversSP retrievers = handlers.get()->getDocumentRetrievers(context.getReadConsistency());
        for (size_t i = 0; i < retrievers->size(); ++i) {
            IDocumentRetriever &retriever = *(*retrievers)[i];
            search::DocumentMetaData meta = retriever.getDocumentMetaData(did);
            if (meta.timestamp != 0 && meta.bucketId == b.getBucketId()) {
                if (meta.removed) {
                    return GetResult();
                }
                document::Document::UP doc = retriever.getDocument(meta.lid);
                if (!doc || doc->getId().getGlobalId() != meta.gid) {
                    return GetResult();
                }
                document::FieldSet::stripFields(*doc, fields);
                return GetResult(std::move(doc), meta.timestamp);
            }
        }
    }
    return GetResult();
}


PersistenceEngine::CreateIteratorResult
PersistenceEngine::createIterator(const Bucket &bucket, const document::FieldSet& fields, const Selection &selection,
                                  IncludedVersions versions, Context & context)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    HandlerSnapshot::UP snapshot = getHandlerSnapshot(bucket.getBucketSpace());

    auto entry = std::make_unique<IteratorEntry>(context.getReadConsistency(), bucket, fields, selection,
                                                 versions, _defaultSerializedSize, _ignoreMaxBytes);
    entry->bucket_guards.reserve(snapshot->size());
    for (PersistenceHandlerSequence & handlers = snapshot->handlers(); handlers.valid(); handlers.next()) {
        entry->bucket_guards.push_back(handlers.get()->lockBucket(bucket));
        IPersistenceHandler::RetrieversSP retrievers = handlers.get()->getDocumentRetrievers(context.getReadConsistency());
        for (size_t i = 0; i < retrievers->size(); ++i) {
            entry->it.add((*retrievers)[i]);
        }
    }
    entry->handler_sequence = HandlerSnapshot::release(std::move(*snapshot));

    std::lock_guard<std::mutex> guard(_iterators_lock);
    static IteratorId id_counter(0);
    IteratorId id(++id_counter);
    _iterators[id] = entry.release();
    return CreateIteratorResult(id);
}


PersistenceEngine::IterateResult
PersistenceEngine::iterate(IteratorId id, uint64_t maxByteSize, Context&) const
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    IteratorEntry *iteratorEntry;
    {
        std::lock_guard<std::mutex> guard(_iterators_lock);
        auto it = _iterators.find(id);
        if (it == _iterators.end()) {
            return IterateResult(Result::PERMANENT_ERROR, make_string("Unknown iterator with id %" PRIu64, id.getValue()));
        }
        iteratorEntry = it->second;
        if (iteratorEntry->in_use) {
            return IterateResult(Result::TRANSIENT_ERROR, make_string("Iterator with id %" PRIu64 " is already in use", id.getValue()));
        }
        iteratorEntry->in_use = true;
    }

    DocumentIterator &iterator = iteratorEntry->it;
    try {
        IterateResult result = iterator.iterate(maxByteSize);
        std::lock_guard<std::mutex> guard(_iterators_lock);
        iteratorEntry->in_use = false;
        return result;
    } catch (const std::exception & e) {
        IterateResult result(Result::PERMANENT_ERROR, make_string("Caught exception during visitor iterator.iterate() = '%s'", e.what()));
        LOG(warning, "Caught exception during visitor iterator.iterate() = '%s'", e.what());
        std::lock_guard<std::mutex> guard(_iterators_lock);
        iteratorEntry->in_use = false;
        return result;
    }
}


Result
PersistenceEngine::destroyIterator(IteratorId id, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    std::lock_guard<std::mutex> guard(_iterators_lock);
    auto it = _iterators.find(id);
    if (it == _iterators.end()) {
        return Result();
    }
    if (it->second->in_use) {
        return Result(Result::TRANSIENT_ERROR, make_string("Iterator with id %" PRIu64 " is currently in use", id.getValue()));
    }
    delete it->second;
    _iterators.erase(it);
    return Result();
}


Result
PersistenceEngine::createBucket(const Bucket &b, Context &)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    LOG(spam, "createBucket(%s)", b.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot(b.getBucketSpace());
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleCreateBucket(feedtoken::make(latch), b);
    }
    latch.await();
    return latch.getResult();
}


Result
PersistenceEngine::deleteBucket(const Bucket& b, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    LOG(spam, "deleteBucket(%s)", b.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot(b.getBucketSpace());
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleDeleteBucket(feedtoken::make(latch), b);
    }
    latch.await();
    return latch.getResult();
}


BucketIdListResult
PersistenceEngine::getModifiedBuckets(BucketSpace bucketSpace) const
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    typedef BucketIdListResultV MBV;
    MBV extraModifiedBuckets;
    {
        std::lock_guard<std::mutex> guard(_lock);
        extraModifiedBuckets.swap(_extraModifiedBuckets[bucketSpace]);
    }
    HandlerSnapshot::UP snap = getHandlerSnapshot(bucketSpace);
    SynchronizedBucketIdListResultHandler resultHandler(snap->size() + extraModifiedBuckets.size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleGetModifiedBuckets(resultHandler);
    }
    for (const auto & item : extraModifiedBuckets) {
        resultHandler.handle(*item);
    }
    resultHandler.await();
    return resultHandler.getResult();
}


Result
PersistenceEngine::split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    LOG(spam, "split(%s, %s, %s)", source.toString().c_str(), target1.toString().c_str(), target2.toString().c_str());
    assert(source.getBucketSpace() == target1.getBucketSpace());
    assert(source.getBucketSpace() == target2.getBucketSpace());
    HandlerSnapshot::UP snap = getHandlerSnapshot(source.getBucketSpace());
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleSplit(feedtoken::make(latch), source, target1, target2);
    }
    latch.await();
    return latch.getResult();
}


Result
PersistenceEngine::join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(_rwMutex);
    LOG(spam, "join(%s, %s, %s)", source1.toString().c_str(), source2.toString().c_str(), target.toString().c_str());
    assert(source1.getBucketSpace() == target.getBucketSpace());
    assert(source2.getBucketSpace() == target.getBucketSpace());
    HandlerSnapshot::UP snap = getHandlerSnapshot(target.getBucketSpace());
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleJoin(feedtoken::make(latch), source1, source2, target);
    }
    latch.await();
    return latch.getResult();
}


Result
PersistenceEngine::maintain(const Bucket& , MaintenanceLevel)
{
    return Result();
}

void
PersistenceEngine::destroyIterators()
{
    Context context(storage::spi::LoadType(0, "default"),
                    storage::spi::Priority(0x80),
                    storage::spi::Trace::TraceLevel(0));
    for (;;) {
        IteratorId id;
        {
            std::lock_guard<std::mutex> guard(_iterators_lock);
            if (_iterators.empty())
                break;
            id = _iterators.begin()->first;
        }
        Result res(destroyIterator(id, context));
        if (res.hasError()) {
            LOG(debug, "%ld iterator left. Can not destroy iterator '%ld'. Reason='%s'", _iterators.size(), id.getValue(), res.toString().c_str());
            std::this_thread::sleep_for(100ms);
        }
    }
}


void
PersistenceEngine::saveClusterState(BucketSpace bucketSpace, const ClusterState &calc)
{
    auto clusterState = std::make_shared<ClusterState>(calc);
    {
        std::lock_guard<std::mutex> guard(_lock);
        clusterState.swap(_clusterStates[bucketSpace]);
    }
}

PersistenceEngine::ClusterState::SP
PersistenceEngine::savedClusterState(BucketSpace bucketSpace) const
{
    std::lock_guard<std::mutex> guard(_lock);
    auto itr(_clusterStates.find(bucketSpace));
    return ((itr != _clusterStates.end()) ? itr->second : ClusterState::SP());
}

void
PersistenceEngine::propagateSavedClusterState(BucketSpace bucketSpace, IPersistenceHandler &handler)
{
    ClusterState::SP clusterState(savedClusterState(bucketSpace));
    if (!clusterState)
        return;
    // Propagate saved cluster state.
    // TODO: Fix race with new cluster state setting.
    GenericResultHandler resultHandler(1);
    handler.handleSetClusterState(*clusterState, resultHandler);
    resultHandler.await();
}

void
PersistenceEngine::grabExtraModifiedBuckets(BucketSpace bucketSpace, IPersistenceHandler &handler)
{
    BucketIdListResultHandler resultHandler;
    handler.handleListBuckets(resultHandler);
    auto result = std::make_shared<BucketIdListResult>(resultHandler.getResult());
    std::lock_guard<std::mutex> guard(_lock);
    _extraModifiedBuckets[bucketSpace].push_back(result);
}


class ActiveBucketIdListResultHandler : public IBucketIdListResultHandler
{
private:
    using BucketIdMap = std::map<document::BucketId, size_t>;
    using IR = std::pair<BucketIdMap::iterator, bool>;
    BucketIdMap _bucketMap;
public:
    ActiveBucketIdListResultHandler() : _bucketMap() { }

    void handle(const BucketIdListResult &result) override {
        const BucketIdListResult::List &buckets = result.getList();
        for (size_t i = 0; i < buckets.size(); ++i) {
            IR ir(_bucketMap.insert(std::make_pair(buckets[i], 1u)));
            if (!ir.second) {
                ++(ir.first->second);
            }
        }
    }

    const BucketIdMap & getBucketMap() const { return _bucketMap; }
};

void
PersistenceEngine::populateInitialBucketDB(BucketSpace bucketSpace,
                                           IPersistenceHandler &targetHandler)
{
    HandlerSnapshot::UP snap = getHandlerSnapshot(bucketSpace);
    
    size_t snapSize(snap->size());
    size_t flawed = 0;

    // handleListActiveBuckets() runs in SPI thread.
    // No handover to write threads in persistence handlers.
    ActiveBucketIdListResultHandler resultHandler;
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleListActiveBuckets(resultHandler);
    }
    typedef std::map<document::BucketId, size_t> BucketIdMap;
    document::BucketId::List buckets;
    const BucketIdMap &bucketMap(resultHandler.getBucketMap());

    for (const auto & item : bucketMap) {
        if (item.second != snapSize) {
            ++flawed;
        }
        buckets.push_back(item.first);
    }
    LOG(info, "Adding %zu active buckets (%zu flawed) to new bucket db", buckets.size(), flawed);
    GenericResultHandler trHandler(1);
    targetHandler.handlePopulateActiveBuckets(buckets, trHandler);
    trHandler.await();
}

std::unique_lock<std::shared_timed_mutex>
PersistenceEngine::getWLock() const
{
    return std::unique_lock<std::shared_timed_mutex>(_rwMutex);
}

} // storage
