// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_initializer.h"

#include "attribute_collection_spec_factory.h"
#include "attribute_initializer_task.h"
#include "attributes_initializer_base.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/tracing.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreattribute.h>
#include <vespa/searchcorespi/index/i_thread_service.h>

#include <future>

using opentelemetry::trace::Span;
using search::SerialNum;
using vespa::config::search::AttributesConfig;
using vespalib::datastore::CompactionStrategy;

namespace proton {

using initializer::InitializerTask;

namespace {

class AttributeManagerInitializerTask : public vespalib::Executor::Task {
    std::shared_ptr<Span>                       _span;
    std::promise<void>                          _promise;
    SerialNum                                   _configSerialNum;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    std::shared_ptr<AttributeManager>           _attrMgr;
    InitializedAttributesResult&                _attributesResult;

public:
    AttributeManagerInitializerTask(std::shared_ptr<Span> span, std::promise<void>&& promise,
                                    SerialNum                                   configSerialNum,
                                    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                    std::shared_ptr<AttributeManager>           attrMgr,
                                    InitializedAttributesResult&                attributesResult);
    ~AttributeManagerInitializerTask() override;
    void run() override;
};

AttributeManagerInitializerTask::AttributeManagerInitializerTask(
    std::shared_ptr<Span> span, std::promise<void>&& promise, search::SerialNum configSerialNum,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, std::shared_ptr<AttributeManager> attrMgr,
    InitializedAttributesResult& attributesResult)
    : _span(std::move(span)),
      _promise(std::move(promise)),
      _configSerialNum(configSerialNum),
      _documentMetaStore(std::move(documentMetaStore)),
      _attrMgr(std::move(attrMgr)),
      _attributesResult(attributesResult) {
}

AttributeManagerInitializerTask::~AttributeManagerInitializerTask() = default;

void AttributeManagerInitializerTask::run() {
    auto tracer = get_tracer();
    auto scope = tracer->WithActiveSpan(_span);
    _span->AddEvent("Running attribute manager initializer task in document db main executor");
    _attrMgr->addExtraAttribute(_documentMetaStore);
    _attrMgr->addInitializedAttributes(_attributesResult.get(), 1, _configSerialNum);
    _attrMgr->pruneRemovedFields(_configSerialNum);
    _promise.set_value();
}

class AttributeInitializerTasksBuilder : public IAttributeInitializerRegistry {
private:
    InitializerTask&                            _attrMgrInitTask;
    std::shared_ptr<InitializerTask>            _documentMetaStoreInitTask;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    InitializedAttributesResult&                _attributesResult;

public:
    AttributeInitializerTasksBuilder(InitializerTask&                            attrMgrInitTask,
                                     std::shared_ptr<InitializerTask>            documentMetaStoreInitTask,
                                     std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                     InitializedAttributesResult&                attributesResult);
    ~AttributeInitializerTasksBuilder() override;
    void add(AttributeInitializer::UP initializer) override;
};

AttributeInitializerTasksBuilder::AttributeInitializerTasksBuilder(
    InitializerTask& attrMgrInitTask, std::shared_ptr<InitializerTask> documentMetaStoreInitTask,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, InitializedAttributesResult& attributesResult)
    : _attrMgrInitTask(attrMgrInitTask),
      _documentMetaStoreInitTask(std::move(documentMetaStoreInitTask)),
      _documentMetaStore(std::move(documentMetaStore)),
      _attributesResult(attributesResult) {
}

AttributeInitializerTasksBuilder::~AttributeInitializerTasksBuilder() = default;

void AttributeInitializerTasksBuilder::add(AttributeInitializer::UP initializer) {
    std::shared_ptr<InitializerTask> attributeInitTask =
        std::make_shared<AttributeInitializerTask>(std::move(initializer), _documentMetaStore, _attributesResult);
    attributeInitTask->addDependency(_documentMetaStoreInitTask);
    _attrMgrInitTask.addDependency(attributeInitTask);
}

} // namespace

std::unique_ptr<AttributeCollectionSpec> AttributeManagerInitializer::createAttributeSpec() const {
    uint32_t docIdLimit = 1; // The real docIdLimit is used after attributes are loaded to pad them
    AttributeCollectionSpecFactory factory(_alloc_strategy, _fastAccessAttributesOnly);
    return factory.create(_attrCfg, docIdLimit, _configSerialNum);
}

AttributeManagerInitializer::AttributeManagerInitializer(
    SerialNum configSerialNum, std::shared_ptr<InitializerTask> documentMetaStoreInitTask,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, const AttributeManager& baseAttrMgr,
    const AttributesConfig& attrCfg, const AllocStrategy& alloc_strategy, bool fastAccessAttributesOnly,
    searchcorespi::index::IThreadService& master, std::shared_ptr<std::shared_ptr<AttributeManager>> attrMgrResult)
    : _configSerialNum(configSerialNum),
      _documentMetaStore(documentMetaStore),
      _attrMgr(),
      _attrCfg(attrCfg),
      _alloc_strategy(alloc_strategy),
      _fastAccessAttributesOnly(fastAccessAttributesOnly),
      _master(master),
      _attributesResult(),
      _attrMgrResult(std::move(attrMgrResult)) {
    addDependency(documentMetaStoreInitTask);
    AttributeInitializerTasksBuilder         tasksBuilder(*this, std::move(documentMetaStoreInitTask),
                                                          std::move(documentMetaStore), _attributesResult);
    std::unique_ptr<AttributeCollectionSpec> attrSpec = createAttributeSpec();
    _attrMgr = std::make_shared<AttributeManager>(baseAttrMgr, std::move(*attrSpec), tasksBuilder);
}

AttributeManagerInitializer::~AttributeManagerInitializer() = default;

void AttributeManagerInitializer::run() {
    auto tracer = get_tracer();
    auto span = tracer->StartSpan(
        "Initialize attribute manager",
        {{vespa_opentelemetry::span_attribute_names::document_type, _attrMgr->doc_type_name().getName()},
         {vespa_opentelemetry::span_attribute_names::subdb, _attrMgr->subdb_name()}});
    auto               scope = tracer->WithActiveSpan(span);
    std::promise<void> promise;
    auto               future = promise.get_future();
    /*
     * Attribute manager and some its members (e.g. _attributeFieldWriter) assumes that work is performed
     * by document db master thread and lacks locking to handle calls from multiple threads.
     */
    _master.execute(std::make_unique<AttributeManagerInitializerTask>(
        span, std::move(promise), _configSerialNum, _documentMetaStore, _attrMgr, _attributesResult));
    future.wait();
    *_attrMgrResult = _attrMgr;
    span->End();
}

} // namespace proton
