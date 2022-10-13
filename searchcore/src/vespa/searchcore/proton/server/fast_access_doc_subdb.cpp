// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fast_access_doc_subdb.h"
#include "attribute_writer_factory.h"
#include "emptysearchview.h"
#include "fast_access_document_retriever.h"
#include "document_subdb_initializer.h"
#include "reconfig_params.h"
#include "i_document_subdb_owner.h"
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_initializer.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/sequential_attributes_initializer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/reprocessing/reprocess_documents_task.h>
#include <vespa/vespalib/util/destructor_callbacks.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.fast_access_doc_subdb");

using proton::matching::SessionManager;
using search::AttributeGuard;
using search::AttributeVector;
using search::SerialNum;
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
                                               _writeService.attributeFieldWriter(),
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

void
FastAccessDocSubDB::setupAttributeManager(AttributeManager::SP attrMgrResult)
{
    if (_addMetrics) {
        // register attribute metrics
        std::vector<AttributeGuard> list;
        attrMgrResult->getAttributeListAll(list);
        for (const auto &attr : list) {
            const AttributeVector &v = *attr;
            _metricsWireService.addAttribute(_subAttributeMetrics, v.getName());
        }
    }
    _initAttrMgr = attrMgrResult;
}


std::unique_ptr<AttributeCollectionSpec>
FastAccessDocSubDB::createAttributeSpec(const AttributesConfig &attrCfg, const AllocStrategy& alloc_strategy, SerialNum serialNum) const
{
    uint32_t docIdLimit(_dms->getCommittedDocIdLimit());
    AttributeCollectionSpecFactory factory(alloc_strategy, _fastAccessAttributesOnly);
    return factory.create(attrCfg, docIdLimit, serialNum);
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
    std::set<vespalib::string> toAdd;
    std::set<vespalib::string> toRemove;
    std::vector<AttributeGuard> newList;
    std::vector<AttributeGuard> oldList;
    newMgr.getAttributeList(newList);
    oldMgr.getAttributeList(oldList);
    for (const auto &newAttr : newList) {
        if (std::find_if(oldList.begin(),
                         oldList.end(),
                         AttributeGuardComp(newAttr->getName())) ==
            oldList.end()) {
            toAdd.insert(newAttr->getName());
        }
    }
    for (const auto &oldAttr : oldList) {
        if (std::find_if(newList.begin(),
                         newList.end(),
                         AttributeGuardComp(oldAttr->getName())) ==
            newList.end()) {
            toRemove.insert(oldAttr->getName());
        }
    }
    for (const auto &attrName : toAdd) {
        LOG(debug, "reconfigureAttributeMetrics(): addAttribute='%s'", attrName.c_str());
        _metricsWireService.addAttribute(_subAttributeMetrics, attrName);
    }
    for (const auto &attrName : toRemove) {
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
FastAccessDocSubDB::initViews(const DocumentDBConfig &configSnapshot,
                              const SessionManager::SP &sessionManager)
{
    // Called by executor thread
    (void) sessionManager;
    _iSearchView.set(std::make_shared<EmptySearchView>());
    auto writer = std::make_shared<AttributeWriter>(getAndResetInitAttributeManager());
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(std::move(writer), configSnapshot);
    }
}

IReprocessingTask::List
FastAccessDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver)
{
    (void) resolver;

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
        FastAccessDocSubDBConfigurer configurer(_fastAccessFeedView,
                std::make_unique<AttributeWriterFactory>(), getSubDbName());
        proton::IAttributeManager::SP oldMgr = extractAttributeManager(_fastAccessFeedView.get());
        std::unique_ptr<AttributeCollectionSpec> attrSpec =
            createAttributeSpec(newConfigSnapshot.getAttributesConfig(), alloc_strategy, serialNum);
        IReprocessingInitializer::UP initializer =
                configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, std::move(*attrSpec));
        if (initializer->hasReprocessors()) {
            tasks.push_back(IReprocessingTask::SP(createReprocessingTask(*initializer,
                    newConfigSnapshot.getDocumentTypeRepoSP()).release()));
        }
        if (_addMetrics) {
            proton::IAttributeManager::SP newMgr = extractAttributeManager(_fastAccessFeedView.get());
            reconfigureAttributeMetrics(*newMgr, *oldMgr);
        }
        _iFeedView.set(_fastAccessFeedView.get());
        if (isNodeRetired()) {
            // TODO Should probably ahve a similar OnDone callback to applyConfig too.
            vespalib::Gate gate;
            reconfigureAttributesConsideringNodeState(std::make_shared<vespalib::GateCallback>(gate));
            gate.await();
        }
    }
    return tasks;
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

} // namespace proton
