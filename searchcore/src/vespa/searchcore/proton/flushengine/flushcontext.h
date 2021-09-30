// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "iflushhandler.h"
#include <vespa/vespalib/util/executor.h>

namespace proton {

/**
 * This class is used by FlushEngine to hold the necessary context for flushing
 * a single IFlushTarget.
 */
class FlushContext {
private:
    using IFlushTarget = searchcorespi::IFlushTarget;
    vespalib::string               _name;
    IFlushHandler::SP              _handler;
    IFlushTarget::SP               _target;
    searchcorespi::FlushTask::UP   _task;
    search::SerialNum              _lastSerial;

public:
    typedef std::shared_ptr<FlushContext> SP;
    typedef std::vector<SP> List;
    FlushContext(const FlushContext &) = delete;
    FlushContext & operator = (const FlushContext &) = delete;

    /**
     * Create a name of the handler and the target.
     *
     * @param handler The flush handler that contains the given target.
     * @param target  The target to flush.
     * @return the name created.
     */
    static vespalib::string createName(const IFlushHandler & handler, const IFlushTarget & target);

    /**
     * Constructs a new instance of this class.
     *
     * @param handler The flush handler that contains the given target.
     * @param target  The target to flush.
     */
    FlushContext(const IFlushHandler::SP &handler,
                 const IFlushTarget::SP &target,
                 search::SerialNum lastSerial);

    /**
     * Destructor. Will log a warning if it contains an unexecuted task.
     */
    ~FlushContext();

    /**
     * This method proxies initFlush() in IFlushTarget, but simplifies the call
     * signature. If this method returns true, the task to complete the flush is
     * available through getTask().
     *
     * @return True if a flush was initiated.
     */
    bool initFlush(std::shared_ptr<search::IFlushToken> flush_token);

    /**
     * Returns the unique name of this context. This is the concatenation of the
     * handler and target names.
     *
     * @return The name of this.
     */
    const vespalib::string & getName() const { return _name; }

    /**
     * Returns the flush handler of this context.
     *
     * @return The handler.
     */
    const IFlushHandler::SP & getHandler() const { return _handler; }

    /**
     * Returns the flush target of this context.
     *
     * @return The target.
     */
    const IFlushTarget::SP & getTarget() const { return _target; }

    /**
     * Returns the last serial number.
     *
     * @return The last serial number
     */
    search::SerialNum getLastSerial() const { return _lastSerial; }

    /**
     * Returns the task required to be run to complete an initiated flush. This
     * is null until initFlush() has been called.
     *
     * @return The flush completion task.
     */
    searchcorespi::FlushTask::UP getTask() { return std::move(_task); }

};

} // namespace proton

