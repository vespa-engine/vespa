// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_initializer.h"
#include "idocumentsubdb.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchcorespi/index/i_thread_service.h>

using vespalib::makeLambdaTask;

namespace proton {

DocumentSubDbInitializer::DocumentSubDbInitializer(IDocumentSubDB &subDB, searchcorespi::index::IThreadService &master)
    : InitTask(),
      _result(),
      _documentMetaStoreInitTask(),
      _subDB(subDB),
      _master(master)
{ }

void
DocumentSubDbInitializer::
addDocumentMetaStoreInitTask(InitTask::SP documentMetaStoreInitTask)
{
    assert(!_documentMetaStoreInitTask);
    _documentMetaStoreInitTask = documentMetaStoreInitTask;
    addDependency(documentMetaStoreInitTask);
}

void
DocumentSubDbInitializer::run()
{
    std::promise<void> promise;
    auto future = promise.get_future();
    _master.execute(makeLambdaTask([&]() { _subDB.setup(_result); promise.set_value(); }));
    future.wait();
}

} // namespace proton
