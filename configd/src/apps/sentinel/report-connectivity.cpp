// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "report-connectivity.h"
#include "connectivity.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/log/log.h>
#include <chrono>

LOG_SETUP(".sentinel.report-connectivity");

using cloud::config::ModelConfig;
using namespace std::chrono_literals;

namespace config::sentinel {

ReportConnectivity::ReportConnectivity(FRT_RPCRequest *req, int timeout_ms, FRT_Supervisor &orb, ModelOwner &modelOwner)
  : _parentRequest(req),
    _checks()
{
    auto cfg = modelOwner.getModelConfig();
    if (cfg.has_value()) {
        auto map = Connectivity::specsFrom(cfg.value());
        LOG(debug, "making connectivity report for %zd peers", map.size());
        _remaining = map.size();
        timeout_ms += 50 * map.size();
        for (const auto & [ hostname, port ] : map) {
            _checks.emplace_back(std::make_unique<PeerCheck>(*this, hostname, port, orb, timeout_ms));
        }
    } else {
        _parentRequest->SetError(FRTE_RPC_METHOD_FAILED, "failed getting model config");
        _parentRequest->Return();
    }
}

ReportConnectivity::~ReportConnectivity() = default;

void ReportConnectivity::returnStatus(bool) {
    if (--_remaining == 0) {
        finish();
    }
}

void ReportConnectivity::finish() const {
    FRT_Values *dst = _parentRequest->GetReturn();
    FRT_StringValue *pt_hn = dst->AddStringArray(_checks.size());
    FRT_StringValue *pt_ss = dst->AddStringArray(_checks.size());
    for (const auto & peer : _checks) {
        dst->SetString(pt_hn++, peer->getHostname().c_str());
        dst->SetString(pt_ss++, peer->okStatus() ? "ok" : "ping failed");
    }
    _parentRequest->Return();
}

}
