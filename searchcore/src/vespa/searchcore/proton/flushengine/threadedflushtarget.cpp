// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadedflushtarget.h"

#include <vespa/searchcore/proton/server/igetserialnum.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <cassert>
#include <future>

using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using vespalib::makeLambdaTask;

namespace proton {

ThreadedFlushTarget::ThreadedFlushTarget(vespalib::Executor& executor, const IGetSerialNum& getSerialNum,
                                         const IFlushTarget::SP& target)
    : FlushTargetProxy(target), _executor(executor), _getSerialNum(getSerialNum) {
}

ThreadedFlushTarget::ThreadedFlushTarget(vespalib::Executor& executor, const IGetSerialNum& getSerialNum,
                                         const IFlushTarget::SP& target, const std::string& prefix)
    : FlushTargetProxy(target, prefix), _executor(executor), _getSerialNum(getSerialNum) {
}

namespace {
void call_init_flush(IFlushTarget* target, IFlushTarget::SerialNum serial, const IGetSerialNum* getSerialNum,
                     std::shared_ptr<search::IFlushToken> flush_token,
                     IFlushTarget::TaskPromise            target_task_promise) {
    // Serial number from flush engine might have become stale, obtain
    // a fresh serial number now.
    (void)serial;
    search::SerialNum freshSerial = getSerialNum->getSerialNum();
    assert(freshSerial >= serial);
    target->init_flush(freshSerial, std::move(flush_token), std::move(target_task_promise));
}
} // namespace

void ThreadedFlushTarget::init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                                     TaskPromise task_promise) {
    // Normally called by flush engine main thread
    TaskPromise target_task_promise;
    auto        future_target_task = target_task_promise.get_future();
    _executor.execute(makeLambdaTask([&, target_task_promise(std::move(target_task_promise))]() mutable {
        call_init_flush(_target.get(), currentSerial, &_getSerialNum, flush_token, std::move(target_task_promise));
    }));
    task_promise.set_value(future_target_task.get());
}

} // namespace proton
