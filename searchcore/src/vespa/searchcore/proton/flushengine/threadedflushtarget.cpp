// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "threadedflushtarget.h"
#include <vespa/searchcore/proton/server/igetserialnum.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <future>
#include <cassert>

using searchcorespi::IFlushTarget;
using searchcorespi::FlushStats;
using vespalib::makeLambdaTask;

namespace proton {

ThreadedFlushTarget::ThreadedFlushTarget(vespalib::Executor &executor,
                                         const IGetSerialNum &getSerialNum,
                                         const IFlushTarget::SP &target)
    : FlushTargetProxy(target),
      _executor(executor),
      _getSerialNum(getSerialNum)
{
}

ThreadedFlushTarget::ThreadedFlushTarget(vespalib::Executor &executor,
                                         const IGetSerialNum &getSerialNum,
                                         const IFlushTarget::SP &target,
                                         const vespalib::string & prefix)
    : FlushTargetProxy(target, prefix),
      _executor(executor),
      _getSerialNum(getSerialNum)
{
}

namespace {
IFlushTarget::Task::UP
callInitFlush(IFlushTarget *target, IFlushTarget::SerialNum serial,
              const IGetSerialNum *getSerialNum, std::shared_ptr<search::IFlushToken> flush_token) {
    // Serial number from flush engine might have become stale, obtain
    // a fresh serial number now.
    (void) serial;
    search::SerialNum freshSerial = getSerialNum->getSerialNum();
    assert(freshSerial >= serial);
    return target->initFlush(freshSerial, std::move(flush_token));
}
}  // namespace

IFlushTarget::Task::UP
ThreadedFlushTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _executor.execute(makeLambdaTask([&]() {
        promise.set_value(callInitFlush(_target.get(), currentSerial, &_getSerialNum, flush_token));
    }));
    return future.get();
}

} // namespace proton
