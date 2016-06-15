// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/flushengine/flushengine.h>

namespace proton {

/**
 * This class decorates a task returned by initFlush() in IFlushTarget so that
 * the appropriate callback is invoked on the running FlushEngine.
 */
class FlushTask : public boost::noncopyable,
                  public vespalib::Executor::Task
{
private:
    uint32_t                          _taskId;
    FlushEngine                      &_engine;
    FlushContext::SP                  _context;
    search::SerialNum _serial;

public:
    /**
     * Constructs a new instance of this class.
     *
     * @param taskId The identifier used by IFlushStrategy.
     * @param engine The running flush engine.
     * @param ctx    The context of the flush to perform.
     * @param serial The oldest unflushed serial available in the handler once
     *               this task has been run.
     */
    FlushTask(uint32_t taskId,
              FlushEngine &engine,
              const FlushContext::SP &ctx,
              search::SerialNum serial);

    /**
     * Destructor. Notifies the engine that the flush is done to prevent the
     * engine from locking targets because of a glitch.
     */
    ~FlushTask();

    // Implements Executor::Task.
    void run();
};

} // namespace proton

