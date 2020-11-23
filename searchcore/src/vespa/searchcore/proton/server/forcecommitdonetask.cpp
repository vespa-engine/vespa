// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcecommitdonetask.h"
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/reference/i_pending_gid_to_lid_changes.h>

namespace proton {

ForceCommitDoneTask::ForceCommitDoneTask(IDocumentMetaStore &documentMetaStore, std::unique_ptr<IPendingGidToLidChanges> pending_gid_to_lid_changes)
    : _lidsToReuse(),
      _holdUnblockShrinkLidSpace(false),
      _documentMetaStore(documentMetaStore),
      _pending_gid_to_lid_changes(std::move(pending_gid_to_lid_changes))
{
}

ForceCommitDoneTask::~ForceCommitDoneTask() = default;

void
ForceCommitDoneTask::reuseLids(std::vector<uint32_t> &&lids)
{
    assert(_lidsToReuse.empty());
    _lidsToReuse = std::move(lids);
}

void
ForceCommitDoneTask::run()
{
    if (_pending_gid_to_lid_changes) {
        _pending_gid_to_lid_changes->notify_done();
    }
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
