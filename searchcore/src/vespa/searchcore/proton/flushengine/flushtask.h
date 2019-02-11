// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/flushengine/flushengine.h>

namespace proton {

/**
 * This class decorates a task returned by initFlush() in IFlushTarget so that
 * the appropriate callback is invoked on the running FlushEngine.
 */
class FlushTask : public vespalib::Executor::Task
{
private:
    uint32_t                          _taskId;
    FlushEngine                      &_engine;
    FlushContext::SP                  _context;

public:
    FlushTask(const FlushTask &) = delete;
    FlushTask & operator = (const FlushTask &) = delete;
    /**
     * Constructs a new instance of this class.
     *
     * @param taskId The identifier used by IFlushStrategy.
     * @param engine The running flush engine.
     * @param ctx    The context of the flush to perform.
     */
    FlushTask(uint32_t taskId, FlushEngine &engine, const FlushContext::SP &ctx);

    /**
     * Destructor. Notifies the engine that the flush is done to prevent the
     * engine from locking targets because of a glitch.
     */
    ~FlushTask();

    // Implements Executor::Task.
    void run() override;
};

} // namespace proton

