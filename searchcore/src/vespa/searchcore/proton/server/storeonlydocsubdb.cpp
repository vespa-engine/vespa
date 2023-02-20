// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storeonlydocsubdb.h"
#include "docstorevalidator.h"
#include "document_subdb_initializer.h"
#include "document_subdb_initializer_result.h"
#include "document_subdb_reconfig.h"
#include "emptysearchview.h"
#include "i_document_subdb_owner.h"
#include "minimal_document_retriever.h"
#include "reconfig_params.h"
#include "ibucketstatecalculator.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/bucketdb/ibucketdbhandlerinitializer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/docsummary/summaryflushtarget.h>
#include <vespa/searchcore/proton/docsummary/summarymanagerinitializer.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreinitializer.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/searchcore/proton/flushengine/threadedflushtarget.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/reference/dummy_gid_to_lid_change_handler.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/docstore/document_store_visitor_progress.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.storeonlydocsubdb");

using search::GrowStrategy;
using vespalib::makeLambdaTask;
using search::index::Schema;
using search::SerialNum;
using vespalib::IllegalStateException;
using vespalib::ThreadStackExecutorBase;
using proton::matching::MatchingStats;
using vespalib::GenericHeader;
using search::common::FileHeaderContext;
using proton::initializer::InitializerTask;
using searchcorespi::IFlushTarget;
using vespalib::datastore::CompactionStrategy;

namespace proton {

namespace {

searchcorespi::IIndexManager::SP nullIndexManager;
IIndexWriter::SP nullIndexWriter;

}

StoreOnlyDocSubDB::Config::Config(const DocTypeName &docTypeName, const vespalib::string &subName,
                                  const vespalib::string &baseDir,
                                  uint32_t subDbId, SubDbType subDbType)
    : _docTypeName(docTypeName),
      _subName(subName),
      _baseDir(baseDir + "/" + subName),
      _subDbId(subDbId),
      _subDbType(subDbType)
{ }
StoreOnlyDocSubDB::Config::~Config() = default;

StoreOnlyDocSubDB::Context::Context(IDocumentSubDBOwner &owner,
                                    search::transactionlog::SyncProxy &tlSyncer,
                                    const IGetSerialNum &getSerialNum,
                                    const FileHeaderContext &fileHeaderContext,
                                    searchcorespi::index::IThreadingService &writeService,
                                    BucketDBOwnerSP bucketDB,
                                    bucketdb::IBucketDBHandlerInitializer & bucketDBHandlerInitializer,
                                    DocumentDBTaggedMetrics &metrics,
                                    std::mutex &configMutex,
                                    const HwInfo &hwInfo)
    : _owner(owner),
      _tlSyncer(tlSyncer),
      _getSerialNum(getSerialNum),
      _fileHeaderContext(fileHeaderContext),
      _writeService(writeService),
      _bucketDB(std::move(bucketDB)),
      _bucketDBHandlerInitializer(bucketDBHandlerInitializer),
      _metrics(metrics),
      _configMutex(configMutex),
      _hwInfo(hwInfo)
{ }
StoreOnlyDocSubDB::Context::~Context() = default;

StoreOnlyDocSubDB::StoreOnlyDocSubDB(const Config &cfg, const Context &ctx)
    : DocSubDB(ctx._owner, ctx._tlSyncer),
      _docTypeName(cfg._docTypeName),
      _subName(cfg._subName),
      _baseDir(cfg._baseDir),
      _bucketDB(ctx._bucketDB),
      _bucketDBHandlerInitializer(ctx._bucketDBHandlerInitializer),
      _metaStoreCtx(),
      _flushedDocumentMetaStoreSerialNum(0u),
      _flushedDocumentStoreSerialNum(0u),
      _dms(),
      _iSummaryMgr(),
      _rSummaryMgr(),
      _summaryAdapter(),
      _writeService(ctx._writeService),
      _metrics(ctx._metrics),
      _iSearchView(),
      _iFeedView(),
      _configMutex(ctx._configMutex),
      _hwInfo(ctx._hwInfo),
      _getSerialNum(ctx._getSerialNum),
      _tlsSyncer(ctx._writeService.master(), ctx._getSerialNum, ctx._tlSyncer),
      _dmsFlushTarget(),
      _dmsShrinkTarget(),
      _pendingLidsForCommit(std::make_shared<PendingLidTracker>()),
      _nodeRetired(false),
      _lastConfiguredCompactionStrategy(),
      _subDbId(cfg._subDbId),
      _subDbType(cfg._subDbType),
      _fileHeaderContext(ctx._fileHeaderContext, _docTypeName, _baseDir),
      _gidToLidChangeHandler(std::make_shared<DummyGidToLidChangeHandler>())
{
    std::filesystem::create_directory(std::filesystem::path(_baseDir)); // Assume parent is created.
    vespalib::File::sync(vespalib::dirname(_baseDir));
}

StoreOnlyDocSubDB::~StoreOnlyDocSubDB()
{
    // XXX: Disk index wrappers should not live longer than index manager
    // which owns map of active disk indexes.
    clearViews();
    // Metastore must live longer than summarystore.
    _iSummaryMgr.reset();
    _rSummaryMgr.reset();
}

void
StoreOnlyDocSubDB::clearViews() {
    _iFeedView.clear();
    _iSearchView.clear();
}

size_t
StoreOnlyDocSubDB::getNumDocs() const
{
    if (_metaStoreCtx) {
        auto guard = _metaStoreCtx->getReadGuard();
        return guard->get().getNumUsedLids();
    }
    return 0u;
}

size_t
StoreOnlyDocSubDB::getNumActiveDocs() const
{
    return 0;
}

bool
StoreOnlyDocSubDB::hasDocument(const document::DocumentId &id)
{
    search::DocumentIdT lid;
    IDocumentMetaStoreContext::IReadGuard::UP guard = _metaStoreCtx->getReadGuard();
    return guard->get().getLid(id.getGlobalId(), lid);
}

namespace {

void docStoreReplayDone(search::IDocumentStore &docStore, uint32_t docIdLimit)
{
    if (docIdLimit < docStore.getDocIdLimit()) {
        docStore.compactLidSpace(docIdLimit);
        docStore.shrinkLidSpace();
    }
}

}

void
StoreOnlyDocSubDB::onReplayDone()
{
    _dms->constructFreeList();
    auto stats = _dms->getLidUsageStats();
    uint32_t docIdLimit = stats.getHighestUsedLid() + 1;
    assert(docIdLimit <= _dms->getCommittedDocIdLimit());
    _dms->compactLidSpace(docIdLimit);
    _dms->unblockShrinkLidSpace();
    _dms->shrinkLidSpace();
    auto &docStore = _rSummaryMgr->getBackingStore();
    std::promise<void> promise;
    auto future = promise.get_future();
    _writeService.summary().execute(makeLambdaTask([&]() { docStoreReplayDone(docStore, docIdLimit); promise.set_value(); }));
    future.wait();
}


void
StoreOnlyDocSubDB::onReprocessDone(SerialNum)
{
}


SerialNum
StoreOnlyDocSubDB::getOldestFlushedSerial()
{
    SerialNum lowest(_iSummaryMgr->getBackingStore().lastSyncToken());
    lowest = std::min(lowest, _dmsFlushTarget->getFlushedSerialNum());
    lowest = std::min(lowest, _dmsShrinkTarget->getFlushedSerialNum());
    return lowest;
}


SerialNum
StoreOnlyDocSubDB::getNewestFlushedSerial()
{
    SerialNum highest(_iSummaryMgr->getBackingStore().lastSyncToken());
    highest = std::max(highest, _dmsFlushTarget->getFlushedSerialNum());
    highest = std::max(highest, _dmsShrinkTarget->getFlushedSerialNum());
    return highest;
}


initializer::InitializerTask::SP
StoreOnlyDocSubDB::
createSummaryManagerInitializer(const search::LogDocumentStore::Config & storeCfg,
                                const AllocStrategy& alloc_strategy,
                                const search::TuneFileSummary &tuneFile,
                                search::IBucketizer::SP bucketizer,
                                std::shared_ptr<SummaryManager::SP> result) const
{
    GrowStrategy grow = alloc_strategy.get_grow_strategy();
    vespalib::string baseDir(_baseDir + "/summary");
    return std::make_shared<SummaryManagerInitializer>
        (grow, baseDir, getSubDbName(), _writeService.shared(),
         storeCfg, tuneFile, _fileHeaderContext, _tlSyncer, std::move(bucketizer), std::move(result));
}

void
StoreOnlyDocSubDB::setupSummaryManager(SummaryManager::SP summaryManager)
{
    _rSummaryMgr = std::move(summaryManager);
    _iSummaryMgr = _rSummaryMgr; // Upcast allowed with std::shared_ptr
    _flushedDocumentStoreSerialNum = _iSummaryMgr->getBackingStore().lastSyncToken();
    _summaryAdapter = std::make_shared<SummaryAdapter>(_rSummaryMgr);
}


InitializerTask::SP
StoreOnlyDocSubDB::
createDocumentMetaStoreInitializer(const AllocStrategy& alloc_strategy,
                                   const search::TuneFileAttributes &tuneFile,
                                   std::shared_ptr<DocumentMetaStoreInitializerResult::SP> result) const
{
    GrowStrategy grow = alloc_strategy.get_grow_strategy();
    // Amortize memory spike cost over N docs
    grow.setGrowDelta(grow.getGrowDelta() + alloc_strategy.get_amortize_count());
    vespalib::string baseDir(_baseDir + "/documentmetastore");
    vespalib::string name = DocumentMetaStore::getFixedName();
    vespalib::string attrFileName = baseDir + "/" + name; // XXX: Wrong
    // make preliminary result visible early, allowing dependent
    // initializers to get hold of document meta store instance in
    // their constructors.
    *result = std::make_shared<DocumentMetaStoreInitializerResult>
              (std::make_shared<DocumentMetaStore>(_bucketDB, attrFileName, grow, _subDbType), tuneFile);
    return std::make_shared<documentmetastore::DocumentMetaStoreInitializer>
        (baseDir, getSubDbName(), _docTypeName.toString(), (*result)->documentMetaStore());
}


void
StoreOnlyDocSubDB::setupDocumentMetaStore(DocumentMetaStoreInitializerResult::SP dmsResult)
{
    vespalib::string baseDir(_baseDir + "/documentmetastore");
    vespalib::string name = DocumentMetaStore::getFixedName();
    DocumentMetaStore::SP dms(dmsResult->documentMetaStore());
    if (dms->isLoaded()) {
        _flushedDocumentMetaStoreSerialNum = dms->getStatus().getLastSyncToken();
    }
    _bucketDBHandlerInitializer.addDocumentMetaStore(dms.get(), _flushedDocumentMetaStoreSerialNum);
    _metaStoreCtx = std::make_shared<DocumentMetaStoreContext>(dms);
    LOG(debug, "Added document meta store '%s' with flushed serial num %" PRIu64,
               name.c_str(), _flushedDocumentMetaStoreSerialNum);
    _dms = dms;
    _dmsFlushTarget = std::make_shared<DocumentMetaStoreFlushTarget>(dms, _tlsSyncer, baseDir, dmsResult->tuneFile(),
                                                                     _fileHeaderContext, _hwInfo);
    using Type = IFlushTarget::Type;
    using Component = IFlushTarget::Component;
    _dmsShrinkTarget = std::make_shared<ShrinkLidSpaceFlushTarget>("documentmetastore.shrink", Type::GC,
                                                                   Component::ATTRIBUTE, _flushedDocumentMetaStoreSerialNum,
                                                                   _dmsFlushTarget->getLastFlushTime(), dms);
    _lastConfiguredCompactionStrategy = dms->getConfig().getCompactionStrategy();
}

DocumentSubDbInitializer::UP
StoreOnlyDocSubDB::createInitializer(const DocumentDBConfig &configSnapshot, SerialNum , const IndexConfig &) const
{
    auto result = std::make_unique<DocumentSubDbInitializer>(const_cast<StoreOnlyDocSubDB &>(*this),
                                                             _writeService.master());
    AllocStrategy alloc_strategy = configSnapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    auto dmsInitTask = createDocumentMetaStoreInitializer(alloc_strategy,
                                                          configSnapshot.getTuneFileDocumentDBSP()->_attr,
                                                          result->writableResult().writableDocumentMetaStore());
    result->addDocumentMetaStoreInitTask(dmsInitTask);
    auto summaryTask = createSummaryManagerInitializer(configSnapshot.getStoreConfig(),
                                                       alloc_strategy,
                                                       configSnapshot.getTuneFileDocumentDBSP()->_summary,
                                                       result->result().documentMetaStore()->documentMetaStore(),
                                                       result->writableResult().writableSummaryManager());
    result->addDependency(summaryTask);
    summaryTask->addDependency(dmsInitTask);

    result->writableResult().setFlushConfig(configSnapshot.getMaintenanceConfigSP()->getFlushConfig());
    return result;
}

void
StoreOnlyDocSubDB::setup(const DocumentSubDbInitializerResult &initResult)
{
    setupDocumentMetaStore(initResult.documentMetaStore());
    setupSummaryManager(initResult.summaryManager());
}

IFlushTarget::List
StoreOnlyDocSubDB::getFlushTargets()
{
    IFlushTarget::List ret;
    for (const auto &target : getFlushTargetsInternal()) {
        ret.push_back(std::make_shared<ThreadedFlushTarget>(_writeService.master(), _getSerialNum, target, _subName));
    }
    return ret;
}

IFlushTarget::List
StoreOnlyDocSubDB::getFlushTargetsInternal()
{
    IFlushTarget::List ret(_rSummaryMgr->getFlushTargets(_writeService.summary()));
    ret.push_back(_dmsFlushTarget);
    ret.push_back(_dmsShrinkTarget);
    return ret;
}

StoreOnlyFeedView::Context
StoreOnlyDocSubDB::getStoreOnlyFeedViewContext(const DocumentDBConfig &configSnapshot)
{
    return { getSummaryAdapter(), configSnapshot.getSchemaSP(), _metaStoreCtx, configSnapshot.getDocumentTypeRepoSP(),
             _pendingLidsForCommit, *_gidToLidChangeHandler, _writeService};
}

StoreOnlyFeedView::PersistentParams
StoreOnlyDocSubDB::getFeedViewPersistentParams()
{
    SerialNum flushedDMSSN(_flushedDocumentMetaStoreSerialNum);
    SerialNum flushedDSSN(_flushedDocumentStoreSerialNum);
    return { flushedDMSSN, flushedDSSN, _docTypeName, _subDbId, _subDbType };
}

void
StoreOnlyDocSubDB::initViews(const DocumentDBConfig &configSnapshot)
{
    assert(_writeService.master().isCurrentThread());
    _iSearchView.set(std::make_shared<EmptySearchView>());
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(configSnapshot);
    }
}

void
StoreOnlyDocSubDB::validateDocStore(FeedHandler & feedHandler, SerialNum serialNum) const
{
    LOG(info, "Validating document store for sub db %u doctype %s", _subDbId, _docTypeName.toString().c_str());

    search::IDocumentStore &docStore = _iSummaryMgr->getBackingStore();
    DocStoreValidator validator(_metaStoreCtx->get());
    search::DocumentStoreVisitorProgress validatorProgress;

    docStore.accept(validator, validatorProgress, *_iFeedView.get()->getDocumentTypeRepo());

    validator.visitDone();

    LOG(info, "Validated document store for sub db %u, doctype %s, %u orphans, %u invalid, %u visits, %u empty visits",
        _subDbId, _docTypeName.toString().c_str(), validator.getOrphanCount(),
        validator.getInvalidCount(), validator.getVisitCount(), validator.getVisitEmptyCount());

    validator.killOrphans(docStore, serialNum);
    if (validator.getInvalidCount() != 0u) {
        validator.performRemoves(feedHandler, docStore, *_iFeedView.get()->getDocumentTypeRepo());
    }
}


void
StoreOnlyDocSubDB::initFeedView(const DocumentDBConfig &configSnapshot)
{
    assert(_writeService.master().isCurrentThread());
    auto feedView = std::make_shared<StoreOnlyFeedView>(getStoreOnlyFeedViewContext(configSnapshot),
                                                        getFeedViewPersistentParams());

    // XXX: Not exception safe.
    _iFeedView.set(std::move(feedView));
}

vespalib::string
StoreOnlyDocSubDB::getSubDbName() const {
    return vespalib::make_string("%s.%s", _owner.getName().c_str(), _subName.c_str());
}

std::unique_ptr<DocumentSubDBReconfig>
StoreOnlyDocSubDB::prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num)
{
    (void) new_config_snapshot;
    (void) reconfig_params;
    (void) serial_num;
    return std::make_unique<DocumentSubDBReconfig>(std::shared_ptr<Matchers>(), std::shared_ptr<IAttributeManager>());
}

void
StoreOnlyDocSubDB::complete_prepare_reconfig(DocumentSubDBReconfig& prepared_reconfig, SerialNum serial_num)
{
    prepared_reconfig.complete(_dms->getCommittedDocIdLimit(), serial_num);
}

IReprocessingTask::List
StoreOnlyDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                               SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentSubDBReconfig& prepared_reconfig)
{
    (void) oldConfigSnapshot;
    (void) serialNum;
    (void) params;
    (void) resolver;
    (void) prepared_reconfig;
    assert(_writeService.master().isCurrentThread());
    AllocStrategy alloc_strategy = newConfigSnapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    reconfigure(newConfigSnapshot.getStoreConfig(), alloc_strategy);
    initFeedView(newConfigSnapshot);
    return {};
}

namespace {

constexpr double RETIRED_DEAD_RATIO = 0.5;

struct UpdateConfig : public search::attribute::IAttributeFunctor {
    explicit UpdateConfig(CompactionStrategy compactionStrategy) noexcept
        : _compactionStrategy(compactionStrategy)
    {}
    void operator()(search::attribute::IAttributeVector &iAttributeVector) override {
        auto attributeVector = dynamic_cast<search::AttributeVector *>(&iAttributeVector);
        if (attributeVector != nullptr) {
            auto cfg = attributeVector->getConfig();
            cfg.setCompactionStrategy(_compactionStrategy);
            attributeVector->update_config(cfg);
        }
    }
    CompactionStrategy _compactionStrategy;
};

}

CompactionStrategy
StoreOnlyDocSubDB::computeCompactionStrategy(CompactionStrategy strategy) const {
    return isNodeRetired()
           ? CompactionStrategy(RETIRED_DEAD_RATIO, RETIRED_DEAD_RATIO)
           : strategy;
}

void
StoreOnlyDocSubDB::reconfigure(const search::LogDocumentStore::Config & config, const AllocStrategy& alloc_strategy)
{
    _lastConfiguredCompactionStrategy = alloc_strategy.get_compaction_strategy();
    auto cfg = _dms->getConfig();
    GrowStrategy grow = alloc_strategy.get_grow_strategy();
    // Amortize memory spike cost over N docs
    grow.setGrowDelta(grow.getGrowDelta() + alloc_strategy.get_amortize_count());
    cfg.setGrowStrategy(grow);
    cfg.setCompactionStrategy(computeCompactionStrategy(alloc_strategy.get_compaction_strategy()));
    _dms->update_config(cfg); // Update grow and compaction config
    _rSummaryMgr->reconfigure(config);
}

void
StoreOnlyDocSubDB::setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> & calc, OnDone onDone) {
    bool wasNodeRetired = isNodeRetired();
    _nodeRetired = calc->nodeRetired();
    if (wasNodeRetired != isNodeRetired()) {
        CompactionStrategy compactionStrategy = computeCompactionStrategy(_lastConfiguredCompactionStrategy);
        auto cfg = _dms->getConfig();
        cfg.setCompactionStrategy(compactionStrategy);
        _dms->update_config(cfg);
        reconfigureAttributesConsideringNodeState(std::move(onDone));
    }
}

void
StoreOnlyDocSubDB::reconfigureAttributesConsideringNodeState(OnDone onDone) {
    CompactionStrategy compactionStrategy = computeCompactionStrategy(_lastConfiguredCompactionStrategy);
    auto attrMan = getAttributeManager();
    if (attrMan) {
        attrMan->asyncForEachAttribute(std::make_shared<UpdateConfig>(compactionStrategy), std::move(onDone));
    }
}

proton::IAttributeManager::SP
StoreOnlyDocSubDB::getAttributeManager() const
{
    return {};
}

const searchcorespi::IIndexManager::SP &
StoreOnlyDocSubDB::getIndexManager() const
{
    return nullIndexManager;
}

const IIndexWriter::SP &
StoreOnlyDocSubDB::getIndexWriter() const
{
    return nullIndexWriter;
}

void
StoreOnlyDocSubDB::pruneRemovedFields(SerialNum)
{
}

void
StoreOnlyDocSubDB::setIndexSchema(const Schema::SP &, SerialNum )
{
    assert(_writeService.master().isCurrentThread());
}

search::SearchableStats
StoreOnlyDocSubDB::getSearchableStats() const
{
    return {};
}

IDocumentRetriever::UP
StoreOnlyDocSubDB::getDocumentRetriever()
{
    return std::make_unique<MinimalDocumentRetriever>(_docTypeName, _iFeedView.get()->getDocumentTypeRepo(),
                                                      *_metaStoreCtx, _iSummaryMgr->getBackingStore(),
                                                      _subDbType != SubDbType::REMOVED);
}

MatchingStats
StoreOnlyDocSubDB::getMatcherStats(const vespalib::string &rankProfile) const
{
    (void) rankProfile;
    return {};
}

void
StoreOnlyDocSubDB::close()
{
    assert(_writeService.master().isCurrentThread());
    search::IDocumentStore & store(_rSummaryMgr->getBackingStore());
    auto summaryFlush = std::make_shared<SummaryFlushTarget>(store, _writeService.summary());
    auto summaryFlushTask = summaryFlush->initFlush(store.tentativeLastSyncToken(), std::make_shared<search::FlushToken>());
    if (summaryFlushTask) {
        SerialNum syncToken = summaryFlushTask->getFlushSerial();
        _tlSyncer.sync(syncToken);
        summaryFlushTask->run();
    }
}

std::shared_ptr<IDocumentDBReference>
StoreOnlyDocSubDB::getDocumentDBReference()
{
    return {};
}

StoreOnlySubDBFileHeaderContext::
StoreOnlySubDBFileHeaderContext(const FileHeaderContext & parentFileHeaderContext,
                                const DocTypeName &docTypeName,
                                const vespalib::string &baseDir)
    : FileHeaderContext(),
      _parentFileHeaderContext(parentFileHeaderContext),
      _docTypeName(docTypeName),
      _subDB()
{
    size_t pos = baseDir.rfind('/');
    _subDB = (pos != vespalib::string::npos) ? baseDir.substr(pos + 1) : baseDir;
}
StoreOnlySubDBFileHeaderContext::~StoreOnlySubDBFileHeaderContext() = default;

void
StoreOnlyDocSubDB::tearDownReferences(IDocumentDBReferenceResolver &)
{
}

void
StoreOnlySubDBFileHeaderContext::
addTags(vespalib::GenericHeader &header, const vespalib::string &name) const
{
    _parentFileHeaderContext.addTags(header, name);
    using Tag = GenericHeader::Tag;
    header.putTag(Tag("documentType", _docTypeName.toString()));
    header.putTag(Tag("subDB", _subDB));
}

TransientResourceUsage
StoreOnlyDocSubDB::get_transient_resource_usage() const
{
    return _dmsFlushTarget->get_transient_resource_usage();
}

} // namespace proton
