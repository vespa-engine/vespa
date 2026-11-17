// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_collection_initializer.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/tracing.h>

namespace proton {

DocumentSubDbCollectionInitializer::DocumentSubDbCollectionInitializer(const DocTypeName& doc_type_name_in)
    : _doc_type_name(doc_type_name_in), _subDbInitializers() {
}

void DocumentSubDbCollectionInitializer::run() {
    auto tracer = get_tracer();
    auto span =
        tracer->StartSpan("Initialize document subdb collection",
                          {{vespa_opentelemetry::span_attribute_names::document_type, _doc_type_name.getName()}});
    auto scope = tracer->WithActiveSpan(span);
    span->End();
}

} // namespace proton
