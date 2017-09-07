// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remove_batch_done_context.h"
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_handler.h>

namespace proton {

RemoveBatchDoneContext::RemoveBatchDoneContext(vespalib::Executor &executor,
                                               vespalib::Executor::Task::UP task,
                                               IGidToLidChangeHandler &gidToLidChangeHandler,
                                               std::vector<document::GlobalId> gidsToRemove,
                                               search::SerialNum serialNum)
    : search::ScheduleTaskCallback(executor, std::move(task)),
      _gidToLidChangeHandler(gidToLidChangeHandler),
      _gidsToRemove(std::move(gidsToRemove)),
      _serialNum(serialNum)
{
}

RemoveBatchDoneContext::~RemoveBatchDoneContext()
{
    for (const auto &gid : _gidsToRemove) {
        _gidToLidChangeHandler.notifyRemoveDone(gid, _serialNum);
    }
}

}  // namespace proton
