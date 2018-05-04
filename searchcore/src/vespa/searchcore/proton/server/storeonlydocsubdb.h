// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdbconfig.h"
#include "idocumentsubdb.h"
#include "storeonlyfeedview.h"
#include "summaryadapter.h"
#include "tlssyncer.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreflushtarget.h>
#include <vespa/searchcore/proton/documentmetastore/ilidreusedelayer.h>
#include <vespa/searchcore/proton/matchengine/imatchhandler.h>
#include <vespa/searchcore/proton/summaryengine/isearchhandler.h>
#include <vespa/searchcore/proton/common/commit_time_tracker.h>
#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>

namespace proton {

class DocumentDBMetricsCollection;
class DocumentMetaStoreInitializerResult;
class FeedHandler;
class IDocumentSubDBOwner;
class MetricsWireService;
class ShrinkLidSpaceFlushTarget;
namespace initializer { class InitializerTask; }

namespace bucketdb { class IBucketDBHandlerInitializer; }
namespace documentmetastore { class LidReuseDelayerConfig; }

/**
 * Base class for a document sub database.
 */
class DocSubDB : public IDocumentSubDB
{
protected:
    IDocumentSubDBOwner               &_owner;
    search::transactionlog::SyncProxy &_tlSyncer;

public:
    DocSubDB(IDocumentSubDBOwner &owner, search::transactionlog::SyncProxy &tlSyncer)
        : IDocumentSubDB(),
          _owner(owner),
          _tlSyncer(tlSyncer)
    { }

    virtual ~DocSubDB() { }
    void close() override { }
};

class StoreOnlyDocSubDB;

/**
 * File header context used by the store-only sub database.
 *
 * This header context contains information that will be part of the header of all data files
 * written by a store-only sub database.
 */
class StoreOnlySubDBFileHeaderContext : public search::common::FileHeaderContext
{
    StoreOnlyDocSubDB &_owner;
    const search::common::FileHeaderContext &_parentFileHeaderContext;
    const DocTypeName &_docTypeName;
    vespalib::string _subDB;

public:
    StoreOnlySubDBFileHeaderContext(StoreOnlyDocSubDB &owner,
                                    const search::common::FileHeaderContext & parentFileHeaderContext,
                                    const DocTypeName &docTypeName,
                                    const vespalib::string &baseDir);
    ~StoreOnlySubDBFileHeaderContext();

    void addTags(vespalib::GenericHeader &header, const vespalib::string &name) const override;
};

/**
 * The store-only sub database handles only storing and retrieving of documents.
 *
 * lid<->gid mapping is handled via DocumentMetaStore and storing of documents via DocumentStore.
 * This class is used as base class for other sub databases and directly by the "1.removed"
 * sub database for for storing removed documents.
 */
class StoreOnlyDocSubDB : public DocSubDB
{
public:
    struct Config {
        const DocTypeName _docTypeName;
        const vespalib::string _subName;
        const vespalib::string _baseDir;
        const search::GrowStrategy _attributeGrow;
        const size_t _attributeGrowNumDocs;
        const uint32_t _subDbId;
        const SubDbType _subDbType;

        Config(const DocTypeName &docTypeName, const vespalib::string &subName,
               const vespalib::string &baseDir, const search::GrowStrategy &attributeGrow,
               size_t attributeGrowNumDocs, uint32_t subDbId, SubDbType subDbType);
        ~Config();
    };

    struct Context {
        IDocumentSubDBOwner &_owner;
        search::transactionlog::SyncProxy &_tlSyncer;
        const IGetSerialNum &_getSerialNum;
        const search::common::FileHeaderContext &_fileHeaderContext;
        searchcorespi::index::IThreadingService &_writeService;
        vespalib::ThreadStackExecutorBase &_summaryExecutor;
        std::shared_ptr<BucketDBOwner> _bucketDB;
        bucketdb::IBucketDBHandlerInitializer &_bucketDBHandlerInitializer;
        DocumentDBMetricsCollection &_metrics;
        std::mutex &_configMutex;
        const HwInfo &_hwInfo;

        Context(IDocumentSubDBOwner &owner,
                search::transactionlog::SyncProxy &tlSyncer,
                const IGetSerialNum &getSerialNum,
                const search::common::FileHeaderContext &fileHeaderContext,
                searchcorespi::index::IThreadingService &writeService,
                vespalib::ThreadStackExecutorBase &summaryExecutor,
                std::shared_ptr<BucketDBOwner> bucketDB,
                bucketdb::IBucketDBHandlerInitializer &
                bucketDBHandlerInitializer,
                DocumentDBMetricsCollection &metrics,
                std::mutex &configMutex,
                const HwInfo &hwInfo);
        ~Context();
    };


protected:
    const DocTypeName             _docTypeName;
    const vespalib::string        _subName;
    const vespalib::string        _baseDir;
    BucketDBOwner::SP             _bucketDB;
    bucketdb::IBucketDBHandlerInitializer &_bucketDBHandlerInitializer;
    IDocumentMetaStoreContext::SP _metaStoreCtx;
    const search::GrowStrategy    _attributeGrow;
    const size_t                  _attributeGrowNumDocs;
    // The following two serial numbers reflect state at program startup
    // and are used by replay logic.
    SerialNum                     _flushedDocumentMetaStoreSerialNum;
    SerialNum                     _flushedDocumentStoreSerialNum;
    DocumentMetaStore::SP         _dms;
    ISummaryManager::SP           _iSummaryMgr; // Interface class
private:
    SummaryManager::SP            _rSummaryMgr; // Our specific subclass
    ISummaryAdapter::SP           _summaryAdapter;
protected:
    searchcorespi::index::IThreadingService &_writeService;
    vespalib::ThreadStackExecutorBase       &_summaryExecutor;
    DocumentDBMetricsCollection             &_metrics;
    vespalib::VarHolder<ISearchHandler::SP> _iSearchView;
    vespalib::VarHolder<IFeedView::SP>      _iFeedView;
    std::mutex                             &_configMutex;
    HwInfo                                  _hwInfo;
private:
    const IGetSerialNum             &_getSerialNum;
    TlsSyncer                        _tlsSyncer;
    DocumentMetaStoreFlushTarget::SP _dmsFlushTarget;
    std::shared_ptr<ShrinkLidSpaceFlushTarget> _dmsShrinkTarget;

    IFlushTargetList getFlushTargets() override;
protected:
    const uint32_t                  _subDbId;
    const SubDbType                 _subDbType;
    StoreOnlySubDBFileHeaderContext _fileHeaderContext;
    std::unique_ptr<documentmetastore::ILidReuseDelayer> _lidReuseDelayer;
    CommitTimeTracker               _commitTimeTracker;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;

    std::shared_ptr<initializer::InitializerTask>
    createSummaryManagerInitializer(const search::LogDocumentStore::Config & protonSummaryCfg,
                                    const search::TuneFileSummary &tuneFile,
                                    search::IBucketizer::SP bucketizer,
                                    std::shared_ptr<SummaryManager::SP> result) const;

    void setupSummaryManager(SummaryManager::SP summaryManager);

    std::shared_ptr<initializer::InitializerTask>
    createDocumentMetaStoreInitializer(const search::TuneFileAttributes &tuneFile,
                                       std::shared_ptr<std::shared_ptr<DocumentMetaStoreInitializerResult>> result) const;

    void setupDocumentMetaStore(std::shared_ptr<DocumentMetaStoreInitializerResult> dmsResult);
    void initFeedView(const DocumentDBConfig &configSnapshot);
    virtual IFlushTargetList getFlushTargetsInternal();
    StoreOnlyFeedView::Context getStoreOnlyFeedViewContext(const DocumentDBConfig &configSnapshot);
    StoreOnlyFeedView::PersistentParams getFeedViewPersistentParams();
    vespalib::string getSubDbName() const;
    void updateLidReuseDelayer(const DocumentDBConfig *newConfigSnapshot);

    using LidReuseDelayerConfig = documentmetastore::LidReuseDelayerConfig;

    virtual void updateLidReuseDelayer(const LidReuseDelayerConfig &config);
    void reconfigure(const search::LogDocumentStore::Config & protonConfig);
public:
    StoreOnlyDocSubDB(const Config &cfg, const Context &ctx);
    ~StoreOnlyDocSubDB();

    uint32_t getSubDbId() const override { return _subDbId; }
    vespalib::string getName() const override { return _subName; }

    std::unique_ptr<DocumentSubDbInitializer>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const ProtonConfig::Index &indexCfg) const override;

    void setup(const DocumentSubDbInitializerResult &initResult) override;
    void initViews(const DocumentDBConfig &configSnapshot, const std::shared_ptr<matching::SessionManager> &sessionManager) override;

    IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver) override;
    virtual void setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &calc) override;

    ISearchHandler::SP getSearchView() const override { return _iSearchView.get(); }
    IFeedView::SP getFeedView() const override { return _iFeedView.get(); }

    void clearViews() override;

    const ISummaryManager::SP &getSummaryManager() const override { return _iSummaryMgr; }
    IAttributeManager::SP getAttributeManager() const override;
    const std::shared_ptr<searchcorespi::IIndexManager> & getIndexManager() const override;
    const ISummaryAdapter::SP & getSummaryAdapter() const override { return _summaryAdapter; }
    const std::shared_ptr<IIndexWriter> & getIndexWriter() const override;
    IDocumentMetaStoreContext & getDocumentMetaStoreContext() override { return *_metaStoreCtx; }
    size_t getNumDocs() const override;
    size_t getNumActiveDocs() const override;
    bool hasDocument(const document::DocumentId &id) override;
    void onReplayDone() override;
    void onReprocessDone(SerialNum serialNum) override;
    SerialNum getOldestFlushedSerial() override;
    SerialNum getNewestFlushedSerial() override;

    void pruneRemovedFields(SerialNum serialNum) override;
    void setIndexSchema(const Schema::SP &schema, SerialNum serialNum) override;
    search::SearchableStats getSearchableStats() const override;
    IDocumentRetriever::UP getDocumentRetriever() override;
    matching::MatchingStats getMatcherStats(const vespalib::string &rankProfile) const override;
    void close() override;
    std::shared_ptr<IDocumentDBReference> getDocumentDBReference() override;
    void tearDownReferences(IDocumentDBReferenceResolver &resolver) override;
};

}
