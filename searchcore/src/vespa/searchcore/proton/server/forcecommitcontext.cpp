// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcecommitcontext.h"
#include "forcecommitdonetask.h"
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <cassert>

namespace proton {

ForceCommitContext::ForceCommitContext(vespalib::Executor &executor,
                                       IDocumentMetaStore &documentMetaStore)
    : _executor(executor),
      _task(std::make_unique<ForceCommitDoneTask>(documentMetaStore)),
      _committedDocIdLimit(0u),
      _docIdLimit(nullptr)
{
}

ForceCommitContext::~ForceCommitContext()
{
    if (_docIdLimit != nullptr) {
        _docIdLimit->bumpUpLimit(_committedDocIdLimit);
    }
    if (!_task->empty()) {
        vespalib::Executor::Task::UP res = _executor.execute(std::move(_task));
        assert(!res);
    }
}

void
ForceCommitContext::reuseLids(std::vector<uint32_t> &&lids)
{
    _task->reuseLids(std::move(lids));
}

void
ForceCommitContext::holdUnblockShrinkLidSpace()
{
    _task->holdUnblockShrinkLidSpace();
}

void
ForceCommitContext::registerCommittedDocIdLimit(uint32_t committedDocIdLimit,
                                                DocIdLimit *docIdLimit)
{
    _committedDocIdLimit = committedDocIdLimit;
    _docIdLimit = docIdLimit;
}

}  // namespace proton
