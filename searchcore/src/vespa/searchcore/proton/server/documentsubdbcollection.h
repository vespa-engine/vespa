// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <mutex>
#include <optional>

namespace vespalib {
    class Clock;
    class Executor;
    class ThreadStackExecutorBase;
}

namespace search {
    namespace attribute { class Interlock; }
    namespace common { class FileHeaderContext; }
    namespace transactionlog { class SyncProxy; }
}

namespace searchcorespi {
    class IFlushTarget;
    namespace index { struct IThreadingService; }
}

namespace proton {

class DocTypeName;
class DocumentDBConfig;
class DocumentDBReconfig;
class FeedHandler;
class HwInfo;
class IDocumentRetriever;
class IDocumentSubDB;
class IDocumentSubDBOwner;
class IFeedView;
class IGetSerialNum;
class MaintenanceController;
class ReconfigParams;
class RemoveDocumentsOperation;
struct DocumentDBTaggedMetrics;
struct IBucketStateCalculator;
struct IDocumentDBReferenceResolver;
struct MetricsWireService;

namespace matching { class QueryLimiter; }

namespace initializer { class InitializerTask; }

namespace index { struct IndexConfig; }

class DocumentSubDBCollection {
public:
    using SubDBVector = std::vector<IDocumentSubDB *>;
    using const_iterator = SubDBVector::const_iterator;
    using SerialNum = search::SerialNum;
    using OnDone = std::shared_ptr<vespalib::IDestructorCallback>;

private:
    using IFeedViewSP = std::shared_ptr<IFeedView>;
    using IBucketStateCalculatorSP = std::shared_ptr<IBucketStateCalculator>;
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
            vespalib::Executor &warmupExecutor,
            const search::common::FileHeaderContext &fileHeaderContext,
            std::shared_ptr<search::attribute::Interlock> attribute_interlock,
            MetricsWireService &metricsWireService,
            DocumentDBTaggedMetrics &metrics,
            matching::QueryLimiter & queryLimiter,
            const vespalib::Clock &clock,
            std::mutex &configMutex,
            const vespalib::string &baseDir,
            const HwInfo &hwInfo);
    ~DocumentSubDBCollection();

    void setBucketStateCalculator(const IBucketStateCalculatorSP &calc, OnDone onDone);

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
    const bucketdb::BucketDBOwner &getBucketDB() const { return *_bucketDB; }

    bucketdb::IBucketDBHandler &getBucketDBHandler() {
        return *_bucketDBHandler;
    }
    bucketdb::IBucketCreateNotifier &getBucketCreateNotifier() {
        return _bucketDBHandler->getBucketCreateNotifier();
    }

    std::shared_ptr<initializer::InitializerTask>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const index::IndexConfig & indexCfg);

    void initViews(const DocumentDBConfig &configSnapshot);
    void clearViews();
    void onReplayDone();
    void onReprocessDone(SerialNum serialNum);
    SerialNum getOldestFlushedSerial();
    SerialNum getNewestFlushedSerial();

    void pruneRemovedFields(SerialNum serialNum);

    std::unique_ptr<DocumentDBReconfig> prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num);
    void complete_prepare_reconfig(DocumentDBReconfig& prepared_reconfig, SerialNum serial_num);
    void applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                     SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentDBReconfig& prepared_reconfig);

    IFeedViewSP getFeedView();
    IFlushTargetList getFlushTargets();
    ReprocessingRunner &getReprocessingRunner() { return _reprocessingRunner; }
    double getReprocessingProgress() const;
    void close();
    void tearDownReferences(IDocumentDBReferenceResolver &resolver);
    void validateDocStore(FeedHandler & feedHandler, SerialNum serialNum);
};

} // namespace proton
