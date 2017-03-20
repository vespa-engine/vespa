// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ibucketstatecalculator.h"
#include "idocumentsubdb.h"
#include "ifeedview.h"
#include "searchable_doc_subdb_configurer.h"
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_task.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <mutex>

namespace proton {
class DocumentDBConfig;
class DocumentDBMetricsCollection;
class MaintenanceController;
class MetricsWireService;
class ICommitable;
class IDocumentDBReferenceResolver;
class IGetSerialNum;

class DocumentSubDBCollection {
public:
    typedef std::vector<IDocumentSubDB *> SubDBVector;
    typedef SubDBVector::const_iterator const_iterator;
    typedef search::SerialNum      SerialNum;

private:
    SubDBVector _subDBs;
    IBucketStateCalculator::SP _calc;
    const uint32_t _readySubDbId;
    const uint32_t _remSubDbId;
    const uint32_t _notReadySubDbId;
    typedef std::shared_ptr<std::vector<std::shared_ptr<IDocumentRetriever>> > RetrieversSP;
    vespalib::VarHolder<RetrieversSP> _retrievers;
    typedef std::vector<std::shared_ptr<IReprocessingTask>> ReprocessingTasks;
    ReprocessingRunner _reprocessingRunner;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    std::unique_ptr<bucketdb::BucketDBHandler> _bucketDBHandler;

public:
    DocumentSubDBCollection(
            IDocumentSubDB::IOwner &owner,
            search::transactionlog::SyncProxy &tlSyncer,
            const IGetSerialNum &getSerialNum,
            const DocTypeName &docTypeName,
            searchcorespi::index::IThreadingService &writeService,
            vespalib::ThreadExecutor &warmupExecutor,
            vespalib::ThreadStackExecutorBase &summaryExecutor,
            const search::common::FileHeaderContext &fileHeaderContext,
            MetricsWireService &metricsWireService,
            DocumentDBMetricsCollection &metrics,
            matching::QueryLimiter & queryLimiter,
            const vespalib::Clock &clock,
            std::mutex &configMutex,
            const vespalib::string &baseDir,
            const vespa::config::search::core::ProtonConfig &protonCfg,
            const HwInfo &hwInfo);
    ~DocumentSubDBCollection();

    void setBucketStateCalculator(const IBucketStateCalculator::SP &calc) {
        _calc = calc;
    }

    void createRetrievers();
    void maintenanceSync(MaintenanceController &mc, ICommitable &commit);

    // Internally synchronized
    RetrieversSP getRetrievers() {
        return _retrievers.get();
    }

    IDocumentSubDB *getReadySubDB() { return _subDBs[_readySubDbId]; }
    const IDocumentSubDB *getReadySubDB() const { return _subDBs[_readySubDbId]; }
    IDocumentSubDB *getRemSubDB() { return _subDBs[_remSubDbId]; }
    const IDocumentSubDB *getRemSubDB() const { return _subDBs[_remSubDbId]; }
    IDocumentSubDB *getNotReadySubDB() { return _subDBs[_notReadySubDbId]; }
    const IDocumentSubDB *getNotReadySubDB() const { return _subDBs[_notReadySubDbId]; }

    const_iterator begin() const { return _subDBs.begin(); }
    const_iterator end() const { return _subDBs.end(); }

    BucketDBOwner &getBucketDB() { return *_bucketDB; }

    bucketdb::IBucketDBHandler &getBucketDBHandler() {
        return *_bucketDBHandler;
    }

    initializer::InitializerTask::SP
    createInitializer(const DocumentDBConfig &configSnapshot,
                      SerialNum configSerialNum,
                      const vespa::config::search::core::ProtonConfig::Summary &protonSummaryCfg,
                      const vespa::config::search::core::ProtonConfig::Index & indexCfg);

    void
    initViews(const DocumentDBConfig &configSnapshot,
              const matching::SessionManager::SP &sessionManager);

    void clearViews(void);
    void onReplayDone(void);
    void onReprocessDone(SerialNum serialNum);
    SerialNum getOldestFlushedSerial(void);
    SerialNum getNewestFlushedSerial(void);

    void
    wipeHistory(SerialNum wipeSerial,
                const search::index::Schema &wipeSchema);

    void
    applyConfig(const DocumentDBConfig &newConfigSnapshot,
                const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver);

    IFeedView::SP getFeedView();
    IFlushTarget::List getFlushTargets();
    ReprocessingRunner &getReprocessingRunner() { return _reprocessingRunner; }
    double getReprocessingProgress() const;
    void close();
    void tearDownReferences(IDocumentDBReferenceResolver &resolver);
};


} // namespace proton

