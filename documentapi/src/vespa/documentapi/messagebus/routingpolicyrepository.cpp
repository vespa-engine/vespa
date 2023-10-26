// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routingpolicyrepository.h"

#include <vespa/log/log.h>
LOG_SETUP(".routingpolicyrepository");

namespace documentapi {

RoutingPolicyRepository::RoutingPolicyRepository() :
    _lock(),
    _factories()
{
    // empty
}

void
RoutingPolicyRepository::putFactory(const string &name, IRoutingPolicyFactory::SP factory)
{
    std::lock_guard guard(_lock);
    _factories[name] = factory;
}

IRoutingPolicyFactory::SP
RoutingPolicyRepository::getFactory(const string &name) const
{
    std::lock_guard guard(_lock);
    FactoryMap::const_iterator it = _factories.find(name);
    if (it != _factories.end()) {
        return it->second;
    }
    return IRoutingPolicyFactory::SP();
}

mbus::IRoutingPolicy::UP
RoutingPolicyRepository::createPolicy(const string &name, const string &param) const
{
    IRoutingPolicyFactory::SP factory = getFactory(name);
    if (factory.get() == NULL) {
        LOG(error, "No routing policy factory found for name '%s'.", name.c_str());
        return mbus::IRoutingPolicy::UP();
    }
    mbus::IRoutingPolicy::UP ret = factory->createPolicy(param);
    if (ret.get() == NULL) {
        LOG(error, "Routing policy factory failed to create a routing policy for parameter '%s'.",
            param.c_str());
        return mbus::IRoutingPolicy::UP();
    }
    return ret;
}

}
