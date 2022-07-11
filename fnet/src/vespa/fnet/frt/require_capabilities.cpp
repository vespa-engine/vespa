// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require_capabilities.h"
#include "rpcrequest.h"
#include <vespa/fnet/connection.h>
#include <vespa/vespalib/net/connection_auth_context.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".fnet.frt.require_capabilities");

using namespace vespalib::net::tls;

bool
FRT_RequireCapabilities::allow(FRT_RPCRequest& req) const noexcept
{
    const auto& auth_ctx = req.GetConnection()->auth_context();
    const bool is_authorized = auth_ctx.capabilities().contains_all(_required_capabilities);
    if (!is_authorized) {
        auto peer_spec = req.GetConnection()->GetPeerSpec();
        std::string method_name(req.GetMethodName(), req.GetMethodNameLen());
        LOGBT(warning, peer_spec, "Permission denied for RPC method '%s'. "
                                  "Peer at %s with %s. Call requires %s, but peer has %s",
              method_name.c_str(), peer_spec.c_str(),
              to_string(auth_ctx.peer_credentials()).c_str(),
              _required_capabilities.to_string().c_str(),
              auth_ctx.capabilities().to_string().c_str());
    }
    return is_authorized;
}
