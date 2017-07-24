// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "protocolrepository.h"

#include <vespa/log/log.h>
LOG_SETUP(".protocolrepository");

namespace mbus {

ProtocolRepository::ProtocolRepository() {}
ProtocolRepository::~ProtocolRepository() {}

void
ProtocolRepository::clearPolicyCache()
{
    vespalib::LockGuard guard(_lock);
    _routingPolicyCache.clear();
}

IProtocol::SP
ProtocolRepository::putProtocol(const IProtocol::SP & protocol)
{
    const string &name = protocol->getName();
    if (_protocols.find(name) != _protocols.end()) {
        clearPolicyCache();
    }
    IProtocol::SP prev = _protocols[name];
    _protocols[name] = protocol;
    return prev;
}

bool
ProtocolRepository::hasProtocol(const string &name) const
{
    return _protocols.find(name) != _protocols.end();
}

IProtocol *
ProtocolRepository::getProtocol(const string &name)
{
    ProtocolMap::iterator it = _protocols.find(name);
    if (it != _protocols.end()) {
        return it->second.get();
    }
    return nullptr;
}

IRoutingPolicy::SP
ProtocolRepository::getRoutingPolicy(const string &protocolName,
                                     const string &policyName,
                                     const string &policyParam)
{
    string cacheKey = protocolName;
    cacheKey.append('.').append(policyName).append(".").append(policyParam);
    vespalib::LockGuard guard(_lock);
    RoutingPolicyCache::iterator cit = _routingPolicyCache.find(cacheKey);
    if (cit != _routingPolicyCache.end()) {
        return cit->second;
    }
    ProtocolMap::iterator pit = _protocols.find(protocolName);
    if (pit == _protocols.end()) {
        LOG(error, "Protocol '%s' not supported.", protocolName.c_str());
        return IRoutingPolicy::SP();
    }
    IRoutingPolicy::UP policy;
    try {
        policy = pit->second->createPolicy(policyName, policyParam);
    } catch (const std::exception &e) {
        LOG(error, "Protocol '%s' threw an exception; %s", protocolName.c_str(), e.what());
    }
    if (policy.get() == NULL) {
        LOG(error, "Protocol '%s' failed to create routing policy '%s' with parameter '%s'.",
            protocolName.c_str(), policyName.c_str(), policyParam.c_str());
        return IRoutingPolicy::SP();
    }
    IRoutingPolicy::SP ret(policy.release());
    _routingPolicyCache[cacheKey] = ret;
    return ret;
}

}
