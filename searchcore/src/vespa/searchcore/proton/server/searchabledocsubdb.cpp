// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchabledocsubdb.h"
#include "fast_access_document_retriever.h"
#include "document_subdb_initializer.h"
#include "reconfig_params.h"
#include "i_document_subdb_owner.h"
#include "ibucketstatecalculator.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/flushengine/threadedflushtarget.h>
#include <vespa/searchcore/proton/index/index_manager_initializer.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/metrics/legacy_documentdb_metrics.h>
#include <vespa/searchcore/proton/reference/document_db_reference.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_handler.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/util/exceptions.h>

using vespa::config::search::AttributesConfig;
using vespa::config::search::RankProfilesConfig;
using vespa::config::search::core::ProtonConfig;
using proton::matching::MatchingStats;
using proton::matching::SessionManager;
using search::AttributeGuard;
using search::AttributeVector;
using search::GrowStrategy;
using search::TuneFileDocumentDB;
using search::index::Schema;
using search::SerialNum;
using vespalib::IllegalStateException;
using vespalib::ThreadStackExecutorBase;
using namespace searchcorespi;

namespace proton {

SearchableDocSubDB::SearchableDocSubDB(const Config &cfg, const Context &ctx)
    : FastAccessDocSubDB(cfg._fastUpdCfg, ctx._fastUpdCtx),
      IIndexManager::Reconfigurer(),
      _indexMgr(),
      _indexWriter(),
      _rSearchView(),
      _rFeedView(),
      _tensorLoader(vespalib::tensor::DefaultTensorEngine::ref()),
      _constantValueCache(_tensorLoader),
      _constantValueRepo(_constantValueCache),
      _configurer(_iSummaryMgr, _rSearchView, _rFeedView, ctx._queryLimiter, _constantValueRepo, ctx._clock,
                  getSubDbName(), ctx._fastUpdCtx._storeOnlyCtx._owner.getDistributionKey()),
      _numSearcherThreads(cfg._numSearcherThreads),
      _warmupExecutor(ctx._warmupExecutor),
      _realGidToLidChangeHandler(std::make_shared<GidToLidChangeHandler>()),
      _flushConfig(),
      _nodeRetired(false)
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
    _owner.syncFeedView();
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
createIndexManagerInitializer(const DocumentDBConfig &configSnapshot,
                              SerialNum configSerialNum,
                              const ProtonConfig::Index &indexCfg,
                              std::shared_ptr<searchcorespi::IIndexManager::SP> indexManager) const
{
    Schema::SP schema(configSnapshot.getSchemaSP());
    vespalib::string vespaIndexDir(_baseDir + "/index");
    // Note: const_cast for reconfigurer role
    return std::make_shared<IndexManagerInitializer>
        (vespaIndexDir,
         searchcorespi::index::WarmupConfig(indexCfg.warmup.time, indexCfg.warmup.unpack),
         indexCfg.maxflushed,
         indexCfg.cache.size,
         *schema,
         configSerialNum,
         const_cast<SearchableDocSubDB &>(*this),
         _writeService,
         _warmupExecutor,
         configSnapshot.getTuneFileDocumentDBSP()->_index,
         configSnapshot.getTuneFileDocumentDBSP()->_attr,
         _fileHeaderContext,
         indexManager);
}

void
SearchableDocSubDB::setupIndexManager(searchcorespi::IIndexManager::SP indexManager)
{
    _indexMgr = indexManager;
    _indexWriter.reset(new IndexWriter(_indexMgr));
}

DocumentSubDbInitializer::UP
SearchableDocSubDB::
createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                  const ProtonConfig::Index &indexCfg) const
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
    setupIndexManager(initResult.indexManager());
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

IReprocessingTask::List
SearchableDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver)
{
    StoreOnlyDocSubDB::reconfigure(newConfigSnapshot.getStoreConfig());
    IReprocessingTask::List tasks;
    updateLidReuseDelayer(&newConfigSnapshot);
    applyFlushConfig(newConfigSnapshot.getMaintenanceConfigSP()->getFlushConfig());
    if (params.shouldMatchersChange() && _addMetrics) {
        reconfigureMatchingMetrics(newConfigSnapshot.getRankProfilesConfig());
    }
    if (params.shouldAttributeManagerChange()) {
        proton::IAttributeManager::SP oldMgr = getAttributeManager();
        AttributeCollectionSpec::UP attrSpec =
            createAttributeSpec(newConfigSnapshot.getAttributesConfig(), serialNum);
        IReprocessingInitializer::UP initializer =
                _configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, *attrSpec, params, resolver);
        if (initializer.get() != nullptr && initializer->hasReprocessors()) {
            tasks.push_back(IReprocessingTask::SP(createReprocessingTask(*initializer,
                    newConfigSnapshot.getDocumentTypeRepoSP()).release()));
        }
        proton::IAttributeManager::SP newMgr = getAttributeManager();
        if (_addMetrics) {
            reconfigureAttributeMetrics(*newMgr, *oldMgr);
        }
    } else {
        _configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, params, resolver);
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
    uint32_t maxFlushed = _nodeRetired ? _flushConfig.getMaxFlushedRetired() : _flushConfig.getMaxFlushed();
    _indexMgr->setMaxFlushed(maxFlushed);
}

void
SearchableDocSubDB::setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> &calc)
{
    _nodeRetired = calc->nodeRetired();
    propagateFlushConfig();
}

void
SearchableDocSubDB::initViews(const DocumentDBConfig &configSnapshot, const SessionManager::SP &sessionManager)
{
    assert(_writeService.master().isCurrentThread());

    AttributeManager::SP attrMgr = getAndResetInitAttributeManager();
    const Schema::SP &schema = configSnapshot.getSchemaSP();
    const IIndexManager::SP &indexMgr = getIndexManager();
    _constantValueRepo.reconfigure(configSnapshot.getRankingConstants());
    Matchers::SP matchers(_configurer.createMatchers(schema, configSnapshot.getRankProfilesConfig()).release());
    MatchView::SP matchView(new MatchView(matchers, indexMgr->getSearchable(), attrMgr,
                                          sessionManager, _metaStoreCtx, _docIdLimit));
    _rSearchView.set(SearchView::SP(
                              new SearchView(
                                      getSummaryManager()->createSummarySetup(
                                              configSnapshot.getSummaryConfig(),
                                              configSnapshot.getSummarymapConfig(),
                                              configSnapshot.getJuniperrcConfig(),
                                              configSnapshot.getDocumentTypeRepoSP(),
                                              matchView->getAttributeManager()),
                                      matchView)));

    IAttributeWriter::SP attrWriter(new AttributeWriter(attrMgr));
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(attrWriter, configSnapshot);
    }
    if (_addMetrics) {
        reconfigureMatchingMetrics(configSnapshot.getRankProfilesConfig());
    }
}

void
SearchableDocSubDB::initFeedView(const IAttributeWriter::SP &attrWriter,
                                 const DocumentDBConfig &configSnapshot)
{
    assert(_writeService.master().isCurrentThread());
    SearchableFeedView::UP feedView(new SearchableFeedView(getStoreOnlyFeedViewContext(configSnapshot),
            getFeedViewPersistentParams(),
            FastAccessFeedView::Context(attrWriter, _docIdLimit),
            SearchableFeedView::Context(getIndexWriter())));

    // XXX: Not exception safe.
    _rFeedView.set(SearchableFeedView::SP(feedView.release()));
    syncViews();
}

/**
 * Handle reconfigure caused by index manager changing state.
 *
 * Flush engine is disabled (for all document dbs) during initial replay and
 * recovery feed modes, the flush engine has not started.  For a resurrected
 * document type, flushing might occur during replay.
 */
bool
SearchableDocSubDB::
reconfigure(vespalib::Closure0<bool>::UP closure)
{
    assert(_writeService.master().isCurrentThread());

    _writeService.sync();

    // Everything should be quiet now.

    SearchView::SP oldSearchView = _rSearchView.get();
    IFeedView::SP oldFeedView = _iFeedView.get();

    bool ret = true;

    if (closure.get() != NULL)
        ret = closure->call();  // Perform index manager reconfiguration now
    reconfigureIndexSearchable();
    return ret;
}

void
SearchableDocSubDB::reconfigureIndexSearchable()
{
    std::lock_guard<std::mutex> guard(_configMutex);
    // Create new views as needed.
    _configurer.reconfigureIndexSearchable();
    // Activate new feed view at once
    syncViews();
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
SearchableDocSubDB::setIndexSchema(const Schema::SP &schema, SerialNum serialNum)
{
    assert(_writeService.master().isCurrentThread());

    SearchView::SP oldSearchView = _rSearchView.get();
    IFeedView::SP oldFeedView = _iFeedView.get();

    _indexMgr->setSchema(*schema, serialNum);
    reconfigureIndexSearchable();
}

size_t
SearchableDocSubDB::getNumActiveDocs() const
{
    return _metaStoreCtx->getReadGuard()->get().getNumActiveLids();
}

search::SearchableStats
SearchableDocSubDB::getSearchableStats() const
{
    return _indexMgr ? _indexMgr->getSearchableStats() : search::SearchableStats();
}

IDocumentRetriever::UP
SearchableDocSubDB::getDocumentRetriever()
{
    return IDocumentRetriever::UP(new FastAccessDocumentRetriever(_rFeedView.get(), _rSearchView.get()->getAttributeManager()));
}

MatchingStats
SearchableDocSubDB::getMatcherStats(const vespalib::string &rankProfile) const
{
    return _rSearchView.get()->getMatcherStats(rankProfile);
}

void
SearchableDocSubDB::updateLidReuseDelayer(const LidReuseDelayerConfig &config)
{
    Parent::updateLidReuseDelayer(config);
    /*
     * The lid reuse delayer should not have any pending lids stored at this
     * time, since DocumentDB::applyConfig() calls forceCommit() on the
     * feed view before applying the new config to the sub dbs.
     */
    _lidReuseDelayer->setHasIndexedOrAttributeFields(config.hasIndexedOrAttributeFields());
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
    auto attrMgr = std::dynamic_pointer_cast<AttributeManager>(getAttributeManager());
    assert(attrMgr);
    return std::make_shared<DocumentDBReference>(attrMgr, _dms, _gidToLidChangeHandler);
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

} // namespace proton
