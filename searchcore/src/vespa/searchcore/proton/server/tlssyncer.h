// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "itlssyncer.h"

namespace vespalib { class ThreadExecutor; }
namespace search::transactionlog { class SyncProxy; }

namespace proton {

class IGetSerialNum;

/**
 * Class for syncing transaction log server in a safe manner.  The
 * serial number is retrieved by running a task in the document db
 * master thread to ensure that it reflects changes performed to data
 * structures as of now.
 */
class TlsSyncer : public ITlsSyncer
{
    vespalib::ThreadExecutor &_executor;
    const IGetSerialNum &_getSerialNum;
    search::transactionlog::SyncProxy &_proxy;
public:
    virtual ~TlsSyncer() = default;

    TlsSyncer(vespalib::ThreadExecutor &executor,
              const IGetSerialNum &getSerialNum,
              search::transactionlog::SyncProxy &proxy);

    void sync() override;
};

}
