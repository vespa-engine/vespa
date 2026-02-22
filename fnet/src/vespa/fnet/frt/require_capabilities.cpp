// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require_capabilities.h"

#include "rpcrequest.h"

#include <vespa/fnet/connection.h>
#include <vespa/log/bufferedlogger.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/net/tls/capability_env_config.h>
#include <vespa/vespalib/net/tls/statistics.h>
LOG_SETUP(".fnet.frt.require_capabilities");

using namespace vespalib::net::tls;

bool FRT_RequireCapabilities::allow(FRT_RPCRequest& req) const noexcept {
    const auto& auth_ctx = req.GetConnection()->auth_context();
    const bool  is_authorized = auth_ctx.capabilities().contains_all(_required_capabilities);
    if (is_authorized) {
        return true;
    } else {
        CapabilityStatistics::get().inc_rpc_capability_checks_failed();
        const auto mode = capability_enforcement_mode_from_env();
        if (mode == CapabilityEnforcementMode::Disable) {
            return true;
        }
        auto        peer_spec = req.GetConnection()->GetPeerSpec();
        std::string method_name(req.GetMethodName(), req.GetMethodNameLen());
        LOGBT(warning, peer_spec,
              "%sPermission denied for RPC method '%s'. "
              "Peer at %s with %s. Call requires %s, but peer has %s",
              ((mode == CapabilityEnforcementMode::LogOnly) ? "(Dry-run only, not enforced): " : ""),
              method_name.c_str(), peer_spec.c_str(), auth_ctx.peer_credentials().to_string().c_str(),
              _required_capabilities.to_string().c_str(), auth_ctx.capabilities().to_string().c_str());
        return (mode != CapabilityEnforcementMode::Enforce);
    }
}

std::unique_ptr<FRT_RequireCapabilities> FRT_RequireCapabilities::of(Capability required_capability) {
    return std::make_unique<FRT_RequireCapabilities>(CapabilitySet::of({required_capability}));
}

std::unique_ptr<FRT_RequireCapabilities> FRT_RequireCapabilities::of(CapabilitySet required_capabilities) {
    return std::make_unique<FRT_RequireCapabilities>(required_capabilities);
}
