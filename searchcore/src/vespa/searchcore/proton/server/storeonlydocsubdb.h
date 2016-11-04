// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentdbconfig.h"
#include "idocumentsubdb.h"
#include "ifeedview.h"
#include "summaryadapter.h"
#include "tlssyncer.h"
#include <memory>
#include <vector>
#include <vespa/searchcore/config/config-proton.h>
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
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/util/varholder.h>


namespace proton
{

class MetricsWireService;
class LegacyDocumentDBMetrics;
class FeedHandler;

namespace bucketdb
{

class IBucketDBHandlerInitializer;

}

namespace documentmetastore { class LidReuseDelayerConfig; }

/**
 * Base class for a document sub database.
 */
class DocSubDB : public IDocumentSubDB
{
protected:
    IOwner			  &_owner;
    search::transactionlog::SyncProxy &_tlSyncer;

public:
    DocSubDB(IOwner &owner, search::transactionlog::SyncProxy &tlSyncer)
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
                                     const search::common::FileHeaderContext &
                                     parentFileHeaderContext,
                                     const DocTypeName &docTypeName,
                                     const vespalib::string &baseDir)
        : search::common::FileHeaderContext(),
          _owner(owner),
          _parentFileHeaderContext(parentFileHeaderContext),
          _docTypeName(docTypeName),
          _subDB()
    {
        size_t pos = baseDir.rfind('/');
        if (pos != vespalib::string::npos)
            _subDB = baseDir.substr(pos + 1);
        else
            _subDB = baseDir;
    }

    virtual void
    addTags(vespalib::GenericHeader &header,
            const vespalib::string &name) const;
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

        Config(const DocTypeName &docTypeName,
               const vespalib::string &subName,
               const vespalib::string &baseDir,
               const search::GrowStrategy &attributeGrow,
               size_t attributeGrowNumDocs,
               uint32_t subDbId,
               SubDbType subDbType)
            : _docTypeName(docTypeName),
              _subName(subName),
              _baseDir(baseDir + "/" + subName),
              _attributeGrow(attributeGrow),
              _attributeGrowNumDocs(attributeGrowNumDocs),
              _subDbId(subDbId),
              _subDbType(subDbType)
        {
        }
    };

    struct Context {
        IDocumentSubDB::IOwner &_owner;
        search::transactionlog::SyncProxy &_tlSyncer;
        const IGetSerialNum &_getSerialNum;
        const search::common::FileHeaderContext &_fileHeaderContext;
        searchcorespi::index::IThreadingService &_writeService;
        vespalib::ThreadStackExecutorBase &_summaryExecutor;
        std::shared_ptr<BucketDBOwner> _bucketDB;
        bucketdb::IBucketDBHandlerInitializer &_bucketDBHandlerInitializer;
        LegacyDocumentDBMetrics &_metrics;
        vespalib::Lock &_configLock;
        const HwInfo &_hwInfo;

        Context(IDocumentSubDB::IOwner &owner,
                search::transactionlog::SyncProxy &tlSyncer,
                const IGetSerialNum &getSerialNum,
                const search::common::FileHeaderContext &fileHeaderContext,
                searchcorespi::index::IThreadingService &writeService,
                vespalib::ThreadStackExecutorBase &summaryExecutor,
                std::shared_ptr<BucketDBOwner> bucketDB,
                bucketdb::IBucketDBHandlerInitializer &
                bucketDBHandlerInitializer,
                LegacyDocumentDBMetrics &metrics,
                vespalib::Lock &configLock,
                const HwInfo &hwInfo)
            : _owner(owner),
              _tlSyncer(tlSyncer),
              _getSerialNum(getSerialNum),
              _fileHeaderContext(fileHeaderContext),
              _writeService(writeService),
              _summaryExecutor(summaryExecutor),
              _bucketDB(bucketDB),
              _bucketDBHandlerInitializer(bucketDBHandlerInitializer),
              _metrics(metrics),
              _configLock(configLock),
              _hwInfo(hwInfo)
        {
        }
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
    ISummaryManager::SP           _iSummaryMgr;	// Interface class
private:
    SummaryManager::SP            _rSummaryMgr; // Our specific subclass
    ISummaryAdapter::SP           _summaryAdapter;
protected:
    searchcorespi::index::IThreadingService &_writeService;
    vespalib::ThreadStackExecutorBase       &_summaryExecutor;
    LegacyDocumentDBMetrics                 &_metrics;
    vespalib::VarHolder<ISearchHandler::SP> _iSearchView;
    vespalib::VarHolder<IFeedView::SP>      _iFeedView;
    vespalib::Lock                         &_configLock;
    HwInfo                                  _hwInfo;
private:
    const IGetSerialNum             &_getSerialNum;
    TlsSyncer                        _tlsSyncer;
    DocumentMetaStoreFlushTarget::SP _dmsFlushTarget;

    virtual IFlushTarget::List
    getFlushTargets();
protected:
    const uint32_t		  _subDbId;
    const SubDbType               _subDbType;
    StoreOnlySubDBFileHeaderContext _fileHeaderContext;
    std::unique_ptr<documentmetastore::ILidReuseDelayer> _lidReuseDelayer;
    CommitTimeTracker               _commitTimeTracker;

    initializer::InitializerTask::SP
    createSummaryManagerInitializer(const vespa::config::search::core::
                                    ProtonConfig::Summary protonSummaryCfg,
                                    const search::TuneFileSummary &tuneFile,
                                    search::IBucketizer::SP bucketizer,
                                    std::shared_ptr<SummaryManager::SP> result)
        const;

    void
    setupSummaryManager(SummaryManager::SP summaryManager);

    initializer::InitializerTask::SP
    createDocumentMetaStoreInitializer(const search::TuneFileAttributes &tuneFile,
                                       std::shared_ptr<DocumentMetaStoreInitializerResult::SP> result) const;

    void
    setupDocumentMetaStore(DocumentMetaStoreInitializerResult::SP dmsResult);

    void
    initFeedView(const DocumentDBConfig &configSnapshot);

    virtual IFlushTarget::List
    getFlushTargetsInternal();

    StoreOnlyFeedView::Context getStoreOnlyFeedViewContext(const DocumentDBConfig &configSnapshot);

    StoreOnlyFeedView::PersistentParams getFeedViewPersistentParams();

    vespalib::string getSubDbName() const {
        return vespalib::make_string("%s.%s",
                _owner.getName().c_str(), _subName.c_str());
    }

    void
    updateLidReuseDelayer(const DocumentDBConfig *newConfigSnapshot);

    using LidReuseDelayerConfig = documentmetastore::LidReuseDelayerConfig;

    virtual void
    updateLidReuseDelayer(const LidReuseDelayerConfig &config);

public:
    StoreOnlyDocSubDB(const Config &cfg,
                    const Context &ctx);

    virtual
    ~StoreOnlyDocSubDB();

    virtual uint32_t getSubDbId() const { return _subDbId; }

    virtual vespalib::string getName() const { return _subName; }

    virtual DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &configSnapshot,
                      SerialNum configSerialNum,
                      const search::index::Schema::SP &unionSchema,
                      const vespa::config::search::core::
                      ProtonConfig::Summary &protonSummaryCfg,
                      const vespa::config::search::core::
                      ProtonConfig::Index &indexCfg) const override;

    virtual void setup(const DocumentSubDbInitializerResult &initResult)
        override;

    virtual void
    initViews(const DocumentDBConfig &configSnapshot,
              const matching::SessionManager::SP &sessionManager);

    virtual IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot,
                const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum,
                const ReconfigParams params);

    virtual ISearchHandler::SP
    getSearchView() const
    {
        return _iSearchView.get();
    }

    virtual IFeedView::SP
    getFeedView() const
    {
        return _iFeedView.get();
    }

    virtual void
    clearViews()
    {
        _iFeedView.clear();
        _iSearchView.clear();
    }

    /**
     * Returns the summary manager that this database uses to manage
     * document summaries of the corresponding document type.
     *
     * @return The summary manager.
     */
    virtual const ISummaryManager::SP &
    getSummaryManager() const
    {
        return _iSummaryMgr;
    }

    virtual proton::IAttributeManager::SP
    getAttributeManager() const;

    virtual const IIndexManager::SP &
    getIndexManager() const;

    virtual const ISummaryAdapter::SP &
    getSummaryAdapter() const
    {
        return _summaryAdapter;
    }

    virtual const IIndexWriter::SP &
    getIndexWriter() const;

    virtual IDocumentMetaStoreContext &
    getDocumentMetaStoreContext()
    {
        return *_metaStoreCtx;
    }

    virtual size_t
    getNumDocs() const;

    virtual size_t
    getNumActiveDocs() const override;

    virtual bool
    hasDocument(const document::DocumentId &id);

    virtual void
    onReplayDone();

    virtual void
    onReprocessDone(SerialNum serialNum);

    virtual SerialNum
    getOldestFlushedSerial();

    virtual SerialNum
    getNewestFlushedSerial();

    virtual void
    wipeHistory(SerialNum wipeSerial,
                const search::index::Schema &newHistorySchema,
                const search::index::Schema &wipeSchema);

    virtual void
    setIndexSchema(const search::index::Schema::SP &schema,
                   const search::index::Schema::SP &fusionSchema);

    virtual search::SearchableStats
    getSearchableStats() const;

    virtual IDocumentRetriever::UP
    getDocumentRetriever();

    virtual matching::MatchingStats
    getMatcherStats(const vespalib::string &rankProfile) const;

    void close() override;
};

} // namespace proton

