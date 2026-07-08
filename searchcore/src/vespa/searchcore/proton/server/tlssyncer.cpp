// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tlssyncer.h"

#include "igetserialnum.h"

#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadexecutor.h>

#include <future>

using search::SerialNum;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

std::thread::id get_thread_id(vespalib::ThreadExecutor& executor) {
    std::promise<std::thread::id> promise;
    auto                          future = promise.get_future();
    executor.execute(makeLambdaTask([&]() { promise.set_value(std::this_thread::get_id()); }));
    return future.get();
}

} // namespace

TlsSyncer::TlsSyncer(vespalib::ThreadExecutor& executor, const IGetSerialNum& getSerialNum,
                     search::transactionlog::SyncProxy& proxy)
    : _executor(executor), _executor_thread_id(get_thread_id(executor)), _getSerialNum(getSerialNum), _proxy(proxy) {
}

SerialNum TlsSyncer::get_serial_num() {
    if (std::this_thread::get_id() == _executor_thread_id) {
        return _getSerialNum.getSerialNum();
    } else {
        std::promise<SerialNum> promise;
        std::future<SerialNum>  future = promise.get_future();
        _executor.execute(makeLambdaTask([&]() { promise.set_value(_getSerialNum.getSerialNum()); }));
        return future.get();
    }
}

void TlsSyncer::sync() {
    auto serial_num = get_serial_num();
    _proxy.sync(serial_num);
}

} // namespace proton
