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

TlsSyncer::TlsSyncer(vespalib::ThreadExecutor& executor, const IGetSerialNum& getSerialNum,
                     search::transactionlog::SyncProxy& proxy)
    : _executor(executor), _getSerialNum(getSerialNum), _proxy(proxy) {
}

void TlsSyncer::sync() {
    std::promise<SerialNum> promise;
    std::future<SerialNum>  future = promise.get_future();
    _executor.execute(makeLambdaTask([&]() { promise.set_value(_getSerialNum.getSerialNum()); }));
    SerialNum serialNum = future.get();
    _proxy.sync(serialNum);
}

} // namespace proton
