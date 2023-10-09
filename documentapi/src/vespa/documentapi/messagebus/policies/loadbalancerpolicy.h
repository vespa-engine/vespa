// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "loadbalancer.h"
#include "externslobrokpolicy.h"

namespace documentapi {

class LoadBalancerPolicy : public ExternSlobrokPolicy
{
public:
    LoadBalancerPolicy(const string& param);
    void doSelect(mbus::RoutingContext &context) override;

    /**
       Finds the TCP address of the target docproc.

       @return Returns a hop representing the TCP address of the target docproc, or null if none could be found.
    */
    std::pair<string, int> getRecipient(mbus::RoutingContext& context) {
        return _loadBalancer->getRecipient(lookup(context, _pattern));
    }

    void merge(mbus::RoutingContext &context) override;

private:
    string _pattern;
    string _cluster;
    string _session;
    std::unique_ptr<LoadBalancer> _loadBalancer;
};

}

