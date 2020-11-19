// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "removedonecontext.h"
#include "removedonetask.h"
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>
#include <cassert>

namespace proton {

RemoveDoneContext::RemoveDoneContext(FeedToken token, IPendingLidTracker::Token uncommitted, vespalib::Executor &executor,
                                     IDocumentMetaStore &documentMetaStore,
                                     uint32_t lid)
    : OperationDoneContext(std::move(token)),
      _executor(executor),
      _task(),
      _uncommitted(std::move(uncommitted))
{
    if (lid != 0) {
        _task = std::make_unique<RemoveDoneTask>(documentMetaStore, lid);
    }
}

RemoveDoneContext::~RemoveDoneContext()
{
    ack();
    if (_task) {
        vespalib::Executor::Task::UP res = _executor.execute(std::move(_task));
        assert(!res);
    }
}

}  // namespace proton
