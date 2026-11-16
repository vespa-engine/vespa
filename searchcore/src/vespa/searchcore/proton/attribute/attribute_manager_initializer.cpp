// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_manager_initializer.h"

#include "attribute_collection_spec_factory.h"
#include "attribute_directory.h"
#include "attributes_initializer_base.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreattribute.h>
#include <vespa/searchcore/proton/initializer/load_memory_usage.h>
#include <vespa/searchcorespi/index/i_thread_service.h>

#include <opentelemetry/trace/provider.h>

#include <future>

using opentelemetry::trace::Provider;
using opentelemetry::trace::Span;
using proton::initializer::LoadMemoryUsage;
using search::AttributeVector;
using search::GrowStrategy;
using search::SerialNum;
using vespa::config::search::AttributesConfig;
using vespalib::datastore::CompactionStrategy;

namespace proton {

using initializer::InitializerTask;
using initializer::LoadMemoryUsage;

namespace {

class AttributeInitializerTask : public InitializerTask {
private:
    AttributeInitializer::UP                    _initializer;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    InitializedAttributesResult&                _result;
    LoadMemoryUsage                             _load_memory_usage;

public:
    AttributeInitializerTask(AttributeInitializer::UP                    initializer,
                             std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                             InitializedAttributesResult&                result) noexcept;
    ~AttributeInitializerTask() override;
    void run() override;
    [[nodiscard]] LoadMemoryUsage get_load_memory_usage() const noexcept override;
    void accept_visitor(initializer::InitializerTaskVisitor& visitor) override;
};

AttributeInitializerTask::AttributeInitializerTask(AttributeInitializer::UP                    initializer,
                                                   std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                                   InitializedAttributesResult&                result) noexcept
    : _initializer(std::move(initializer)),
      _documentMetaStore(std::move(documentMetaStore)),
      _result(result),
      _load_memory_usage(_initializer->get_load_memory_usage()) {
}

AttributeInitializerTask::~AttributeInitializerTask() = default;

void AttributeInitializerTask::run() {
    auto provider = Provider::GetTracerProvider();
    auto tracer = provider->GetTracer("proton");
    auto span = tracer->StartSpan(
        "Load attribute",
        {{vespa_opentelemetry::span_attribute_names::document_type, _initializer->doc_type_name().getName()},
         {vespa_opentelemetry::span_attribute_names::subdb, _initializer->subdb_name()},
         {vespa_opentelemetry::span_attribute_names::attribute, _initializer->attr_dir()->getAttrName()}});
    auto                       scope = tracer->WithActiveSpan(span);
    AttributeInitializerResult result = _initializer->init();
    if (result) {
        AttributesInitializerBase::considerPadAttribute(*result.getAttribute(), _initializer->getCurrentSerialNum(),
                                                        _documentMetaStore->getCommittedDocIdLimit());
        _result.add(result);
    }
    span->End();
}

LoadMemoryUsage AttributeInitializerTask::get_load_memory_usage() const noexcept {
    return _load_memory_usage;
}

void AttributeInitializerTask::accept_visitor(initializer::InitializerTaskVisitor& visitor) {
    visitor.visit_attribute_initializer(*_initializer);
    InitializerTask::accept_visitor(visitor);
}

class AttributeManagerInitializerTask : public vespalib::Executor::Task {
    std::shared_ptr<Span>                       _span;
    std::promise<void>                          _promise;
    search::SerialNum                           _configSerialNum;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    AttributeManager::SP                        _attrMgr;
    InitializedAttributesResult&                _attributesResult;

public:
    AttributeManagerInitializerTask(std::shared_ptr<Span> span, std::promise<void>&& promise,
                                    search::SerialNum                           configSerialNum,
                                    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                    AttributeManager::SP attrMgr, InitializedAttributesResult& attributesResult);
    ~AttributeManagerInitializerTask() override;
    void run() override;
};

AttributeManagerInitializerTask::AttributeManagerInitializerTask(
    std::shared_ptr<Span> span, std::promise<void>&& promise, search::SerialNum configSerialNum,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, AttributeManager::SP attrMgr,
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
    auto provider = Provider::GetTracerProvider();
    auto tracer = provider->GetTracer("proton");
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
    InitializerTask::SP                         _documentMetaStoreInitTask;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    InitializedAttributesResult&                _attributesResult;

public:
    AttributeInitializerTasksBuilder(InitializerTask& attrMgrInitTask, InitializerTask::SP documentMetaStoreInitTask,
                                     std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                     InitializedAttributesResult&                attributesResult);
    ~AttributeInitializerTasksBuilder() override;
    void add(AttributeInitializer::UP initializer) override;
};

AttributeInitializerTasksBuilder::AttributeInitializerTasksBuilder(
    InitializerTask& attrMgrInitTask, InitializerTask::SP documentMetaStoreInitTask,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, InitializedAttributesResult& attributesResult)
    : _attrMgrInitTask(attrMgrInitTask),
      _documentMetaStoreInitTask(std::move(documentMetaStoreInitTask)),
      _documentMetaStore(std::move(documentMetaStore)),
      _attributesResult(attributesResult) {
}

AttributeInitializerTasksBuilder::~AttributeInitializerTasksBuilder() = default;

void AttributeInitializerTasksBuilder::add(AttributeInitializer::UP initializer) {
    InitializerTask::SP attributeInitTask =
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
    SerialNum configSerialNum, initializer::InitializerTask::SP documentMetaStoreInitTask,
    std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore, const AttributeManager& baseAttrMgr,
    const AttributesConfig& attrCfg, const AllocStrategy& alloc_strategy, bool fastAccessAttributesOnly,
    searchcorespi::index::IThreadService& master, std::shared_ptr<AttributeManager::SP> attrMgrResult)
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
    auto provider = Provider::GetTracerProvider();
    auto tracer = provider->GetTracer("proton");
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
