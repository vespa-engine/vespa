// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "combiningfeedview.h"
#include "commit_and_wait_document_retriever.h"
#include "document_subdb_collection_initializer.h"
#include "documentsubdbcollection.h"
#include "i_document_subdb_owner.h"
#include "maintenancecontroller.h"
#include "searchabledocsubdb.h"

#include <vespa/searchcore/proton/metrics/documentdb_metrics_collection.h>

using proton::matching::SessionManager;
using search::GrowStrategy;
using search::SerialNum;
using search::index::Schema;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

GrowStrategy
makeGrowStrategy(uint32_t docsInitialCapacity,
                 const DocumentSubDBCollection::ProtonConfig::Grow &growCfg)
{
    return GrowStrategy(docsInitialCapacity, growCfg.factor,
                        growCfg.add, growCfg.multivalueallocfactor);
}

}

DocumentSubDBCollection::DocumentSubDBCollection(
        IDocumentSubDBOwner &owner,
        search::transactionlog::SyncProxy &tlSyncer,
        const IGetSerialNum &getSerialNum,
        const DocTypeName &docTypeName,
        searchcorespi::index::IThreadingService &writeService,
        vespalib::ThreadExecutor &warmupExecutor,
        vespalib::ThreadStackExecutorBase &summaryExecutor,
        const search::common::FileHeaderContext &fileHeaderContext,
        MetricsWireService &metricsWireService,
        DocumentDBMetricsCollection &metrics,
        matching::QueryLimiter &queryLimiter,
        const vespalib::Clock &clock,
        std::mutex &configMutex,
        const vespalib::string &baseDir,
        const ProtonConfig &protonCfg,
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
      _bucketDBHandler()
{
    const ProtonConfig::Grow & growCfg = protonCfg.grow;
    const ProtonConfig::Distribution & distCfg = protonCfg.distribution;
    _bucketDB = std::make_shared<BucketDBOwner>();
    _bucketDBHandler.reset(new bucketdb::BucketDBHandler(*_bucketDB));
    GrowStrategy searchableGrowth = makeGrowStrategy(growCfg.initial * distCfg.searchablecopies, growCfg);
    GrowStrategy removedGrowth = makeGrowStrategy(std::max(1024l, growCfg.initial/100), growCfg);
    GrowStrategy notReadyGrowth = makeGrowStrategy(growCfg.initial * (distCfg.redundancy - distCfg.searchablecopies), growCfg);
    size_t attributeGrowNumDocs(growCfg.numdocs);
    size_t numSearcherThreads = protonCfg.numsearcherthreads;

    StoreOnlyDocSubDB::Context context(owner,
                                       tlSyncer,
                                       getSerialNum,
                                       fileHeaderContext,
                                       writeService,
                                       summaryExecutor,
                                       _bucketDB,
                                       *_bucketDBHandler,
                                       metrics.getLegacyMetrics(),
                                       configMutex,
                                       hwInfo);
    _subDBs.push_back
        (new SearchableDocSubDB
            (SearchableDocSubDB::Config(FastAccessDocSubDB::Config
                (StoreOnlyDocSubDB::Config(docTypeName,
                        "0.ready",
                        baseDir,
                        searchableGrowth,
                        attributeGrowNumDocs,
                        _readySubDbId,
                        SubDbType::READY),
                        true,
                        true,
                        false),
                        numSearcherThreads),
                SearchableDocSubDB::Context(FastAccessDocSubDB::Context
                        (context,
                         AttributeMetricsCollection(metrics.getTaggedMetrics().ready.attributes,
                                                    metrics.getLegacyMetrics().ready.attributes),
                        &metrics.getLegacyMetrics().attributes,
                        metricsWireService),
                        queryLimiter,
                        clock,
                        warmupExecutor)));
    _subDBs.push_back
        (new StoreOnlyDocSubDB(StoreOnlyDocSubDB::Config(docTypeName,
                                                     "1.removed",
                                                     baseDir,
                                                     removedGrowth,
                                                     attributeGrowNumDocs,
                                                     _remSubDbId,
                                                     SubDbType::REMOVED),
                             context));
    _subDBs.push_back
        (new FastAccessDocSubDB(FastAccessDocSubDB::Config
                (StoreOnlyDocSubDB::Config(docTypeName,
                        "2.notready",
                        baseDir,
                        notReadyGrowth,
                        attributeGrowNumDocs,
                        _notReadySubDbId,
                        SubDbType::NOTREADY),
                        true,
                        true,
                        true),
                FastAccessDocSubDB::Context(context,
                        AttributeMetricsCollection(metrics.getTaggedMetrics().notReady.attributes,
                                                   metrics.getLegacyMetrics().notReady.attributes),
                        NULL,
                        metricsWireService)));
}


DocumentSubDBCollection::~DocumentSubDBCollection()
{
    for (auto subDb : _subDBs) {
        delete subDb;
    }
    _bucketDB.reset();
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

IDocumentRetriever::SP
wrapRetriever(const IDocumentRetriever::SP &retriever,
              ICommitable &commit)
{
    return std::make_shared<CommitAndWaitDocumentRetriever>(retriever, commit);
}

}


void DocumentSubDBCollection::maintenanceSync(MaintenanceController &mc,
                                              ICommitable &commit) {
    RetrieversSP retrievers = getRetrievers();
    MaintenanceDocumentSubDB readySubDB(
            getReadySubDB()->getDocumentMetaStoreContext().getSP(),
            wrapRetriever((*retrievers)[_readySubDbId], commit),
            _readySubDbId);
    MaintenanceDocumentSubDB remSubDB(
            getRemSubDB()->getDocumentMetaStoreContext().getSP(),
            (*retrievers)[_remSubDbId],
            _remSubDbId);
    MaintenanceDocumentSubDB notReadySubDB(
            getNotReadySubDB()->getDocumentMetaStoreContext().getSP(),
            wrapRetriever((*retrievers)[_notReadySubDbId], commit),
            _notReadySubDbId);
    mc.syncSubDBs(readySubDB, remSubDB, notReadySubDB);
}

initializer::InitializerTask::SP
DocumentSubDBCollection::createInitializer(const DocumentDBConfig &configSnapshot,
                                           SerialNum configSerialNum,
                                           const ProtonConfig::Index & indexCfg)
{
    DocumentSubDbCollectionInitializer::SP task = std::make_shared<DocumentSubDbCollectionInitializer>();
    for (auto subDb : _subDBs) {
        DocumentSubDbInitializer::SP subTask(subDb->createInitializer(configSnapshot, configSerialNum, indexCfg));
        task->add(subTask);
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
    assert(views.size() >= 1);
    if (views.size() > 1) {
        return IFeedView::SP(new CombiningFeedView(views, _owner.getBucketSpace(), _calc));
    } else {
        assert(views.front() != NULL);
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

} // namespace proton
