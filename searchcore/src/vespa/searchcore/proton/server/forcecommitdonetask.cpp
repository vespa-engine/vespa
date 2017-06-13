// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcecommitdonetask.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

namespace proton {

ForceCommitDoneTask::ForceCommitDoneTask(IDocumentMetaStore &documentMetaStore)
    : _lidsToReuse(),
      _holdUnblockShrinkLidSpace(false),
      _documentMetaStore(documentMetaStore)
{
}

ForceCommitDoneTask::~ForceCommitDoneTask()
{
}

void
ForceCommitDoneTask::reuseLids(std::vector<uint32_t> &&lids)
{
    assert(_lidsToReuse.empty());
    _lidsToReuse = std::move(lids);
}

void
ForceCommitDoneTask::run()
{
    if (!_lidsToReuse.empty()) {
        if (_lidsToReuse.size() == 1) {
            _documentMetaStore.removeComplete(_lidsToReuse[0]);
        } else {
            _documentMetaStore.removeBatchComplete(_lidsToReuse);
        }
    }
    if (_holdUnblockShrinkLidSpace) {
        _documentMetaStore.holdUnblockShrinkLidSpace();
    }
}

}  // namespace proton
