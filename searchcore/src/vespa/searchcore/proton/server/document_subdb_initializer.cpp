// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_initializer.h"

#include "idocumentsubdb.h"

#include <vespa/check_require.h>
#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcorespi/common/tracing.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/lambdatask.h>

using searchcorespi::common::get_tracer;
using vespalib::makeLambdaTask;

namespace proton {

DocumentSubDbInitializer::DocumentSubDbInitializer(IDocumentSubDB&                       subDB,
                                                   searchcorespi::index::IThreadService& master)
    : InitTask(), _result(), _documentMetaStoreInitTask(), _subDB(subDB), _master(master) {
}

void DocumentSubDbInitializer::addDocumentMetaStoreInitTask(InitTask::SP documentMetaStoreInitTask) {
    CHECK(!_documentMetaStoreInitTask);
    _documentMetaStoreInitTask = documentMetaStoreInitTask;
    addDependency(documentMetaStoreInitTask);
}

void DocumentSubDbInitializer::run() {
    auto tracer = get_tracer();
    auto span = tracer->StartSpan(
        "Initialize document subdb",
        {{vespa_opentelemetry::span_attribute_names::document_type,
          _subDB.doc_type_and_subdb_name()->doc_type_name().getName()},
         {vespa_opentelemetry::span_attribute_names::subdb, _subDB.doc_type_and_subdb_name()->subdb_name()}});
    auto               scope = tracer->WithActiveSpan(span);
    std::promise<void> promise;
    auto               future = promise.get_future();
    _master.execute(makeLambdaTask([&, tracer, span]() {
        auto span_copy = span;
        auto scope2 = tracer->WithActiveSpan(span_copy);
        _subDB.setup(_result);
        promise.set_value();
    }));
    future.wait();
    span->End();
}

} // namespace proton
