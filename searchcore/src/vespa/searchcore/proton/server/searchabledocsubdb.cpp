// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchabledocsubdb.h"
#include "fast_access_document_retriever.h"
#include "document_subdb_initializer.h"
#include "document_subdb_reconfig.h"
#include "reconfig_params.h"
#include "i_document_subdb_owner.h"
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/flushengine/threadedflushtarget.h>
#include <vespa/searchcore/proton/index/index_manager_initializer.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/reference/document_db_reference.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_initializer.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/eval/eval/fast_value.h>

using vespa::config::search::RankProfilesConfig;
using proton::matching::MatchingStats;
using search::GrowStrategy;
using search::index::Schema;
using search::SerialNum;
using vespalib::eval::FastValueBuilderFactory;
using namespace searchcorespi;

namespace proton {

SearchableDocSubDB::Context::~Context() = default;

SearchableDocSubDB::SearchableDocSubDB(const Config &cfg, const Context &ctx)
    : FastAccessDocSubDB(cfg, ctx._fastUpdCtx),
      IIndexManager::Reconfigurer(),
      _indexMgr(),
      _indexWriter(),
      _rSearchView(),
      _rFeedView(),
      _tensorLoader(FastValueBuilderFactory::get()),
      _constantValueCache(_tensorLoader),
      _configurer(_iSummaryMgr, _rSearchView, _rFeedView, ctx._queryLimiter, _constantValueCache, ctx._now_ref,
                  getSubDbName(), ctx._fastUpdCtx._storeOnlyCtx._owner.getDistributionKey()),
      _warmupExecutor(ctx._warmupExecutor),
      _realGidToLidChangeHandler(std::make_shared<GidToLidChangeHandler>()),
      _flushConfig(),
      _posting_list_cache(ctx._posting_list_cache)
{
    _gidToLidChangeHandler = _realGidToLidChangeHandler;
}

SearchableDocSubDB::~SearchableDocSubDB()
{
    // XXX: Disk index wrappers should not live longer than index manager
    // which owns map of active disk indexes.
    clearViews();
}

void
SearchableDocSubDB::syncViews()
{
    _iSearchView.set(_rSearchView.get());
    _iFeedView.set(_rFeedView.get());
}

SerialNum
SearchableDocSubDB::getOldestFlushedSerial()
{
    SerialNum lowest(Parent::getOldestFlushedSerial());
    lowest = std::min(lowest, _indexMgr->getFlushedSerialNum());
    return lowest;
}

SerialNum
SearchableDocSubDB::getNewestFlushedSerial()
{
    SerialNum highest(Parent::getNewestFlushedSerial());
    highest = std::max(highest, _indexMgr->getFlushedSerialNum());
    return highest;
}

initializer::InitializerTask::SP
SearchableDocSubDB::
createIndexManagerInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                              const IndexConfig &indexCfg,
                              std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager) const
{
    const Schema & schema = *configSnapshot.getSchemaSP();
    std::string vespaIndexDir(_baseDir + "/index");
    // Note: const_cast for reconfigurer role
    return std::make_shared<IndexManagerInitializer>
        (vespaIndexDir, indexCfg, schema, configSerialNum, const_cast<SearchableDocSubDB &>(*this),
         _writeService, _warmupExecutor, configSnapshot.getTuneFileDocumentDBSP()->_index,
         configSnapshot.getTuneFileDocumentDBSP()->_attr, _fileHeaderContext, _posting_list_cache, std::move(indexManager));
}

void
SearchableDocSubDB::setupIndexManager(searchcorespi::IIndexManager::SP indexManager, const Schema& schema)
{
    _indexMgr = std::move(indexManager);
    _indexWriter = std::make_shared<IndexWriter>(_indexMgr);
    reconfigure_index_metrics(schema);
}

DocumentSubDbInitializer::UP
SearchableDocSubDB::
createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum, const IndexConfig &indexCfg) const
{
    auto result = Parent::createInitializer(configSnapshot, configSerialNum, indexCfg);
    auto indexTask = createIndexManagerInitializer(configSnapshot, configSerialNum, indexCfg,
                                                   result->writableResult().writableIndexManager());
    result->addDependency(indexTask);
    return result;
}

void
SearchableDocSubDB::setup(const DocumentSubDbInitializerResult &initResult)
{
    Parent::setup(initResult);
    setupIndexManager(initResult.indexManager(), *initResult.get_schema());
    _docIdLimit.set(_dms->getCommittedDocIdLimit());
    applyFlushConfig(initResult.getFlushConfig());
}

void
SearchableDocSubDB::
reconfigureMatchingMetrics(const RankProfilesConfig &cfg)
{
    _metricsWireService.cleanRankProfiles(_metrics);
    for (const auto &profile : cfg.rankprofile) {
        search::fef::Properties properties;
        for (const auto &property : profile.fef.property) {
            properties.add(property.name, property.value);
        }
        size_t numDocIdPartitions = search::fef::indexproperties::matching::NumThreadsPerSearch::lookup(properties);
        _metricsWireService.addRankProfile(_metrics, profile.name, numDocIdPartitions);
    }
}

std::unique_ptr<DocumentSubDBReconfig>
SearchableDocSubDB::prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num)
{
    auto alloc_strategy = new_config_snapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    AttributeCollectionSpecFactory attr_spec_factory(alloc_strategy, has_fast_access_attributes_only());
    auto docid_limit = _dms->getCommittedDocIdLimit();
    return _configurer.prepare_reconfig(new_config_snapshot, attr_spec_factory, reconfig_params, docid_limit, serial_num);
}

IReprocessingTask::List
SearchableDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentSubDBReconfig& prepared_reconfig)
{
    AllocStrategy alloc_strategy = newConfigSnapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    StoreOnlyDocSubDB::reconfigure(newConfigSnapshot.getStoreConfig(), alloc_strategy);
    IReprocessingTask::List tasks;
    applyFlushConfig(newConfigSnapshot.getMaintenanceConfigSP()->getFlushConfig());
    if (prepared_reconfig.has_matchers_changed()) {
        reconfigureMatchingMetrics(newConfigSnapshot.getRankProfilesConfig());
    }
    if (prepared_reconfig.has_attribute_manager_changed()) {
        proton::IAttributeManager::SP oldMgr = getAttributeManager();
        IReprocessingInitializer::UP initializer =
            _configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, params, resolver, prepared_reconfig, serialNum);
        if (initializer && initializer->hasReprocessors()) {
            tasks.emplace_back(createReprocessingTask(*initializer, newConfigSnapshot.getDocumentTypeRepoSP()));
        }
        {
            proton::IAttributeManager::SP newMgr = getAttributeManager();
            reconfigure_attribute_metrics(*newMgr);
        }
    } else {
        _configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, params, resolver, prepared_reconfig, serialNum);
    }
    syncViews();
    return tasks;
}

void
SearchableDocSubDB::applyFlushConfig(const DocumentDBFlushConfig &flushConfig)
{
    _flushConfig = flushConfig;
    propagateFlushConfig();
}

void
SearchableDocSubDB::propagateFlushConfig()
{
    uint32_t maxFlushed = is_node_retired_or_maintenance() ? _flushConfig.getMaxFlushedRetired() : _flushConfig.getMaxFlushed();
    _indexMgr->setMaxFlushed(maxFlushed);
}

void
SearchableDocSubDB::setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &calc, OnDone onDone)
{
    FastAccessDocSubDB::setBucketStateCalculator(calc, std::move(onDone));
    propagateFlushConfig();
}

void
SearchableDocSubDB::initViews(const DocumentDBConfig &configSnapshot)
{
    assert(_writeService.master().isCurrentThread());

    AttributeManager::SP attrMgr = getAndResetInitAttributeManager();
    const IIndexManager::SP &indexMgr = getIndexManager();
    Matchers::SP matchers = _configurer.createMatchers(configSnapshot);
    auto matchView = std::make_shared<MatchView>(std::move(matchers), indexMgr->getSearchable(), attrMgr,
                                                 _owner.session_manager(), _metaStoreCtx, _docIdLimit);
    _rSearchView.set(SearchView::create(
                                      getSummaryManager()->createSummarySetup(
                                              configSnapshot.getSummaryConfig(),
                                              configSnapshot.getJuniperrcConfig(),
                                              configSnapshot.getDocumentTypeRepoSP(),
                                              attrMgr,
                                              *configSnapshot.getSchemaSP()),
                                      std::move(matchView)));

    auto attrWriter = std::make_shared<AttributeWriter>(attrMgr);
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(std::move(attrWriter), configSnapshot);
    }
    reconfigureMatchingMetrics(configSnapshot.getRankProfilesConfig());
}

void
SearchableDocSubDB::initFeedView(IAttributeWriter::SP attrWriter,
                                 const DocumentDBConfig &configSnapshot)
{
    assert(_writeService.master().isCurrentThread());
    auto feedView = std::make_shared<SearchableFeedView>(getStoreOnlyFeedViewContext(configSnapshot),
            getFeedViewPersistentParams(),
            FastAccessFeedView::Context(std::move(attrWriter), _docIdLimit),
            SearchableFeedView::Context(getIndexWriter()));

    // XXX: Not exception safe.
    _rFeedView.set(feedView);
    syncViews();
}

/**
 * Handle reconfigure caused by index manager changing state.
 *
 * Flush engine is disabled (for all document dbs) during initial replay, the
 * flush engine has not started.
 */
bool
SearchableDocSubDB::reconfigure(std::unique_ptr<Configure> configure)
{
    assert(_writeService.master().isCurrentThread());

    getFeedView()->forceCommitAndWait(search::CommitParam(_getSerialNum.getSerialNum()));

    // Everything should be quiet now.

    SearchView::SP oldSearchView = _rSearchView.get();

    bool ret = true;

    if (configure)
        ret = configure->configure();  // Perform index manager reconfiguration now
    reconfigureIndexSearchable();
    return ret;
}

void
SearchableDocSubDB::reconfigureIndexSearchable()
{
    std::lock_guard<std::mutex> guard(_configMutex);
    // Create new views as needed.
    _configurer.reconfigureIndexSearchable();
    // Activate new search view at once
    _iSearchView.set(_rSearchView.get());
}

IFlushTarget::List
SearchableDocSubDB::getFlushTargetsInternal()
{
    IFlushTarget::List ret(Parent::getFlushTargetsInternal());

    IFlushTarget::List tmp = _indexMgr->getFlushTargets();
    ret.insert(ret.end(), tmp.begin(), tmp.end());

    return ret;
}

void
SearchableDocSubDB::reconfigure_index_metrics(const Schema& schema)
{
    std::vector<std::string> field_names;
    field_names.reserve(schema.getNumIndexFields());
    for (auto& field : schema.getIndexFields()) {
        field_names.emplace_back(field.getName());
    }
    _metricsWireService.set_index_fields(_metrics.ready.index, std::move(field_names));
}

void
SearchableDocSubDB::setIndexSchema(std::shared_ptr<const Schema> schema, SerialNum serialNum)
{
    assert(_writeService.master().isCurrentThread());

    SearchView::SP oldSearchView = _rSearchView.get();
    IFeedView::SP oldFeedView = _iFeedView.get();

    _indexMgr->setSchema(*schema, serialNum);
    reconfigureIndexSearchable();
    reconfigure_index_metrics(*schema);
}

size_t
SearchableDocSubDB::getNumActiveDocs() const
{
    IDocumentMetaStoreContext::SP metaStoreCtx = _metaStoreCtx;
    return (metaStoreCtx) ? metaStoreCtx->getReadGuard()->get().getNumActiveLids() : 0;
}

search::SearchableStats
SearchableDocSubDB::getSearchableStats() const
{
    return _indexMgr ? _indexMgr->getSearchableStats() : search::SearchableStats();
}

std::shared_ptr<IDocumentRetriever>
SearchableDocSubDB::getDocumentRetriever()
{
    return std::make_shared<FastAccessDocumentRetriever>(_rFeedView.get(), _rSearchView.get()->getAttributeManager());
}

MatchingStats
SearchableDocSubDB::getMatcherStats(const std::string &rankProfile) const
{
    return _rSearchView.get()->getMatcherStats(rankProfile);
}

void
SearchableDocSubDB::close()
{
    _realGidToLidChangeHandler->close();
    Parent::close();
}

std::shared_ptr<IDocumentDBReference>
SearchableDocSubDB::getDocumentDBReference()
{
    return std::make_shared<DocumentDBReference>(getAttributeManager(), _metaStoreCtx, _gidToLidChangeHandler);
}

void
SearchableDocSubDB::tearDownReferences(IDocumentDBReferenceResolver &resolver)
{
    auto attrMgr = getAttributeManager();
    resolver.teardown(*attrMgr);
}

void
SearchableDocSubDB::clearViews() {
    _rFeedView.clear();
    _rSearchView.clear();
    Parent::clearViews();
}

std::shared_ptr<IAttributeWriter>
SearchableDocSubDB::get_attribute_writer() const
{
    return _rFeedView.get()->getAttributeWriter();
}

TransientResourceUsage
SearchableDocSubDB::get_transient_resource_usage() const
{
    auto result = FastAccessDocSubDB::get_transient_resource_usage();
    // Transient disk usage is measured as the total disk usage of all current fusion indexes.
    // Transient memory usage is measured as the total memory usage of all memory indexes.
    auto stats = getSearchableStats();
    result.merge({stats.fusion_size_on_disk(), stats.memoryUsage().allocatedBytes()});
    return result;
}

} // namespace proton
