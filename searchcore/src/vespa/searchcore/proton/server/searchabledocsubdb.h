// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fast_access_doc_subdb.h"
#include "searchable_doc_subdb_configurer.h"
#include "searchable_feed_view.h"
#include "searchview.h"
#include "summaryadapter.h"
#include "igetserialnum.h"
#include "document_db_flush_config.h"
#include <vespa/config-rank-profiles.h>
#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/value_cache/constant_value_cache.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>

namespace proton {

class DocumentDBConfig;
struct IDocumentDBReferenceResolver;
struct MetricsWireService;
class GidToLidChangeHandler;

/**
 * The searchable sub database supports searching and keeps all attribute fields in memory and
 * inserts all index fields into the memory index in addition to storing documents in the
 * underlying document store.
 *
 * This class is used directly by the "0.ready" sub database for handling all ready documents.
 */
class
SearchableDocSubDB : public FastAccessDocSubDB,
                     public searchcorespi::IIndexManager::Reconfigurer

{
public:

    struct Context {
        using steady_time = vespalib::steady_time;
        const FastAccessDocSubDB::Context  _fastUpdCtx;
        matching::QueryLimiter            &_queryLimiter;
        const std::atomic<steady_time>    &_now_ref;
        vespalib::Executor                &_warmupExecutor;
        std::shared_ptr<search::diskindex::IPostingListCache> _posting_list_cache;

        Context(const FastAccessDocSubDB::Context &fastUpdCtx,
                matching::QueryLimiter &queryLimiter,
                const std::atomic<steady_time> & now_ref,
                vespalib:: Executor &warmupExecutor,
                std::shared_ptr<search::diskindex::IPostingListCache> posting_list_cache)
            : _fastUpdCtx(fastUpdCtx),
              _queryLimiter(queryLimiter),
              _now_ref(now_ref),
              _warmupExecutor(warmupExecutor),
              _posting_list_cache(std::move(posting_list_cache))
        { }
        ~Context();
    };

private:
    using Parent = FastAccessDocSubDB;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;

    searchcorespi::IIndexManager::SP            _indexMgr;
    IIndexWriter::SP                            _indexWriter;
    vespalib::VarHolder<SearchView::SP>         _rSearchView;
    vespalib::VarHolder<SearchableFeedView::SP> _rFeedView;
    vespalib::eval::ConstantTensorLoader        _tensorLoader;
    vespalib::eval::ConstantValueCache          _constantValueCache;
    SearchableDocSubDBConfigurer                _configurer;
    vespalib::Executor                         &_warmupExecutor;
    std::shared_ptr<GidToLidChangeHandler>      _realGidToLidChangeHandler;
    DocumentDBFlushConfig                       _flushConfig;
    std::shared_ptr<search::diskindex::IPostingListCache> _posting_list_cache;

    // Note: lifetime of indexManager must be handled by caller.
    std::shared_ptr<initializer::InitializerTask>
    createIndexManagerInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                                  const IndexConfig &indexCfg,
                                  std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager) const;

    void setupIndexManager(searchcorespi::IIndexManager::SP indexManager, const Schema& schema);
    void initFeedView(IAttributeWriter::SP attrWriter, const DocumentDBConfig &configSnapshot);
    void reconfigureMatchingMetrics(const vespa::config::search::RankProfilesConfig &config);
    void reconfigure_index_metrics(const Schema& schema);

    bool reconfigure(std::unique_ptr<Configure> configure) override;
    void reconfigureIndexSearchable();
    void syncViews();
    void applyFlushConfig(const DocumentDBFlushConfig &flushConfig);
    void propagateFlushConfig();
protected:
    IFlushTargetList getFlushTargetsInternal() override;
public:
    SearchableDocSubDB(const Config &cfg, const Context &ctx);
    ~SearchableDocSubDB() override;

    std::unique_ptr<DocumentSubDbInitializer>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const IndexConfig &indexCfg) const override;

    void setup(const DocumentSubDbInitializerResult &initResult) override;
    void initViews(const DocumentDBConfig &configSnapshot) override;

    std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num) override;
    IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentSubDBReconfig& prepared_reconfig) override;
    void setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &calc, OnDone onDone) override;

    void clearViews() override;

    std::shared_ptr<IAttributeWriter> get_attribute_writer() const override;

    std::shared_ptr<IAttributeManager> getAttributeManager() const override {
        return _rSearchView.get()->getAttributeManager();
    }

    const std::shared_ptr<searchcorespi::IIndexManager>& getIndexManager() const override {
        return _indexMgr;
    }

    const IIndexWriter::SP &getIndexWriter() const override {
        return _indexWriter;
    }

    SerialNum getOldestFlushedSerial() override;
    SerialNum getNewestFlushedSerial() override;
    void setIndexSchema(std::shared_ptr<const Schema> schema, SerialNum serialNum) override;
    size_t getNumActiveDocs() const override;
    search::SearchableStats getSearchableStats(bool clear_disk_io_stats) const override ;
    std::shared_ptr<IDocumentRetriever> getDocumentRetriever() override;
    matching::MatchingStats getMatcherStats(const std::string &rankProfile) const override;
    void close() override;
    std::shared_ptr<IDocumentDBReference> getDocumentDBReference() override;
    void tearDownReferences(IDocumentDBReferenceResolver &resolver) override;
    TransientResourceUsage get_transient_resource_usage() const override;
};

} // namespace proton
