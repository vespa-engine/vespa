// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadedflushtarget.h"

#include <vespa/check_require.h>
#include <vespa/searchcore/proton/server/igetserialnum.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <future>

using searchcorespi::FlushStats;
using searchcorespi::IFlushTarget;
using vespalib::makeLambdaTask;

namespace proton {

ThreadedFlushTarget::ThreadedFlushTarget(vespalib::Executor& executor, const IGetSerialNum& getSerialNum,
                                         const IFlushTarget::SP& target, const std::string& prefix)
    : FlushTargetProxy(target, prefix), _executor(executor), _getSerialNum(getSerialNum) {
}

namespace {
void call_init_flush(IFlushTarget* target, IFlushTarget::SerialNum serial, const IGetSerialNum* getSerialNum,
                     std::shared_ptr<search::IFlushToken> flush_token,
                     IFlushTarget::TaskPromise            target_task_promise) {
    /*
     * Called by document db executor
     * Serial number from flush engine might have become stale, obtain a fresh serial number now.
     */
    (void)serial;
    search::SerialNum freshSerial = getSerialNum->getSerialNum();
    CHECK(freshSerial >= serial);
    target->init_flush(freshSerial, std::move(flush_token), std::move(target_task_promise));
}
} // namespace

void ThreadedFlushTarget::init_flush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token,
                                     TaskPromise task_promise) {
    // Called by flush engine main thread via initFlush shim
    _executor.execute(makeLambdaTask(
        [this, currentSerial, flush_token(std::move(flush_token)), task_promise(std::move(task_promise))]() mutable {
            call_init_flush(_target.get(), currentSerial, &_getSerialNum, std::move(flush_token),
                            std::move(task_promise));
        }));
}

} // namespace proton
