// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>

namespace vespalib {
    class Clock;
    class ThreadExecutor;
    class ThreadStackExecutorBase;
}

namespace search {
    namespace common { class FileHeaderContext; }
    namespace transactionlog { class SyncProxy; }
}

namespace searchcorespi {
    class IFlushTarget;
    namespace index { class IThreadingService; }
}

namespace proton {

class DocumentDBConfig;
class DocumentDBMetricsCollection;
class MaintenanceController;
class MetricsWireService;
class ICommitable;
class IDocumentDBReferenceResolver;
class IGetSerialNum;
class DocTypeName;
class HwInfo;
class IFeedView;
struct IBucketStateCalculator;
class IDocumentSubDBOwner;
class IDocumentSubDB;
class IDocumentRetriever;
class IRreprocessingTask;
class ReconfigParams;
class RemoveDocumentsOperation;
class FeedHandler;

namespace matching {
    class QueryLimiter;
    class SessionManager;
}

namespace initializer { class InitializerTask; }

class DocumentSubDBCollection {
public:
    typedef std::vector<IDocumentSubDB *> SubDBVector;
    typedef SubDBVector::const_iterator const_iterator;
    typedef search::SerialNum      SerialNum;

private:
    using IFeedViewSP = std::shared_ptr<IFeedView>;
    using IBucketStateCalculatorSP = std::shared_ptr<IBucketStateCalculator>;
    using SessionManagerSP = std::shared_ptr<matching::SessionManager>;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    SubDBVector _subDBs;
    IBucketStateCalculatorSP _calc;
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
            matching::QueryLimiter & queryLimiter,
            const vespalib::Clock &clock,
            std::mutex &configMutex,
            const vespalib::string &baseDir,
            const vespa::config::search::core::ProtonConfig &protonCfg,
            const HwInfo &hwInfo);
    ~DocumentSubDBCollection();

    void setBucketStateCalculator(const IBucketStateCalculatorSP &calc) {
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

    std::shared_ptr<initializer::InitializerTask>
    createInitializer(const DocumentDBConfig &configSnapshot,
                      SerialNum configSerialNum,
                      const vespa::config::search::core::ProtonConfig::Summary &protonSummaryCfg,
                      const vespa::config::search::core::ProtonConfig::Index & indexCfg);

    void
    initViews(const DocumentDBConfig &configSnapshot,
              const SessionManagerSP &sessionManager);

    void clearViews();
    void onReplayDone();
    void onReprocessDone(SerialNum serialNum);
    SerialNum getOldestFlushedSerial();
    SerialNum getNewestFlushedSerial();

    void pruneRemovedFields(SerialNum serialNum);

    void
    applyConfig(const DocumentDBConfig &newConfigSnapshot,
                const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver);

    IFeedViewSP getFeedView();
    IFlushTargetList getFlushTargets();
    ReprocessingRunner &getReprocessingRunner() { return _reprocessingRunner; }
    double getReprocessingProgress() const;
    void close();
    void tearDownReferences(IDocumentDBReferenceResolver &resolver);
    void validateDocStore(FeedHandler & feedHandler, SerialNum serialNum);
};

} // namespace proton
