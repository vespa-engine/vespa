// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/config-model.h>
#include <vespa/config/helper/configfetcher.h>
#include "model-owner.h"
#include "peer-check.h"
#include "status-callback.h"

#include <atomic>
#include <memory>
#include <string>
#include <vector>

namespace config::sentinel {

class ReportConnectivity : public StatusCallback
{
public:
    ReportConnectivity(FRT_RPCRequest *req, int timeout_ms, FRT_Supervisor &orb, ModelOwner &modelOwner);
    virtual ~ReportConnectivity();
    void returnStatus(bool ok) override;
private:
    void finish() const;
    FRT_RPCRequest *_parentRequest;
    std::vector<std::unique_ptr<PeerCheck>> _checks;
    std::atomic<size_t> _remaining;
};

}
