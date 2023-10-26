// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "protocolrepository.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".protocolrepository");

namespace mbus {

ProtocolRepository::ProtocolRepository() : _numProtocols(0) {}
ProtocolRepository::~ProtocolRepository() = default;

void
ProtocolRepository::clearPolicyCache()
{
    std::lock_guard guard(_lock);
    _routingPolicyCache.clear();
}

IProtocol::SP
ProtocolRepository::putProtocol(const IProtocol::SP & protocol)
{
    const string &name = protocol->getName();
    const auto numProtocols = _numProtocols.load();
    size_t protocolIndex = numProtocols;
    for (size_t i(0); i < numProtocols; i++) {
        if (_protocols[i].first == name) {
            protocolIndex = i;
            break;
        }
    }
    if (protocolIndex == numProtocols) {
        assert(numProtocols < MAX_PROTOCOLS);
        _protocols[protocolIndex].first = name;
        _protocols[protocolIndex].second = nullptr;
        // nullptr may be observed after increment but before protocol pointer
        // update; this is fine as it has the same behavior as if the protocol
        // has not yet been added.
        const auto beforeAdd = _numProtocols.fetch_add(1, std::memory_order_release);
        assert(beforeAdd == numProtocols); // Sanity check for racing inserters
    } else {
        clearPolicyCache();
    }
    _protocols[protocolIndex].second.store(protocol.get(), std::memory_order_release);
    IProtocol::SP prev = _activeProtocols[name];
    _activeProtocols[name] = protocol;
    return prev;
}

IProtocol *
ProtocolRepository::getProtocol(const string &name)
{
    const auto numProtocols = _numProtocols.load(std::memory_order_acquire);
    for (size_t i(0); i < numProtocols; i++) {
        if (_protocols[i].first == name) {
            return _protocols[i].second.load(std::memory_order_acquire);
        }
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
    std::lock_guard guard(_lock);
    RoutingPolicyCache::iterator cit = _routingPolicyCache.find(cacheKey);
    if (cit != _routingPolicyCache.end()) {
        return cit->second;
    }
    ProtocolMap::iterator pit = _activeProtocols.find(protocolName);
    if (pit == _activeProtocols.end()) {
        LOG(error, "Protocol '%s' not supported.", protocolName.c_str());
        return IRoutingPolicy::SP();
    }
    IRoutingPolicy::UP policy;
    try {
        policy = pit->second->createPolicy(policyName, policyParam);
    } catch (const std::exception &e) {
        LOG(error, "Protocol '%s' threw an exception; %s", protocolName.c_str(), e.what());
    }
    if (policy.get() == nullptr) {
        LOG(error, "Protocol '%s' failed to create routing policy '%s' with parameter '%s'.",
            protocolName.c_str(), policyName.c_str(), policyParam.c_str());
        return IRoutingPolicy::SP();
    }
    IRoutingPolicy::SP ret(policy.release());
    _routingPolicyCache[cacheKey] = ret;
    return ret;
}

}
