// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".errorpolicy");

#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include "errorpolicy.h"

namespace documentapi {

ErrorPolicy::ErrorPolicy(const string &msg) :
    _msg(msg)
{
    // empty
}

void
ErrorPolicy::select(mbus::RoutingContext &ctx)
{
    ctx.setError(DocumentProtocol::ERROR_POLICY_FAILURE, _msg);
}

void
ErrorPolicy::merge(mbus::RoutingContext &)
{
    LOG_ASSERT(false);
}

}
