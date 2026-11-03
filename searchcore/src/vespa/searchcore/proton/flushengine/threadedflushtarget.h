// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushtargetproxy.h"

namespace vespalib {
class Executor;
}

namespace proton {

class IGetSerialNum;

/**
 * Implements a flush target that runs init_flush() as a task in the given executor, which must be the document db
 * executor. This is used by the DocumentDB to ensure that init_flush() picks up an updated serial number while in the
 * document db executor. The underlying flush targets must forward init_flush() to the updater thread on their own.
 */
class ThreadedFlushTarget : public FlushTargetProxy {
private:
    using IFlushTarget = searchcorespi::IFlushTarget;
    vespalib::Executor&  _executor;
    const IGetSerialNum& _getSerialNum;

public:
    /**
     * Constructs a new instance of this class.
     *
     * @param executor The executor to submit the task to. Must be document db executor.
     * @param target   The target to decorate.
     * @param prefix   The prefix to prepend to the target
     */
    ThreadedFlushTarget(vespalib::Executor& executor, const IGetSerialNum& getSerialNum,
                        const IFlushTarget::SP& target, const std::string& prefix);

    void init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                    TaskPromise task_promise) override;
};

} // namespace proton
