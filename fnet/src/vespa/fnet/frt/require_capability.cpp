// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require_capability.h"
#include "rpcrequest.h"
#include <vespa/fnet/connection.h>
#include <vespa/vespalib/net/connection_auth_context.h>

bool
FRT_RequireCapability::allow(FRT_RPCRequest& req) const noexcept
{
    const auto& auth_ctx = req.GetConnection()->auth_context();
    return auth_ctx.capabilities().contains_all(_required_capabilities);
}
