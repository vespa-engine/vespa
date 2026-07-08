// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itlssyncer.h"

#include <vespa/searchlib/common/serialnum.h>

#include <thread>

namespace vespalib {
class ThreadExecutor;
}

namespace search::transactionlog {
class SyncProxy;
}

namespace proton {

class IGetSerialNum;

/**
 * Class for syncing transaction log server in a safe manner.  The
 * serial number is retrieved by running a task in the document db
 * master thread to ensure that it reflects changes performed to data
 * structures as of now.
 */
class TlsSyncer : public ITlsSyncer {
    vespalib::ThreadExecutor&          _executor;
    std::thread::id                    _executor_thread_id;
    const IGetSerialNum&               _getSerialNum;
    search::transactionlog::SyncProxy& _proxy;

    search::SerialNum get_serial_num();

public:
    TlsSyncer(vespalib::ThreadExecutor& executor, const IGetSerialNum& getSerialNum,
              search::transactionlog::SyncProxy& proxy);
    virtual ~TlsSyncer() = default;

    void sync() override;
};

} // namespace proton
