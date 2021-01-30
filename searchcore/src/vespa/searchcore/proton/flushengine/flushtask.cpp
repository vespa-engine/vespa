// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushtask.h"
#include "flushengine.h"

namespace proton {

FlushTask::FlushTask(uint32_t taskId,
                     FlushEngine &engine,
                     std::shared_ptr<FlushContext> ctx)
    : _taskId(taskId),
      _engine(engine),
      _context(std::move(ctx))
{
    assert(_context);
}

FlushTask::~FlushTask()
{
    _engine.flushDone(*_context, _taskId);
}

void
FlushTask::run()
{
    searchcorespi::FlushTask::UP task(_context->getTask());
    search::SerialNum flushSerial(task->getFlushSerial());
    if (flushSerial != 0) {
        _context->getHandler()->syncTls(flushSerial);
    }
    task->run();
    task.reset();
}

} // namespace proton
