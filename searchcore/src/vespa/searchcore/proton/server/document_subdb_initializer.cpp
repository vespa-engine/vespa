// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_subdb_initializer.h"
#include "idocumentsubdb.h"
#include <future>
#include <vespa/searchlib/common/lambdatask.h>
#include <vespa/searchcorespi/index/i_thread_service.h>

using search::makeLambdaTask;

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
    std::promise<bool> promise;
    std::future<bool> future = promise.get_future();
    _master.execute(makeLambdaTask([&]() { _subDB.setup(_result); promise.set_value(true); }));
    (void) future.get();
}

} // namespace proton