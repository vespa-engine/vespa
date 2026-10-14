// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_attribute_lid_space_flush_target.h"

namespace proton {

ShrinkAttributeLidSpaceFlushTarget::ShrinkAttributeLidSpaceFlushTarget(const std::string& name, Type type,
                                                                       Component component,
                                                                       SerialNum flushedSerialNum,
                                                                       std::shared_ptr<ICompactableLidSpace> target,
                                                                       ISequencedTaskExecutor&               executor,
                                                                       ISequencedTaskExecutor::ExecutorId executor_id)
    : ShrinkLidSpaceFlushTarget("attribute.shrink." + name, type, component, flushedSerialNum,
                                vespalib::system_clock::now(), std::move(target)),
      _executor(executor),
      _executor_id(executor_id) {
}

ShrinkAttributeLidSpaceFlushTarget::~ShrinkAttributeLidSpaceFlushTarget() = default;

void ShrinkAttributeLidSpaceFlushTarget::init_flush(SerialNum                            currentSerial,
                                                    std::shared_ptr<search::IFlushToken> flush_token,
                                                    TaskPromise                          task_promise) {
    // Called by document db executor.
    TaskPromise proxied_task_promise;
    auto        future_flush_task = proxied_task_promise.get_future();
    _executor.executeLambda(_executor_id, [this, currentSerial, flush_token(std::move(flush_token)),
                                           proxied_task_promise(std::move(proxied_task_promise))]() mutable {
        ShrinkLidSpaceFlushTarget::init_flush(currentSerial, std::move(flush_token), std::move(proxied_task_promise));
    });
    task_promise.set_value(future_flush_task.get());
}

} // namespace proton
