// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpcservice.h"
#include <vespa/vespalib/stllike/lrucache_map.h>

namespace mbus {

class RPCNetwork;

/**
 * Class used to reuse services for the same pattern when sending messages over
 * the rpc network.
 */
class RPCServicePool {
public:
    RPCServicePool(const RPCServicePool &) = delete;
    RPCServicePool & operator = (const RPCServicePool &) = delete;
    /**
     * Create a new service pool for the given network.
     *
     * @param net     The underlying RPC network.
     * @param maxSize The max number of services to cache.
     */
    RPCServicePool(const slobrok::api::IMirrorAPI & mirror, uint32_t maxSize);

    /**
     * Destructor. Frees any allocated resources.
     */
    ~RPCServicePool();

    /**
     * Returns the RPCServiceAddress that corresponds to a given pattern. This
     * reuses the RPCService object for matching pattern so that load balancing
     * is possible on the network level.
     *
     * @param pattern The pattern for the service we require.
     * @return A service address for the given pattern.
     */
    RPCServiceAddress::UP resolve(const string &pattern);

    /**
     * Returns the number of services available in the pool. This number will
     * never exceed the limit given at construction time.
     *
     * @return The current size of this pool.
     */
    uint32_t getSize() const;

    /**
     * Returns whether or not there is a service available in the pool the
     * corresponds to the given pattern.
     *
     * @param pattern The pattern to check for.
     * @return True if a corresponding service is in the pool.
     */
    bool hasService(const string &pattern) const;
private:
    using ServiceCache = vespalib::lrucache_map< vespalib::LruParam<string, std::shared_ptr<RPCService> >>;
    using LockGuard = std::lock_guard<std::mutex>;

    void handleMirrorUpdates(const LockGuard & guard);

    const slobrok::api::IMirrorAPI & _mirror;
    mutable std::mutex               _lock;
    std::unique_ptr<ServiceCache>    _lru;
    uint32_t                         _updateGen;
    uint32_t                         _maxSize;
};

} // namespace mbus

