// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "loadbalancerpolicy.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/routing/ihopdirective.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/log/log.h>

LOG_SETUP(".loadbalancerpolicy");

namespace documentapi {

LoadBalancerPolicy::LoadBalancerPolicy(const string& param)
    : ExternSlobrokPolicy(parse(param))
{
    std::map<string, string> params(parse(param));

    if (params.find("cluster") != params.end()) {
        _cluster = params.find("cluster")->second;
    } else {
        _error = "Required parameter cluster not set";
        return;
    }

    if (params.find("session") != params.end()) {
        _session = params.find("session")->second;
    } else {
        _error = "Required parameter session not set";
        return;
    }

    _pattern = _cluster + "/*/" + _session;
    _loadBalancer.reset(new LoadBalancer(_cluster, _session));
}

void
LoadBalancerPolicy::doSelect(mbus::RoutingContext& context) {
    std::pair<string, int> node = getRecipient(context);

    if (node.second != -1) {
        context.setContext((uint64_t)node.second);
        mbus::Route route = context.getRoute();
        route.setHop(0, mbus::Hop::parse(node.first + "/" + _session));
        context.addChild(route);
    } else {
        context.setError(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE,
                             "Could not resolve any nodes to send to in pattern " + _pattern);
    }
}

void
LoadBalancerPolicy::merge(mbus::RoutingContext& context) {
    mbus::RoutingNodeIterator it = context.getChildIterator();
    mbus::Reply::UP reply = it.removeReply();

    uint64_t target = context.getContext().value.UINT64;

    bool busy = false;
    for (uint32_t i = 0; i < reply->getNumErrors(); i++) {
        if (reply->getError(i).getCode() == mbus::ErrorCode::SESSION_BUSY) {
            string lastSpec = _loadBalancer->getNodeInfo()[target].lastSpec;

            if (reply->getError(i).getMessage().find(lastSpec) == string::npos) {
                LOG(debug, "Received busy with message %s, doesn't contain target %s so not updating weight.", reply->getError(i).getMessage().c_str(), lastSpec.c_str());
            } else {
                LOG(debug, "Received busy for target node %d reducing weight of that node.", (int)target);
                busy = true;
            }
        }
    }

    _loadBalancer->received(target, busy);

    context.setReply(std::move(reply));
}

}

