// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcservicepool.h"
#include "rpcnetwork.h"
#include <vespa/vespalib/stllike/lrucache_map.hpp>

namespace mbus {

RPCServicePool::RPCServicePool(const slobrok::api::IMirrorAPI & mirror, uint32_t maxSize) :
    _mirror(mirror),
    _lock(),
    _lru(std::make_unique<ServiceCache>(maxSize)),
    _updateGen(0),
    _maxSize(maxSize)
{
    _lru->reserve(maxSize);
    assert(maxSize > 0);
}

RPCServicePool::~RPCServicePool() = default;

RPCServiceAddress::UP
RPCServicePool::resolve(const string &pattern)
{
    std::shared_ptr<RPCService> service;
    {
        LockGuard guard(_lock);
        handleMirrorUpdates(guard);
        std::shared_ptr<RPCService> *found = _lru->findAndRef(pattern);
        if (found) {
            service = *found;
        }
    }

    if (service) {
        return service->resolve();
    } else {
        service = std::make_shared<RPCService>(_mirror, pattern);
        auto result = service->resolve();
        if (service->isValid()) {
            LockGuard guard(_lock);
            (*_lru)[pattern] = std::move(service);
        }
        return result;
    }

}

void
RPCServicePool::handleMirrorUpdates(const LockGuard &) {
    uint32_t currentgen = _mirror.updates();
    if (_updateGen != currentgen) {
        auto lru = std::make_unique<ServiceCache>(_maxSize);
        _lru.swap(lru);
        _updateGen = currentgen;
    }
}

uint32_t
RPCServicePool::getSize() const
{
    LockGuard guard(_lock);
    return _lru->size();
}

bool
RPCServicePool::hasService(const string &pattern) const
{
    LockGuard guard(_lock);
    return _lru->hasKey(pattern);
}

} // namespace mbus
