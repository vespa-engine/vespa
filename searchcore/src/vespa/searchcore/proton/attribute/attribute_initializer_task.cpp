// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initializer_task.h"

#include "attribute_directory.h"
#include "attributes_initializer_base.h"
#include "initialized_attributes_result.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastoreattribute.h>
#include <vespa/searchcore/proton/initializer/initializer_task_visitor.h>
#include <vespa/searchcore/proton/initializer/load_memory_usage.h>
#include <vespa/searchcorespi/common/tracing.h>

using proton::initializer::LoadMemoryUsage;
using searchcorespi::common::get_tracer;

namespace proton {

AttributeInitializerTask::AttributeInitializerTask(std::unique_ptr<AttributeInitializer>       initializer,
                                                   std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                                   InitializedAttributesResult&                result) noexcept
    : _initializer(std::move(initializer)),
      _documentMetaStore(std::move(documentMetaStore)),
      _result(result),
      _load_memory_usage(_initializer->get_load_memory_usage()) {
}

AttributeInitializerTask::~AttributeInitializerTask() = default;

void AttributeInitializerTask::run() {
    auto tracer = get_tracer();
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

} // namespace proton
