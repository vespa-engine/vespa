// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_collection_initializer.h"

#include "vespa/opentelemetry/opentelemetry_tracer_providers.h"
#include "vespa/opentelemetry/span_attribute_names.h"

#include <opentelemetry/trace/provider.h>

using opentelemetry::trace::Provider;

namespace proton {

DocumentSubDbCollectionInitializer::DocumentSubDbCollectionInitializer(const DocTypeName& doc_type_name_in)
    : _doc_type_name(doc_type_name_in), _subDbInitializers() {
}

void DocumentSubDbCollectionInitializer::run() {
    auto provider = Provider::GetTracerProvider();
    auto tracer = provider->GetTracer("proton");
    auto span =
        tracer->StartSpan("Initialize document subdb collection",
                          {{vespa_opentelemetry::span_attribute_names::document_type, _doc_type_name.getName()}});
    auto scope = tracer->WithActiveSpan(span);
    span->End();
}

} // namespace proton
