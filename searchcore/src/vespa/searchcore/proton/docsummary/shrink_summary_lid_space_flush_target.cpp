// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shrink_summary_lid_space_flush_target.h"

#include <vespa/vespalib/util/lambdatask.h>

using vespalib::makeLambdaTask;

namespace proton {

ShrinkSummaryLidSpaceFlushTarget::ShrinkSummaryLidSpaceFlushTarget(const std::string& name, Type type,
                                                                   Component component, SerialNum flushedSerialNum,
                                                                   vespalib::Executor& summaryService,
                                                                   std::shared_ptr<ICompactableLidSpace> target)
    : ShrinkLidSpaceFlushTarget(name, type, component, flushedSerialNum, vespalib::system_clock::now(),
                                std::move(target)),
      _summaryService(summaryService) {
}

ShrinkSummaryLidSpaceFlushTarget::~ShrinkSummaryLidSpaceFlushTarget() = default;

void ShrinkSummaryLidSpaceFlushTarget::init_flush(SerialNum                            currentSerial,
                                                  std::shared_ptr<search::IFlushToken> flush_token,
                                                  TaskPromise                          task_promise) {
    // Called by document db executor.
    TaskPromise proxied_task_promise;
    auto        future_flush_task = proxied_task_promise.get_future();
    _summaryService.execute(makeLambdaTask([this, currentSerial, flush_token(std::move(flush_token)),
                                            proxied_task_promise(std::move(proxied_task_promise))]() mutable {
        ShrinkLidSpaceFlushTarget::init_flush(currentSerial, std::move(flush_token), std::move(proxied_task_promise));
    }));
    task_promise.set_value(future_flush_task.get());
}

} // namespace proton
