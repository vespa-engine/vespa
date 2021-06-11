// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/config-model.h>
#include <vespa/config/helper/configfetcher.h>
#include "model-subscriber.h"
#include "peer-check.h"
#include "status-callback.h"

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace config::sentinel {

class ReportConnectivity;

struct SinglePing : StatusCallback {
    ReportConnectivity& parent;
    std::string peerName;
    int peerPort;
    std::string status;
    std::unique_ptr<PeerCheck> check;

    SinglePing(ReportConnectivity& owner, const std::string &hostname, int port)
      : parent(owner),
        peerName(hostname),
        peerPort(port),
        status("unknown"),
        check(nullptr)
    {}

    SinglePing(SinglePing &&) = default;
    SinglePing(const SinglePing &) = default;

    virtual ~SinglePing();
    void startCheck(FRT_Supervisor &orb);
    void returnStatus(bool ok) override;
};


class ReportConnectivity
{
public:
    ReportConnectivity(FRT_RPCRequest *req, FRT_Supervisor &orb, ModelSubscriber &modelSubscriber);
    ~ReportConnectivity();
    void requestDone();
private:
    void finish() const;
    FRT_RPCRequest *_parentRequest;
    FRT_Supervisor &_orb;
    std::vector<SinglePing> _result;
    std::mutex _lock;
    size_t _remaining;
};

}
