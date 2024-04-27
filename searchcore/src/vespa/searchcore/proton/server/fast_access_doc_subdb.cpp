// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_doc_subdb.h"
#include "document_subdb_reconfig.h"
#include "emptysearchview.h"
#include "fast_access_document_retriever.h"
#include "document_subdb_initializer.h"
#include "reconfig_params.h"
#include "i_document_subdb_owner.h"
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_initializer.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/reprocessing/reprocess_documents_task.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.fast_access_doc_subdb");

using search::AttributeGuard;
using search::AttributeVector;
using search::SerialNum;
using search::attribute::ImportedAttributeVector;
using search::index::Schema;
using proton::initializer::InitializerTask;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

struct AttributeGuardComp
{
    vespalib::string name;

    AttributeGuardComp(const vespalib::string &n)
        : name(n)
    { }

    bool operator()(const AttributeGuard &rhs) const {
        return name == rhs->getName();
    };
};

proton::IAttributeManager::SP
extractAttributeManager(const FastAccessFeedView::SP &feedView)
{
    const IAttributeWriter::SP &writer = feedView->getAttributeWriter();
    return writer->getAttributeManager();
}

}

FastAccessDocSubDB::Context::~Context() = default;

InitializerTask::SP
FastAccessDocSubDB::createAttributeManagerInitializer(const DocumentDBConfig &configSnapshot,
                                                      SerialNum configSerialNum,
                                                      InitializerTask::SP documentMetaStoreInitTask,
                                                      DocumentMetaStore::SP documentMetaStore,
                                                      std::shared_ptr<AttributeManager::SP> attrMgrResult) const
{
    AllocStrategy alloc_strategy = configSnapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    IAttributeFactory::SP attrFactory = std::make_shared<AttributeFactory>();
    AttributeManager::SP baseAttrMgr =
            std::make_shared<AttributeManager>(_baseDir + "/attribute",
                                               getSubDbName(),
                                               configSnapshot.getTuneFileDocumentDBSP()->_attr,
                                               _fileHeaderContext,
                                               _attribute_interlock,
                                               _writeService.field_writer(),
                                               _writeService.shared(),
                                               attrFactory,
                                               _hwInfo);
    return std::make_shared<AttributeManagerInitializer>(configSerialNum,
                                                         documentMetaStoreInitTask,
                                                         documentMetaStore,
                                                         *baseAttrMgr,
                                                         (_hasAttributes ? configSnapshot.getAttributesConfig() : AttributesConfig()),
                                                         alloc_strategy,
                                                         _fastAccessAttributesOnly,
                                                         _writeService.master(),
                                                         attrMgrResult);
}

namespace {

vespalib::hash_set<vespalib::string>
get_attribute_names(const proton::IAttributeManager& mgr)
{
    vespalib::hash_set<vespalib::string> both;
    std::vector<AttributeGuard> list;
    mgr.getAttributeListAll(list);
    for (const auto& attr : list) {
        both.insert(attr->getName());
    }
    auto imported = mgr.getImportedAttributes();
    if (imported != nullptr) {
        std::vector<std::shared_ptr<ImportedAttributeVector>> i_list;
        imported->getAll(i_list);
        for (const auto& attr : i_list) {
            both.insert(attr->getName());
        }
    }
    return both;
}

}

void
FastAccessDocSubDB::setupAttributeManager(AttributeManager::SP attrMgrResult)
{
    if (_addMetrics) {
        // register attribute metrics
        auto list = get_attribute_names(*attrMgrResult);
        for (const auto &attr : list) {
            _metricsWireService.addAttribute(_subAttributeMetrics, attr);
        }
    }
    _initAttrMgr = attrMgrResult;
}


void
FastAccessDocSubDB::initFeedView(IAttributeWriter::SP writer, const DocumentDBConfig &configSnapshot)
{
    // Called by executor thread
    auto feedView = std::make_shared<FastAccessFeedView>(
            getStoreOnlyFeedViewContext(configSnapshot),
            getFeedViewPersistentParams(),
            FastAccessFeedView::Context(std::move(writer), _docIdLimit));

    _fastAccessFeedView.set(feedView);
    _iFeedView.set(_fastAccessFeedView.get());
}

AttributeManager::SP
FastAccessDocSubDB::getAndResetInitAttributeManager()
{
    AttributeManager::SP retval = _initAttrMgr;
    _initAttrMgr.reset();
    return retval;
}

IFlushTarget::List
FastAccessDocSubDB::getFlushTargetsInternal()
{
    IFlushTarget::List retval(Parent::getFlushTargetsInternal());
    IFlushTarget::List tmp(getAttributeManager()->getFlushTargets());
    retval.insert(retval.end(), tmp.begin(), tmp.end());
    return retval;
}

void
FastAccessDocSubDB::pruneRemovedFields(SerialNum serialNum)
{
    getAttributeManager()->pruneRemovedFields(serialNum);
}

void
FastAccessDocSubDB::reconfigureAttributeMetrics(const proton::IAttributeManager &newMgr,
                                                const proton::IAttributeManager &oldMgr)
{
    auto old_list = get_attribute_names(oldMgr);
    auto new_list = get_attribute_names(newMgr);

    for (const auto &attrName : new_list) {
        if (old_list.contains(attrName)) {
            continue;
        }
        LOG(debug, "reconfigureAttributeMetrics(): addAttribute='%s'", attrName.c_str());
        _metricsWireService.addAttribute(_subAttributeMetrics, attrName);
    }
    for (const auto &attrName : old_list) {
        if (new_list.contains(attrName)) {
            continue;
        }
        LOG(debug, "reconfigureAttributeMetrics(): removeAttribute='%s'", attrName.c_str());
        _metricsWireService.removeAttribute(_subAttributeMetrics, attrName);
    }
}

IReprocessingTask::UP
FastAccessDocSubDB::createReprocessingTask(IReprocessingInitializer &initializer,
                                           const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo) const
{
    uint32_t docIdLimit = _metaStoreCtx->get().getCommittedDocIdLimit();
    assert(docIdLimit > 0);
    return std::make_unique<ReprocessDocumentsTask>(initializer, getSummaryManager(), docTypeRepo,
                                                    getSubDbName(), docIdLimit);
}

FastAccessDocSubDB::FastAccessDocSubDB(const Config &cfg, const Context &ctx)
    : Parent(cfg._storeOnlyCfg, ctx._storeOnlyCtx),
      _hasAttributes(cfg._hasAttributes),
      _fastAccessAttributesOnly(cfg._fastAccessAttributesOnly),
      _initAttrMgr(),
      _fastAccessFeedView(),
      _configurer(_fastAccessFeedView,
                  getSubDbName()),
      _subAttributeMetrics(ctx._subAttributeMetrics),
      _addMetrics(cfg._addMetrics),
      _metricsWireService(ctx._metricsWireService),
      _attribute_interlock(std::move(ctx._attribute_interlock)),
      _docIdLimit(0)
{
}

FastAccessDocSubDB::~FastAccessDocSubDB() = default;

DocumentSubDbInitializer::UP
FastAccessDocSubDB::createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                                      const IndexConfig &indexCfg) const
{
    auto result = Parent::createInitializer(configSnapshot, configSerialNum, indexCfg);
    auto attrMgrInitTask = createAttributeManagerInitializer(configSnapshot, configSerialNum,
                                                             result->getDocumentMetaStoreInitTask(),
                                                             result->result().documentMetaStore()->documentMetaStore(),
                                                             result->writableResult().writableAttributeManager());
    result->addDependency(attrMgrInitTask);
    return result;
}

void
FastAccessDocSubDB::setup(const DocumentSubDbInitializerResult &initResult)
{
    Parent::setup(initResult);
    setupAttributeManager(initResult.attributeManager());
    _docIdLimit.set(_dms->getCommittedDocIdLimit());
}

void
FastAccessDocSubDB::initViews(const DocumentDBConfig &configSnapshot)
{
    // Called by executor thread
    _iSearchView.set(std::make_shared<EmptySearchView>());
    auto writer = std::make_shared<AttributeWriter>(getAndResetInitAttributeManager());
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(std::move(writer), configSnapshot);
    }
}

std::unique_ptr<DocumentSubDBReconfig>
FastAccessDocSubDB::prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num)
{
    auto alloc_strategy = new_config_snapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    AttributeCollectionSpecFactory attr_spec_factory(alloc_strategy, _fastAccessAttributesOnly);
    auto docid_limit = _dms->getCommittedDocIdLimit();
    return _configurer.prepare_reconfig(new_config_snapshot, attr_spec_factory, reconfig_params, docid_limit, serial_num);
}

IReprocessingTask::List
FastAccessDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &, const DocumentSubDBReconfig& prepared_reconfig)
{
    AllocStrategy alloc_strategy = newConfigSnapshot.get_alloc_config().make_alloc_strategy(_subDbType);
    reconfigure(newConfigSnapshot.getStoreConfig(), alloc_strategy);
    IReprocessingTask::List tasks;
    /*
     * If attribute manager should change then document retriever
     * might have to rewrite a different set of fields.  If document
     * type repo has changed then the new repo is needed to handle
     * documents using new fields, e.g. when moving documents from notready
     * to ready document sub db.
     */
    if (params.shouldAttributeManagerChange() ||
        params.shouldAttributeWriterChange() ||
        newConfigSnapshot.getDocumentTypeRepoSP().get() != oldConfigSnapshot.getDocumentTypeRepoSP().get()) {
        proton::IAttributeManager::SP oldMgr = extractAttributeManager(_fastAccessFeedView.get());
        IReprocessingInitializer::UP initializer =
            _configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, prepared_reconfig, serialNum);
        if (initializer->hasReprocessors()) {
            tasks.push_back(IReprocessingTask::SP(createReprocessingTask(*initializer,
                    newConfigSnapshot.getDocumentTypeRepoSP()).release()));
        }
        if (_addMetrics) {
            proton::IAttributeManager::SP newMgr = extractAttributeManager(_fastAccessFeedView.get());
            reconfigureAttributeMetrics(*newMgr, *oldMgr);
        }
        _iFeedView.set(_fastAccessFeedView.get());
        if (is_node_retired_or_maintenance()) {
            // TODO Should probably ahve a similar OnDone callback to applyConfig too.
            vespalib::Gate gate;
            reconfigureAttributesConsideringNodeState(std::make_shared<vespalib::GateCallback>(gate));
            gate.await();
        }
    }
    return tasks;
}

std::shared_ptr<IAttributeWriter>
FastAccessDocSubDB::get_attribute_writer() const
{
    return _fastAccessFeedView.get()->getAttributeWriter();
}

proton::IAttributeManager::SP
FastAccessDocSubDB::getAttributeManager() const
{
    return extractAttributeManager(_fastAccessFeedView.get());
}

IDocumentRetriever::UP
FastAccessDocSubDB::getDocumentRetriever()
{
    FastAccessFeedView::SP feedView = _fastAccessFeedView.get();
    proton::IAttributeManager::SP attrMgr = extractAttributeManager(feedView);
    return std::make_unique<FastAccessDocumentRetriever>(feedView, attrMgr);
}

void
FastAccessDocSubDB::onReplayDone()
{
    // Called by document db executor thread
    Parent::onReplayDone();
    // Normalize attribute vector sizes
    uint32_t docIdLimit = _metaStoreCtx->get().getCommittedDocIdLimit();
    assert(docIdLimit > 0);
    _docIdLimit.set(docIdLimit);
    IFeedView::SP feedView = _iFeedView.get();
    IAttributeWriter::SP attrWriter = static_cast<FastAccessFeedView &>(*feedView).getAttributeWriter();
    attrWriter->onReplayDone(docIdLimit);
}


void
FastAccessDocSubDB::onReprocessDone(SerialNum serialNum)
{
    IFeedView::SP feedView = _iFeedView.get();
    IAttributeWriter::SP attrWriter = static_cast<FastAccessFeedView &>(*feedView).getAttributeWriter();
    vespalib::Gate gate;
    {
        auto onDone = std::make_shared<vespalib::GateCallback>(gate);
        attrWriter->forceCommit(serialNum, onDone);
        _writeService.summary().execute(vespalib::makeLambdaTask([done = std::move(onDone)]() { (void) done; }));
    }
    gate.await();
    Parent::onReprocessDone(serialNum);
}


SerialNum
FastAccessDocSubDB::getOldestFlushedSerial()
{
    SerialNum lowest(Parent::getOldestFlushedSerial());
    proton::IAttributeManager::SP attrMgr(getAttributeManager());
    lowest = std::min(lowest, attrMgr->getOldestFlushedSerialNumber());
    return lowest;
}

SerialNum
FastAccessDocSubDB::getNewestFlushedSerial()
{
    SerialNum highest(Parent::getNewestFlushedSerial());
    proton::IAttributeManager::SP attrMgr(getAttributeManager());
    highest = std::max(highest, attrMgr->getNewestFlushedSerialNumber());
    return highest;
}

TransientResourceUsage
FastAccessDocSubDB::get_transient_resource_usage() const
{
    auto result = StoreOnlyDocSubDB::get_transient_resource_usage();
    result.merge(getAttributeManager()->get_transient_resource_usage());
    return result;
}

} // namespace proton
