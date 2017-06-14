// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flushtask.h"

namespace proton {

FlushTask::FlushTask(uint32_t taskId,
                     FlushEngine &engine,
                     const FlushContext::SP &ctx)
    : _taskId(taskId),
      _engine(engine),
      _context(ctx)
{
    assert(_context.get() != NULL);
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
