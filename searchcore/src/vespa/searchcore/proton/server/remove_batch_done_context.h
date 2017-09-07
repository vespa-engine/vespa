// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vector>

namespace proton
{

class IGidToLidChangeHandler;

/**
 * Context class for document batch remove that notifies gid to lid
 * change handler about each remove done and schedules a
 * task when instance is destroyed. Typically a shared pointer to an
 * instance is passed around to multiple worker threads that performs
 * portions of a larger task before dropping the shared pointer.
 */
class RemoveBatchDoneContext : public search::ScheduleTaskCallback
{
    IGidToLidChangeHandler         &_gidToLidChangeHandler;
    std::vector<document::GlobalId> _gidsToRemove;
    search::SerialNum               _serialNum;

public:
    RemoveBatchDoneContext(vespalib::Executor &executor,
                           vespalib::Executor::Task::UP task,
                           IGidToLidChangeHandler &gidToLidChangeHandler,
                           std::vector<document::GlobalId> gidsToRemove,
                           search::SerialNum serialNum);

    virtual ~RemoveBatchDoneContext();
};

}  // namespace proton
