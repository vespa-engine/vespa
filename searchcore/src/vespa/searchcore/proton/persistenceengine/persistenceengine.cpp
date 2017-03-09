// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistenceengine.h"
#include "ipersistenceengineowner.h"
#include "transport_latch.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/feedreply.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/fastos/thread.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.persistenceengine.persistenceengine");

using document::Document;
using document::DocumentId;
using documentapi::DocumentReply;
using documentapi::RemoveDocumentReply;
using mbus::Reply;
using storage::spi::BucketChecksum;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfo;
using storage::spi::BucketInfoResult;
using storage::spi::IncludedVersions;
using storage::spi::PartitionState;
using storage::spi::PartitionStateList;
using storage::spi::Result;
using vespalib::IllegalStateException;
using vespalib::LockGuard;
using vespalib::Sequence;
using vespalib::make_string;

namespace proton {

namespace {

class ResultHandlerBase {
protected:
    vespalib::Lock           _lock;
    vespalib::CountDownLatch _latch;
public:
    ResultHandlerBase(uint32_t waitCnt) : _lock(), _latch(waitCnt) {}
    void await() { _latch.await(); }
};


class GenericResultHandler : public ResultHandlerBase, public IGenericResultHandler {
private:
    Result _result;
public:
    GenericResultHandler(uint32_t waitCnt) :
        ResultHandlerBase(waitCnt),
        _result()
    { }
    virtual void handle(const Result &result) {
        if (result.hasError()) {
            vespalib::LockGuard guard(_lock);
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


class BucketIdListResultHandler : public IBucketIdListResultHandler
{
private:
    typedef vespalib::hash_set<document::BucketId, document::BucketId::hash> BucketIdSet;
    BucketIdSet _bucketSet;
public:
    BucketIdListResultHandler()
        : _bucketSet()
    { }
    virtual void handle(const BucketIdListResult &result) {
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


class SynchronizedBucketIdListResultHandler : public ResultHandlerBase,
                                              public BucketIdListResultHandler
{
public:
    SynchronizedBucketIdListResultHandler(uint32_t waitCnt)
        : ResultHandlerBase(waitCnt),
          BucketIdListResultHandler()
    { }
    virtual void handle(const BucketIdListResult &result) {
        {
            vespalib::LockGuard guard(_lock);
            BucketIdListResultHandler::handle(result);
        }
        _latch.countDown();
    }
};


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
    virtual void handle(const BucketInfoResult &result) {
        if (_first) {
            _result = result;
            _first = false;
        } else {
            BucketInfo b1 = _result.getBucketInfo();
            BucketInfo b2 = result.getBucketInfo();
            BucketInfo::ReadyState ready =
                    (b1.getReady() == b2.getReady() ? b1.getReady() :
                            BucketInfo::NOT_READY);
            BucketInfo::ActiveState active =
                    (b1.getActive() == b2.getActive() ? b1.getActive() :
                            BucketInfo::NOT_ACTIVE);
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

}

#define NOT_YET throw vespalib::IllegalArgumentException("Not implemented yet")

PersistenceEngine::HandlerSnapshot::UP
PersistenceEngine::getHandlerSnapshot() const
{
    LockGuard guard(_lock);
    return std::make_unique<HandlerSnapshot>(_handlers.snapshot(), _handlers.size());
}

namespace {
template <typename T>
class SequenceOfOne : public Sequence<T> {
    bool _done;
    T _value;
public:
    SequenceOfOne(const T &value) : _done(false), _value(value) {}

    virtual bool valid() const { return !_done; }
    virtual T get() const { return _value; }
    virtual void next() { _done = true; }
};

template <typename T>
typename Sequence<T>::UP make_sequence(const T &value) {
    return typename Sequence<T>::UP(new SequenceOfOne<T>(value));
}
}  // namespace

PersistenceEngine::HandlerSnapshot::UP
PersistenceEngine::getHandlerSnapshot(const DocumentId &id) const {
    if (!id.hasDocType()) {
        return getHandlerSnapshot();
    }
    IPersistenceHandler::SP handler = getHandler(DocTypeName(id.getDocType()));
    if (!handler.get()) {
        return HandlerSnapshot::UP();
    }
    return HandlerSnapshot::UP(
            new HandlerSnapshot(make_sequence(handler.get()), 1));
}

PersistenceEngine::PersistenceEngine(IPersistenceEngineOwner &owner,
                                     const IResourceWriteFilter &writeFilter,
                                     ssize_t defaultSerializedSize,
                                     bool ignoreMaxBytes)
    : AbstractPersistenceProvider(),
      _defaultSerializedSize(defaultSerializedSize),
      _ignoreMaxBytes(ignoreMaxBytes),
      _handlers(),
      _lock(),
      _iterators(),
      _iterators_lock(),
      _owner(owner),
      _writeFilter(writeFilter),
      _clusterState(),
      _extraModifiedBuckets(),
      _rwMutex()
{
}


PersistenceEngine::~PersistenceEngine()
{
    destroyIterators();
}


IPersistenceHandler::SP
PersistenceEngine::putHandler(const DocTypeName &docType,
                              const IPersistenceHandler::SP &handler)
{
    LockGuard guard(_lock);
    return _handlers.putHandler(docType, handler);
}


IPersistenceHandler::SP
PersistenceEngine::getHandler(const DocTypeName &docType) const
{
    LockGuard guard(_lock);
    return _handlers.getHandler(docType);
}


IPersistenceHandler::SP
PersistenceEngine::removeHandler(const DocTypeName &docType)
{
    // TODO: Grab bucket list and treat them as modified
    LockGuard guard(_lock);
    return _handlers.removeHandler(docType);
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


PartitionStateListResult
PersistenceEngine::getPartitionStates() const
{
    PartitionStateList list(1);
    return PartitionStateListResult(list);
}


BucketIdListResult
PersistenceEngine::listBuckets(PartitionId id) const
{
    // Runs in SPI thread.
    // No handover to write threads in persistence handlers.
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    if (id != 0) {
        BucketIdListResult::List emptyList;
        return BucketIdListResult(emptyList);
    }
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    BucketIdListResultHandler resultHandler;
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleListBuckets(resultHandler);
    }
    return resultHandler.getResult();
}


Result
PersistenceEngine::setClusterState(const ClusterState &calc)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    saveClusterState(calc);
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    GenericResultHandler resultHandler(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        handler->handleSetClusterState(calc, resultHandler);
    }
    resultHandler.await();
    _owner.setClusterState(calc);
    return resultHandler.getResult();
}


Result
PersistenceEngine::setActiveState(const Bucket& bucket,
                                  storage::spi::BucketInfo::ActiveState newState)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
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
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
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
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    DocTypeName docType(doc->getType());
    LOG(spam,
        "put(%s, %" PRIu64 ", (\"%s\", \"%s\"))",
        b.toString().c_str(),
        static_cast<uint64_t>(t.getValue()),
        docType.toString().c_str(),
        doc->getId().toString().c_str());
    if (!doc->getId().hasDocType()) {
        return Result(Result::PERMANENT_ERROR, make_string(
                        "Old id scheme not supported in elastic mode (%s)",
                        doc->getId().toString().c_str()));
    }
    IPersistenceHandler::SP handler = getHandler(docType);
    if (handler.get() == NULL) {
        return Result(Result::PERMANENT_ERROR,
                      make_string("No handler for document type '%s'",
                                  docType.toString().c_str()));
    }
    TransportLatch latch(1);
    FeedToken token(latch, mbus::Reply::UP(new documentapi::FeedReply(
                           documentapi::DocumentProtocol::REPLY_PUTDOCUMENT)));
    handler->handlePut(token, b, t, doc);
    latch.await();
    return latch.getResult();
}

RemoveResult
PersistenceEngine::remove(const Bucket& b, Timestamp t, const DocumentId& did, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LOG(spam,
        "remove(%s, %" PRIu64 ", \"%s\")",
        b.toString().c_str(),
        static_cast<uint64_t>(t.getValue()),
        did.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot(did);
    if (!snap.get()) {
        return RemoveResult(false);
    }
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        FeedToken token(latch, Reply::UP(new RemoveDocumentReply));
        handler->handleRemove(token, b, t, did);
    }
    latch.await();
    return latch.getRemoveResult();
}


UpdateResult
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
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    DocTypeName docType(upd->getType());
    LOG(spam,
        "update(%s, %" PRIu64 ", (\"%s\", \"%s\"), createIfNonExistent='%s')",
        b.toString().c_str(),
        static_cast<uint64_t>(t.getValue()),
        docType.toString().c_str(),
        upd->getId().toString().c_str(),
        (upd->getCreateIfNonExistent() ? "true" : "false"));
    IPersistenceHandler::SP handler = getHandler(docType);
    TransportLatch latch(1);
    if (handler.get() != NULL) {
        FeedToken token(latch, mbus::Reply::UP(new documentapi::UpdateDocumentReply()));
        LOG(debug, "update = %s", upd->toXml().c_str());
        handler->handleUpdate(token, b, t, upd);
        latch.await();
    } else {
        return UpdateResult(Result::PERMANENT_ERROR, make_string("No handler for document type '%s'", docType.toString().c_str()));
    }
    return latch.getUpdateResult();
}


GetResult
PersistenceEngine::get(const Bucket& b,
                       const document::FieldSet& fields,
                       const DocumentId& did,
                       Context& context) const
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    HandlerSnapshot::UP snapshot = getHandlerSnapshot();

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


CreateIteratorResult
PersistenceEngine::createIterator(const Bucket &bucket,
                                  const document::FieldSet& fields,
                                  const Selection &selection,
                                  IncludedVersions versions,
                                  Context & context)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    HandlerSnapshot::UP snapshot = getHandlerSnapshot();

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

    LockGuard guard(_iterators_lock);
    static IteratorId id_counter(0);
    IteratorId id(++id_counter);
    _iterators[id] = entry.release();
    return CreateIteratorResult(id);
}


IterateResult
PersistenceEngine::iterate(IteratorId id, uint64_t maxByteSize, Context&) const
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LockGuard guard(_iterators_lock);
    Iterators::const_iterator it = _iterators.find(id);
    if (it == _iterators.end()) {
        return IterateResult(Result::PERMANENT_ERROR, make_string("Unknown iterator with id %" PRIu64, id.getValue()));
    }
    if (it->second->in_use) {
        return IterateResult(Result::TRANSIENT_ERROR, make_string("Iterator with id %" PRIu64 " is already in use", id.getValue()));
    }
    it->second->in_use = true;
    guard.unlock();

    DocumentIterator &iterator = it->second->it;
    try {
        IterateResult result = iterator.iterate(maxByteSize);
        LockGuard guard2(_iterators_lock);
        it->second->in_use = false;
        return result;
    } catch (const std::exception & e) {
        IterateResult result(Result::PERMANENT_ERROR, make_string("Caught exception during visitor iterator.iterate() = '%s'", e.what()));
        LOG(warning, "Caught exception during visitor iterator.iterate() = '%s'", e.what());
        LockGuard guard2(_iterators_lock);
        it->second->in_use = false;
        return result;
    }
}


Result
PersistenceEngine::destroyIterator(IteratorId id, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LockGuard guard(_iterators_lock);
    Iterators::iterator it = _iterators.find(id);
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
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LOG(spam, "createBucket(%s)", b.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        FeedToken token(latch, Reply::UP(new DocumentReply(0)));
        handler->handleCreateBucket(token, b);
    }
    latch.await();
    return latch.getResult();
}


Result
PersistenceEngine::deleteBucket(const Bucket& b, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LOG(spam, "deleteBucket(%s)", b.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        FeedToken token(latch, Reply::UP(new DocumentReply(0)));
        handler->handleDeleteBucket(token, b);
    }
    latch.await();
    return latch.getResult();
}


BucketIdListResult
PersistenceEngine::getModifiedBuckets() const
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    typedef BucketIdListResultV MBV;
    MBV extraModifiedBuckets;
    {
        LockGuard guard(_lock);
        extraModifiedBuckets.swap(_extraModifiedBuckets);
    }
    HandlerSnapshot::UP snap = getHandlerSnapshot();
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
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LOG(spam, "split(%s, %s, %s)", source.toString().c_str(), target1.toString().c_str(), target2.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        FeedToken token(latch, Reply::UP(new DocumentReply(0)));
        handler->handleSplit(token, source, target1, target2);
    }
    latch.await();
    return latch.getResult();
}


Result
PersistenceEngine::join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&)
{
    std::shared_lock<std::shared_timed_mutex> rguard(getRLock());
    LOG(spam, "join(%s, %s, %s)", source1.toString().c_str(), source2.toString().c_str(), target.toString().c_str());
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    TransportLatch latch(snap->size());
    for (; snap->handlers().valid(); snap->handlers().next()) {
        IPersistenceHandler *handler = snap->handlers().get();
        FeedToken token(latch, Reply::UP(new DocumentReply(0)));
        handler->handleJoin(token, source1, source2, target);
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
PersistenceEngine::destroyIterators(void)
{
    Context context(storage::spi::LoadType(0, "default"),
                    storage::spi::Priority(0x80),
                    storage::spi::Trace::TraceLevel(0));
    for (;;) {
        IteratorId id;
        {
            LockGuard guard(_iterators_lock);
            if (_iterators.empty())
                break;
            id = _iterators.begin()->first;
        }
        Result res(destroyIterator(id, context));
        if (res.hasError()) {
            LOG(debug, "%ld iterator left. Can not destroy iterator '%ld'. Reason='%s'", _iterators.size(), id.getValue(), res.toString().c_str());
            FastOS_Thread::Sleep(100); // Sleep 0.1 seconds
        }
    }
}


void
PersistenceEngine::saveClusterState(const ClusterState &calc)
{
    auto clusterState = std::make_shared<ClusterState>(calc);
    {
        LockGuard guard(_lock);
        clusterState.swap(_clusterState);
    }
}

ClusterState::SP
PersistenceEngine::savedClusterState(void) const
{
    LockGuard guard(_lock);
    return _clusterState;
}

void
PersistenceEngine::propagateSavedClusterState(IPersistenceHandler &handler)
{
    ClusterState::SP clusterState(savedClusterState());
    if (clusterState.get() == NULL)
        return;
    // Propagate saved cluster state.
    // TODO: Fix race with new cluster state setting.
    GenericResultHandler resultHandler(1);
    handler.handleSetClusterState(*clusterState, resultHandler);
    resultHandler.await();
}

void
PersistenceEngine::grabExtraModifiedBuckets(IPersistenceHandler &handler)
{
    BucketIdListResultHandler resultHandler;
    handler.handleListBuckets(resultHandler);
    auto result = std::make_shared<BucketIdListResult>(resultHandler.getResult());
    LockGuard guard(_lock);
    _extraModifiedBuckets.push_back(result);
}


class ActiveBucketIdListResultHandler : public IBucketIdListResultHandler
{
private:
    typedef std::map<document::BucketId, size_t> BucketIdMap;
    typedef  std::pair<BucketIdMap::iterator, bool> IR;
    BucketIdMap _bucketMap;
public:
    ActiveBucketIdListResultHandler() : _bucketMap() { }

    virtual void handle(const BucketIdListResult &result) {
        const BucketIdListResult::List &buckets = result.getList();
        for (size_t i = 0; i < buckets.size(); ++i) {
            IR ir(_bucketMap.insert(std::make_pair(buckets[i], 1u)));
            if (!ir.second) {
                ++(ir.first->second);
            }
        }
    }

    const BucketIdMap & getBucketMap(void) const { return _bucketMap; }
};

void
PersistenceEngine::populateInitialBucketDB(IPersistenceHandler &targetHandler)
{
    HandlerSnapshot::UP snap = getHandlerSnapshot();
    
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


std::shared_lock<std::shared_timed_mutex>
PersistenceEngine::getRLock(void) const
{
    return std::shared_lock<std::shared_timed_mutex>(_rwMutex);
}


std::unique_lock<std::shared_timed_mutex>
PersistenceEngine::getWLock(void) const
{
    return std::unique_lock<std::shared_timed_mutex>(_rwMutex);
}


} // storage
