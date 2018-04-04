// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_writer_factory.h"
#include "emptysearchview.h"
#include "fast_access_doc_subdb.h"
#include "fast_access_document_retriever.h"
#include "document_subdb_initializer.h"
#include "reconfig_params.h"
#include "i_document_subdb_owner.h"
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_manager_initializer.h>
#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/sequential_attributes_initializer.h>
#include <vespa/searchcore/proton/metrics/legacy_documentdb_metrics.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/reprocessing/document_reprocessing_handler.h>
#include <vespa/searchcore/proton/reprocessing/reprocess_documents_task.h>
#include <vespa/searchlib/docstore/document_store_visitor_progress.h>
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

constexpr search::SerialNum ATTRIBUTE_INIT_SERIAL = 1;

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

InitializerTask::SP
FastAccessDocSubDB::createAttributeManagerInitializer(const DocumentDBConfig &configSnapshot,
                                                      SerialNum configSerialNum,
                                                      InitializerTask::SP documentMetaStoreInitTask,
                                                      DocumentMetaStore::SP documentMetaStore,
                                                      std::shared_ptr<AttributeManager::SP> attrMgrResult) const
{
    IAttributeFactory::SP attrFactory = std::make_shared<AttributeFactory>();
    AttributeManager::SP baseAttrMgr =
            std::make_shared<AttributeManager>(_baseDir + "/attribute",
                                               getSubDbName(),
                                               configSnapshot.getTuneFileDocumentDBSP()->_attr,
                                               _fileHeaderContext,
                                               _writeService.attributeFieldWriter(),
                                               attrFactory,
                                               _hwInfo);
    return std::make_shared<AttributeManagerInitializer>(configSerialNum,
                                                         documentMetaStoreInitTask,
                                                         documentMetaStore,
                                                         baseAttrMgr,
                                                         (_hasAttributes ? configSnapshot.getAttributesConfig() : AttributesConfig()),
                                                         _attributeGrow,
                                                         _attributeGrowNumDocs,
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
            _metricsWireService.addAttribute(_subAttributeMetrics, _totalAttributeMetrics,
                    v.getName());
        }
    }
    _initAttrMgr = attrMgrResult;
}


AttributeCollectionSpec::UP
FastAccessDocSubDB::createAttributeSpec(const AttributesConfig &attrCfg,
                                        SerialNum serialNum) const
{
    uint32_t docIdLimit(_dms->getCommittedDocIdLimit());
    AttributeCollectionSpecFactory factory(_attributeGrow,
            _attributeGrowNumDocs, _fastAccessAttributesOnly);
    return factory.create(attrCfg, docIdLimit, serialNum);
}

void
FastAccessDocSubDB::initFeedView(const IAttributeWriter::SP &writer,
                                 const DocumentDBConfig &configSnapshot)
{
    // Called by executor thread
    FastAccessFeedView::UP feedView(new FastAccessFeedView(
            getStoreOnlyFeedViewContext(configSnapshot),
            getFeedViewPersistentParams(),
            FastAccessFeedView::Context(writer, _docIdLimit)));

    _fastAccessFeedView.set(FastAccessFeedView::SP(feedView.release()));
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
        _metricsWireService.addAttribute(_subAttributeMetrics, _totalAttributeMetrics, attrName);
    }
    for (const auto &attrName : toRemove) {
        LOG(debug, "reconfigureAttributeMetrics(): removeAttribute='%s'", attrName.c_str());
        _metricsWireService.removeAttribute(_subAttributeMetrics, _totalAttributeMetrics, attrName);
    }
}

IReprocessingTask::UP
FastAccessDocSubDB::createReprocessingTask(IReprocessingInitializer &initializer,
                                           const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo) const
{
    uint32_t docIdLimit = _metaStoreCtx->get().getCommittedDocIdLimit();
    assert(docIdLimit > 0);
    return IReprocessingTask::UP(new ReprocessDocumentsTask(initializer,
            getSummaryManager(),
            docTypeRepo,
            getSubDbName(),
            docIdLimit));
}

FastAccessDocSubDB::FastAccessDocSubDB(const Config &cfg, const Context &ctx)
    : Parent(cfg._storeOnlyCfg, ctx._storeOnlyCtx),
      _hasAttributes(cfg._hasAttributes),
      _fastAccessAttributesOnly(cfg._fastAccessAttributesOnly),
      _initAttrMgr(),
      _fastAccessFeedView(),
      _subAttributeMetrics(ctx._subAttributeMetrics),
      _totalAttributeMetrics(ctx._totalAttributeMetrics),
      _addMetrics(cfg._addMetrics),
      _metricsWireService(ctx._metricsWireService),
      _docIdLimit(0)
{ }

FastAccessDocSubDB::~FastAccessDocSubDB() { }

DocumentSubDbInitializer::UP
FastAccessDocSubDB::createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                                      const vespa::config::search::core::ProtonConfig::Index &indexCfg) const
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
    _iSearchView.set(ISearchHandler::SP(new EmptySearchView));
    IAttributeWriter::SP writer(new AttributeWriter(getAndResetInitAttributeManager()));
    {
        std::lock_guard<std::mutex> guard(_configMutex);
        initFeedView(writer, configSnapshot);
    }
}

IReprocessingTask::List
FastAccessDocSubDB::applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver)
{
    (void) resolver;

    reconfigure(newConfigSnapshot.getStoreConfig());
    IReprocessingTask::List tasks;
    updateLidReuseDelayer(&newConfigSnapshot);
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
        AttributeCollectionSpec::UP attrSpec =
            createAttributeSpec(newConfigSnapshot.getAttributesConfig(), serialNum);
        IReprocessingInitializer::UP initializer =
                configurer.reconfigure(newConfigSnapshot, oldConfigSnapshot, *attrSpec);
        if (initializer->hasReprocessors()) {
            tasks.push_back(IReprocessingTask::SP(createReprocessingTask(*initializer,
                    newConfigSnapshot.getDocumentTypeRepoSP()).release()));
        }
        if (_addMetrics) {
            proton::IAttributeManager::SP newMgr = extractAttributeManager(_fastAccessFeedView.get());
            reconfigureAttributeMetrics(*newMgr, *oldMgr);
        }
        _iFeedView.set(_fastAccessFeedView.get());
        _owner.syncFeedView();
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
    return IDocumentRetriever::UP(new FastAccessDocumentRetriever(feedView, attrMgr));
}

void
FastAccessDocSubDB::onReplayDone()
{
    // Called by document db executor thread
    Parent::onReplayDone();
    // Normalize attribute vector sizes
    uint32_t docIdLimit = _metaStoreCtx->get().getCommittedDocIdLimit();
    assert(docIdLimit > 0);
    IFeedView::SP feedView = _iFeedView.get();
    IAttributeWriter::SP attrWriter =
        static_cast<FastAccessFeedView &>(*feedView).getAttributeWriter();
    attrWriter->onReplayDone(docIdLimit);
}


void
FastAccessDocSubDB::onReprocessDone(SerialNum serialNum)
{
    IFeedView::SP feedView = _iFeedView.get();
    IAttributeWriter::SP attrWriter =
        static_cast<FastAccessFeedView &>(*feedView).getAttributeWriter();
    attrWriter->forceCommit(serialNum, std::shared_ptr<search::IDestructorCallback>());
    _writeService.attributeFieldWriter().sync();
    _writeService.summary().sync();
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
