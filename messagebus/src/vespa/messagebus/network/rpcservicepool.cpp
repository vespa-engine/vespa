// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".rpcservicepool");

#include <algorithm>
#include "rpcservicepool.h"
#include "rpcnetwork.h"

namespace mbus {

RPCServicePool::RPCServicePool(RPCNetwork &net, uint32_t maxSize) :
    _net(net),
    _lru(maxSize)
{
    _lru.reserve(maxSize);
    LOG_ASSERT(maxSize > 0);
}

RPCServicePool::~RPCServicePool()
{
}

RPCServiceAddress::UP
RPCServicePool::resolve(const string &pattern)
{
    if (_lru.hasKey(pattern)) {
        return _lru[pattern]->resolve();
    } else {
        RPCService::LP service(new RPCService(_net.getMirror(), pattern));
        _lru[pattern] = service;
        return service->resolve();
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
