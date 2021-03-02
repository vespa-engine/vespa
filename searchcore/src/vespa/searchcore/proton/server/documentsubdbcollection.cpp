// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "combiningfeedview.h"
#include "document_subdb_collection_initializer.h"
#include "documentsubdbcollection.h"
#include "i_document_subdb_owner.h"
#include "maintenancecontroller.h"
#include "searchabledocsubdb.h"
#include <vespa/searchcore/proton/persistenceengine/commit_and_wait_document_retriever.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using proton::matching::SessionManager;
using search::GrowStrategy;
using search::SerialNum;
using search::index::Schema;
using searchcorespi::IFlushTarget;
using vespalib::makeLambdaTask;

namespace proton {

DocumentSubDBCollection::Config::Config(size_t numSearchThreads)
    : _numSearchThreads(numSearchThreads)
{ }

DocumentSubDBCollection::DocumentSubDBCollection(
        IDocumentSubDBOwner &owner,
        search::transactionlog::SyncProxy &tlSyncer,
        const IGetSerialNum &getSerialNum,
        const DocTypeName &docTypeName,
        searchcorespi::index::IThreadingService &writeService,
        vespalib::SyncableThreadExecutor &warmupExecutor,
        const search::common::FileHeaderContext &fileHeaderContext,
        MetricsWireService &metricsWireService,
        DocumentDBTaggedMetrics &metrics,
        matching::QueryLimiter &queryLimiter,
        const vespalib::Clock &clock,
        std::mutex &configMutex,
        const vespalib::string &baseDir,
        const Config & cfg,
        const HwInfo &hwInfo)
    : _subDBs(),
      _owner(owner),
      _calc(),
      _readySubDbId(0),
      _remSubDbId(1),
      _notReadySubDbId(2),
      _retrievers(),
      _reprocessingRunner(),
      _bucketDB(),
      _bucketDBHandler(),
      _hwInfo(hwInfo)
{
    _bucketDB = std::make_shared<bucketdb::BucketDBOwner>();
    _bucketDBHandler = std::make_unique<bucketdb::BucketDBHandler>(*_bucketDB);

    StoreOnlyDocSubDB::Context context(owner, tlSyncer, getSerialNum, fileHeaderContext, writeService,
                                       _bucketDB, *_bucketDBHandler, metrics, configMutex, hwInfo);
    _subDBs.push_back
        (new SearchableDocSubDB(
                SearchableDocSubDB::Config(
                    FastAccessDocSubDB::Config(
                            StoreOnlyDocSubDB::Config(docTypeName, "0.ready", baseDir,
                                    _readySubDbId, SubDbType::READY),
                            true, true, false),
                    cfg.getNumSearchThreads()),
                SearchableDocSubDB::Context(
                        FastAccessDocSubDB::Context(context, metrics.ready.attributes, metricsWireService),
                        queryLimiter, clock, warmupExecutor)));

    _subDBs.push_back
        (new StoreOnlyDocSubDB(
                StoreOnlyDocSubDB::Config(docTypeName, "1.removed", baseDir,
               _remSubDbId, SubDbType::REMOVED),
                context));

    _subDBs.push_back
        (new FastAccessDocSubDB(
                FastAccessDocSubDB::Config(
                        StoreOnlyDocSubDB::Config(docTypeName, "2.notready", baseDir,
                                _notReadySubDbId, SubDbType::NOTREADY),
                        true, true, true),
                FastAccessDocSubDB::Context(context, metrics.notReady.attributes, metricsWireService)));
}


DocumentSubDBCollection::~DocumentSubDBCollection()
{
    size_t numThreads = std::min(_subDBs.size(), static_cast<size_t>(_hwInfo.cpu().cores()));
    vespalib::ThreadStackExecutor closePool(numThreads, 0x20000);
    while (!_subDBs.empty()) {
        closePool.execute(makeLambdaTask([subDB=_subDBs.back()]() { delete subDB; }));
        _subDBs.pop_back();
    }
    closePool.sync();

    _bucketDB.reset();

    RetrieversSP retrievers = _retrievers.get();
    _retrievers.clear();
    if (retrievers) {
        while (!retrievers->empty()) {
            auto retriever = std::move(retrievers->back());
            retrievers->pop_back();
            closePool.execute(makeLambdaTask([r = std::move(retriever)]() mutable { r.reset(); }));
        }
    }
    closePool.sync();
}

void
DocumentSubDBCollection::createRetrievers()
{
    RetrieversSP retrievers(std::make_shared<std::vector<IDocumentRetriever::SP>>());
    retrievers->reserve(_subDBs.size());
    for (auto subDb : _subDBs) {
        retrievers->emplace_back(subDb->getDocumentRetriever());
    }
    _retrievers.set(retrievers);
}

namespace {

std::shared_ptr<CommitAndWaitDocumentRetriever>
wrapRetriever(IDocumentRetriever::SP retriever, ILidCommitState & unCommittedLidsTracker)
{
    return std::make_shared<CommitAndWaitDocumentRetriever>(std::move(retriever), unCommittedLidsTracker);
}

}

DocumentSubDBCollection::RetrieversSP
DocumentSubDBCollection::getRetrievers(IDocumentRetriever::ReadConsistency consistency) {
    RetrieversSP list = _retrievers.get();

    if (consistency == IDocumentRetriever::ReadConsistency::STRONG) {
        auto wrappedList = std::make_shared<std::vector<IDocumentRetriever::SP>>();
        wrappedList->reserve(list->size());
        assert(list->size() == 3);
        wrappedList->push_back(wrapRetriever((*list)[_readySubDbId],
                                             getReadySubDB()->getUncommittedLidsTracker()));
        wrappedList->push_back(wrapRetriever((*list)[_remSubDbId],
                                             getRemSubDB()->getUncommittedLidsTracker()));
        wrappedList->push_back(wrapRetriever((*list)[_notReadySubDbId],
                                             getNotReadySubDB()->getUncommittedLidsTracker()));
        return wrappedList;
    } else {
        return list;
    }
}

void DocumentSubDBCollection::maintenanceSync(MaintenanceController &mc) {
    RetrieversSP retrievers = _retrievers.get();
    MaintenanceDocumentSubDB readySubDB(getReadySubDB()->getName(),
                                        _readySubDbId,
                                        getReadySubDB()->getDocumentMetaStoreContext().getSP(),
                                        wrapRetriever((*retrievers)[_readySubDbId],
                                                      getReadySubDB()->getUncommittedLidsTracker()),
                                        getReadySubDB()->getFeedView(),
                                        &getReadySubDB()->getUncommittedLidsTracker());
    MaintenanceDocumentSubDB remSubDB(getRemSubDB()->getName(),
                                      _remSubDbId,
                                      getRemSubDB()->getDocumentMetaStoreContext().getSP(),
                                      wrapRetriever((*retrievers)[_remSubDbId], getRemSubDB()->getUncommittedLidsTracker()),
                                      getRemSubDB()->getFeedView(),
                                      &getRemSubDB()->getUncommittedLidsTracker());
    MaintenanceDocumentSubDB notReadySubDB(getNotReadySubDB()->getName(),
                                           _notReadySubDbId,
                                           getNotReadySubDB()->getDocumentMetaStoreContext().getSP(),
                                           wrapRetriever((*retrievers)[_notReadySubDbId],
                                                         getNotReadySubDB()->getUncommittedLidsTracker()),
                                           getNotReadySubDB()->getFeedView(),
                                           &getNotReadySubDB()->getUncommittedLidsTracker());
    mc.syncSubDBs(readySubDB, remSubDB, notReadySubDB);
}

initializer::InitializerTask::SP
DocumentSubDBCollection::createInitializer(const DocumentDBConfig &configSnapshot,
                                           SerialNum configSerialNum,
                                           const index::IndexConfig & indexCfg)
{
    auto task = std::make_shared<DocumentSubDbCollectionInitializer>();
    for (auto subDb : _subDBs) {
        task->add(subDb->createInitializer(configSnapshot, configSerialNum, indexCfg));
    }
    return task;
}


void
DocumentSubDBCollection::initViews(const DocumentDBConfig &configSnapshot,
                                   const SessionManager::SP &sessionManager)
{
    for (auto subDb : _subDBs) {
        subDb->initViews(configSnapshot, sessionManager);
    }
}


void
DocumentSubDBCollection::clearViews()
{
    for (auto subDb : _subDBs) {
        subDb->clearViews();
    }
}


void
DocumentSubDBCollection::onReplayDone()
{
    for (auto subDb : _subDBs) {
        subDb->onReplayDone();
    }
}


void
DocumentSubDBCollection::onReprocessDone(SerialNum serialNum)
{
    for (auto subDb : _subDBs) {
        subDb->onReprocessDone(serialNum);
    }
}


SerialNum
DocumentSubDBCollection::getOldestFlushedSerial()
{
    SerialNum lowest = -1;
    for (auto subDb : _subDBs) {
        lowest = std::min(lowest, subDb->getOldestFlushedSerial());
    }
    return lowest;
}


SerialNum
DocumentSubDBCollection::getNewestFlushedSerial()
{
    SerialNum highest = 0;
    for (auto subDb : _subDBs) {
        highest = std::max(highest, subDb->getNewestFlushedSerial());
    }
    return highest;
}


void
DocumentSubDBCollection::pruneRemovedFields(SerialNum serialNum)
{
    for (auto subDb : _subDBs) {
        subDb->pruneRemovedFields(serialNum);
    }
}


void
DocumentSubDBCollection::applyConfig(const DocumentDBConfig &newConfigSnapshot,
                                     const DocumentDBConfig &oldConfigSnapshot,
                                     SerialNum serialNum,
                                     const ReconfigParams &params,
                                     IDocumentDBReferenceResolver &resolver)
{
    _reprocessingRunner.reset();
    for (auto subDb : _subDBs) {
        IReprocessingTask::List tasks;
        tasks = subDb->applyConfig(newConfigSnapshot, oldConfigSnapshot, serialNum, params, resolver);
        _reprocessingRunner.addTasks(tasks);
    }
}

IFeedView::SP
DocumentSubDBCollection::getFeedView()
{
    std::vector<IFeedView::SP> views;
    views.reserve(_subDBs.size());

    for (const auto subDb : _subDBs) {
        views.push_back(subDb->getFeedView());
    }
    IFeedView::SP newFeedView;
    assert( ! views.empty());
    if (views.size() > 1) {
        return std::make_shared<CombiningFeedView>(views, _owner.getBucketSpace(), _calc);
    } else {
        assert(views.front() != nullptr);
        return views.front();
    }
}

IFlushTarget::List
DocumentSubDBCollection::getFlushTargets()
{
    IFlushTarget::List ret;
    for (auto subDb : _subDBs) {
        IFlushTarget::List iTargets(subDb->getFlushTargets());
        ret.insert(ret.end(), iTargets.begin(), iTargets.end());
    }
    return ret;
}

double
DocumentSubDBCollection::getReprocessingProgress() const
{
    return _reprocessingRunner.getProgress();
}

void
DocumentSubDBCollection::close()
{
    for (auto subDb : _subDBs) {
        subDb->close();
    }
}

void
DocumentSubDBCollection::setBucketStateCalculator(const IBucketStateCalculatorSP &calc)
{
    _calc = calc;
    for (auto subDb : _subDBs) {
        subDb->setBucketStateCalculator(calc);
    }
}

void
DocumentSubDBCollection::tearDownReferences(IDocumentDBReferenceResolver &resolver)
{
    for (auto subDb : _subDBs) {
        subDb->tearDownReferences(resolver);
    }
}

void DocumentSubDBCollection::validateDocStore(FeedHandler & feedHandler, SerialNum serialNum) {
    for (auto subDb : _subDBs) {
        subDb->validateDocStore(feedHandler, serialNum);
    }
}

} // namespace proton
