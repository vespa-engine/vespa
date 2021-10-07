// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonetask.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

namespace proton {

RemoveDoneTask::RemoveDoneTask(IDocumentMetaStore &documentMetaStore, uint32_t lid)
    : vespalib::Executor::Task(),
      _documentMetaStore(documentMetaStore),
      _lid(lid)
{
}

RemoveDoneTask::~RemoveDoneTask() = default;

void
RemoveDoneTask::run()
{
    if (_lid != 0u) {
        _documentMetaStore.removeComplete(_lid);
    }
}

}  // namespace proton
