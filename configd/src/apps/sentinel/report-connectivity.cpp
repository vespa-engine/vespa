// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "report-connectivity.h"
#include "connectivity.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/log/log.h>
#include <chrono>

LOG_SETUP(".report-connectivity");

using cloud::config::ModelConfig;
using namespace std::chrono_literals;

namespace config::sentinel {

SinglePing::~SinglePing() = default;

void SinglePing::returnStatus(bool ok) {
    status = ok ? "ok" : "ping failed";
    LOG(debug, "peer %s [port %d] -> %s", peerName.c_str(), peerPort, status.c_str());
    parent.requestDone();
}

void SinglePing::startCheck(FRT_Supervisor &orb) {
    check = std::make_unique<PeerCheck>(*this, peerName, peerPort, orb, 2500);
}

ReportConnectivity::ReportConnectivity(FRT_RPCRequest *req, FRT_Supervisor &orb)
  : _parentRequest(req),
    _orb(orb),
    _result(),
    _configFetcher()
{
    try {
        _configFetcher.subscribe<ModelConfig>("admin/model", this, 2000ms);
        _configFetcher.start();
        return;
    } catch (ConfigTimeoutException & ex) {
        LOG(warning, "Timeout getting model config: %s [no connectivity report]", ex.getMessage().c_str());
    } catch (InvalidConfigException& ex) {
        LOG(warning, "Invalid model config: %s [no connectivity report]", ex.getMessage().c_str());
    } catch (ConfigRuntimeException& ex) {
        LOG(warning, "Runtime exception getting model config: %s [no connectivity report]", ex.getMessage().c_str());
    }
    _parentRequest->SetError(FRTE_RPC_METHOD_FAILED, "failed getting model config");
    _parentRequest->Return();
}

ReportConnectivity::~ReportConnectivity() = default;

void ReportConnectivity::requestDone() {
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (--_remaining != 0) {
            return;
        }
    }
    finish();
}

void ReportConnectivity::configure(std::unique_ptr<ModelConfig> config) {
    _configFetcher.close();
    auto map = Connectivity::specsFrom(*config);
    for (const auto & [ hostname, port ] : map) {
        _result.emplace_back(*this, hostname, port);
    }
    LOG(debug, "making connectivity report for %zd peers", _result.size());
    _remaining = _result.size();
    for (auto & peer : _result) {
        peer.startCheck(_orb);
    }
}


void ReportConnectivity::finish() const {
    FRT_Values *dst = _parentRequest->GetReturn();
    FRT_StringValue *pt_hn = dst->AddStringArray(_result.size());
    FRT_StringValue *pt_ss = dst->AddStringArray(_result.size());
    for (const auto & peer : _result) {
        dst->SetString(pt_hn++, peer.peerName.c_str());
        dst->SetString(pt_ss++, peer.status.c_str());
    }
    _parentRequest->Return();
}

}
