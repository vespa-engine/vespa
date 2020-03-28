// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcservicepool.h"
#include "rpcnetwork.h"
#include <vespa/vespalib/stllike/lrucache_map.hpp>

namespace mbus {

RPCServicePool::RPCServicePool(RPCNetwork &net, uint32_t maxSize) :
    _net(net),
    _lru(maxSize)
{
    _lru.reserve(maxSize);
    assert(maxSize > 0);
}

RPCServicePool::~RPCServicePool() = default;

RPCServiceAddress::UP
RPCServicePool::resolve(const string &pattern)
{
    if (_lru.hasKey(pattern)) {
        return _lru[pattern]->resolve();
    } else {
        auto service = std::make_unique<RPCService>(_net.getMirror(), pattern);
        auto result = service->resolve();
        _lru[pattern] = std::move(service);
        return result;
    }
}

uint32_t
RPCServicePool::getSize() const
{
    return _lru.size();
}

bool
RPCServicePool::hasService(const string &pattern) const
{
    return _lru.hasKey(pattern);
}

} // namespace mbus
