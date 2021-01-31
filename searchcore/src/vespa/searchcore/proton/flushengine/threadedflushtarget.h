// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushtargetproxy.h"

namespace vespalib { class Executor; }

namespace proton {

class IGetSerialNum;

/**
 * Implements a flush target that runs initFlush() as a task in the given
 * executor. This is used by the DocumentDB to ensure that initFlush() in the
 * underlying flush targets are run in the updater thread.
 */
class ThreadedFlushTarget : public FlushTargetProxy
{
private:
    using IFlushTarget = searchcorespi::IFlushTarget;
    vespalib::Executor  &_executor;
    const IGetSerialNum &_getSerialNum;

public:
    /**
     * Constructs a new instance of this class. If the argument executor is
     * the same as the one calling initFlush() on this object, make sure
     * that it has more than 1 thread to avoid a deadlock.
     *
     * @param executor The executor to submit the task to.
     * @param target   The target to decorate.
     */
    ThreadedFlushTarget(vespalib::Executor &executor,
                        const IGetSerialNum &getSerialNum,
                        const IFlushTarget::SP &target);

    /**
     * Constructs a new instance of this class. If the argument executor is
     * the same as the one calling initFlush() on this object, make sure
     * that it has more than 1 thread to avoid a deadlock.
     *
     * @param executor The executor to submit the task to.
     * @param target   The target to decorate.
     * @param prefix   The prefix to prepend to the target
     */
    ThreadedFlushTarget(vespalib::Executor &executor,
                        const IGetSerialNum &getSerialNum,
                        const IFlushTarget::SP &target,
                        const vespalib::string & prefix);

    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
};

} // namespace proton

