// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>

namespace vespalib {
    class Clock;
    class SyncableThreadExecutor;
    class ThreadStackExecutorBase;
}

namespace search {
    namespace common { class FileHeaderContext; }
    namespace transactionlog { class SyncProxy; }
}

namespace searchcorespi {
    class IFlushTarget;
    namespace index { struct IThreadingService; }
}

namespace proton {

class DocumentDBConfig;
struct DocumentDBTaggedMetrics;
class MaintenanceController;
struct MetricsWireService;
struct IDocumentDBReferenceResolver;
class IGetSerialNum;
class DocTypeName;
class HwInfo;
class IFeedView;
struct IBucketStateCalculator;
class IDocumentSubDBOwner;
class IDocumentSubDB;
class IDocumentRetriever;
class ReconfigParams;
class RemoveDocumentsOperation;
class FeedHandler;

namespace matching {
    class QueryLimiter;
    class SessionManager;
}

namespace initializer { class InitializerTask; }

namespace index { struct IndexConfig; }

class DocumentSubDBCollection {
public:
    using SubDBVector = std::vector<IDocumentSubDB *>;
    using const_iterator = SubDBVector::const_iterator;
    using SerialNum = search::SerialNum;
    class Config {
    public:
        Config(size_t numSearchThreads);
        size_t getNumSearchThreads() const noexcept { return _numSearchThreads; }
    private:
        const size_t       _numSearchThreads;
    };

private:
    using IFeedViewSP = std::shared_ptr<IFeedView>;
    using IBucketStateCalculatorSP = std::shared_ptr<IBucketStateCalculator>;
    using SessionManagerSP = std::shared_ptr<matching::SessionManager>;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    SubDBVector _subDBs;
    IDocumentSubDBOwner     &_owner;
    IBucketStateCalculatorSP _calc;
    const uint32_t _readySubDbId;
    const uint32_t _remSubDbId;
    const uint32_t _notReadySubDbId;
    using RetrieversSP = std::shared_ptr<std::vector<std::shared_ptr<IDocumentRetriever>> >;
    vespalib::VarHolder<RetrieversSP> _retrievers;
    ReprocessingRunner _reprocessingRunner;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    std::unique_ptr<bucketdb::BucketDBHandler> _bucketDBHandler;
    HwInfo _hwInfo;

public:
    DocumentSubDBCollection(
            IDocumentSubDBOwner &owner,
            search::transactionlog::SyncProxy &tlSyncer,
            const IGetSerialNum &getSerialNum,
            const DocTypeName &docTypeName,
            searchcorespi::index::IThreadingService &writeService,
            vespalib::SyncableThreadExecutor &warmupExecutor,
            const search::common::FileHeaderContext &fileHeaderContext,
            MetricsWireService &metricsWireService,
            DocumentDBTaggedMetrics &metrics,
            matching::QueryLimiter & queryLimiter,
            const vespalib::Clock &clock,
            std::mutex &configMutex,
            const vespalib::string &baseDir,
            const Config & cfg,
            const HwInfo &hwInfo);
    ~DocumentSubDBCollection();

    void setBucketStateCalculator(const IBucketStateCalculatorSP &calc);

    void createRetrievers();
    void maintenanceSync(MaintenanceController &mc);

    // Internally synchronized
    RetrieversSP getRetrievers(IDocumentRetriever::ReadConsistency consistency);

    IDocumentSubDB *getReadySubDB() { return _subDBs[_readySubDbId]; }
    const IDocumentSubDB *getReadySubDB() const { return _subDBs[_readySubDbId]; }
    IDocumentSubDB *getRemSubDB() { return _subDBs[_remSubDbId]; }
    const IDocumentSubDB *getRemSubDB() const { return _subDBs[_remSubDbId]; }
    IDocumentSubDB *getNotReadySubDB() { return _subDBs[_notReadySubDbId]; }
    const IDocumentSubDB *getNotReadySubDB() const { return _subDBs[_notReadySubDbId]; }

    const_iterator begin() const { return _subDBs.begin(); }
    const_iterator end() const { return _subDBs.end(); }

    bucketdb::BucketDBOwner &getBucketDB() { return *_bucketDB; }

    bucketdb::IBucketDBHandler &getBucketDBHandler() {
        return *_bucketDBHandler;
    }
    bucketdb::IBucketCreateNotifier &getBucketCreateNotifier() {
        return _bucketDBHandler->getBucketCreateNotifier();
    }

    std::shared_ptr<initializer::InitializerTask>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const index::IndexConfig & indexCfg);

    void initViews(const DocumentDBConfig &configSnapshot, const SessionManagerSP &sessionManager);
    void clearViews();
    void onReplayDone();
    void onReprocessDone(SerialNum serialNum);
    SerialNum getOldestFlushedSerial();
    SerialNum getNewestFlushedSerial();

    void pruneRemovedFields(SerialNum serialNum);

    void applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                     SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver);

    IFeedViewSP getFeedView();
    IFlushTargetList getFlushTargets();
    ReprocessingRunner &getReprocessingRunner() { return _reprocessingRunner; }
    double getReprocessingProgress() const;
    void close();
    void tearDownReferences(IDocumentDBReferenceResolver &resolver);
    void validateDocStore(FeedHandler & feedHandler, SerialNum serialNum);
};

} // namespace proton
