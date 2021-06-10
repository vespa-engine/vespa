// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/config-model.h>
#include <vespa/config/helper/configfetcher.h>
#include "peer-check.h"
#include "status-callback.h"

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace config::sentinel {

class ReportConnectivity;

struct ConnectivityCheckResult : StatusCallback {
    ReportConnectivity& parent;
    std::string peerName;
    int peerPort;
    std::string status;
    std::unique_ptr<PeerCheck> check;

    ConnectivityCheckResult(ReportConnectivity& owner, const std::string &hostname, int port)
      : parent(owner),
        peerName(hostname),
        peerPort(port),
        status("unknown"),
        check(nullptr)
    {}

    ConnectivityCheckResult(ConnectivityCheckResult &&) = default;
    ConnectivityCheckResult(const ConnectivityCheckResult &) = default;

    virtual ~ConnectivityCheckResult();
    void returnStatus(bool ok) override;
};

struct ConnectivityReportResult {
    std::vector<ConnectivityCheckResult> peers;
    ~ConnectivityReportResult();
};


class ReportConnectivity : public config::IFetcherCallback<cloud::config::ModelConfig>
{
public:
    ReportConnectivity(FRT_RPCRequest *req, FRT_Supervisor &orb);
    ~ReportConnectivity();
    void requestDone();
    /** from IFetcherCallback */
    void configure(std::unique_ptr<cloud::config::ModelConfig> config) override;
private:
    void finish() const;
    FRT_RPCRequest *_parentRequest;
    FRT_Supervisor &_orb;
    ConnectivityReportResult _result;
    config::ConfigFetcher _configFetcher;
    std::mutex _lock;
    size_t _remaining;
};

}
