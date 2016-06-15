// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/policies/andpolicy.h>

LOG_SETUP(".andpolicy");

namespace documentapi {

ANDPolicy::ANDPolicy(const string &param)
{
    if (!param.empty()) {
        mbus::Route route = mbus::Route::parse(param);
        for (uint32_t i = 0; i < route.getNumHops(); ++i) {
            _hops.push_back(route.getHop(i));
        }
    }
}

ANDPolicy::~ANDPolicy()
{
    // empty
}

void
ANDPolicy::select(mbus::RoutingContext &context)
{
    if (_hops.empty()) {
        context.addChildren(context.getAllRecipients());
    } else {
        for (std::vector<mbus::Hop>::iterator it = _hops.begin();
             it != _hops.end(); ++it)
        {
            mbus::Route route = context.getRoute();
            route.setHop(0, *it);
            context.addChild(route);
        }
    }
    context.setSelectOnRetry(false);
    context.addConsumableError(DocumentProtocol::ERROR_MESSAGE_IGNORED);
}

void
ANDPolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}

}
