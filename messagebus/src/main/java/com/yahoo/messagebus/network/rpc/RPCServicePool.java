// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class used to reuse services for the same address when sending messages over the rpc network.
 *
 * @author Simon Thoresen Hult
 */
public class RPCServicePool {

    private final RPCNetwork net;
    private final ThreadLocalCache services = new ThreadLocalCache();
    private final int maxSize;

    /**
     * Create a new service pool for the given network.
     *
     * @param net     The underlying RPC network.
     * @param maxSize The max number of services to cache.
     */
    public RPCServicePool(RPCNetwork net, int maxSize) {
        this.net = net;
        this.maxSize = maxSize;
    }

    /**
     * Returns the RPCServiceAddress that corresponds to a given pattern. This reuses the RPCService object for matching
     * pattern so that load balancing is possible on the network level.
     *
     * @param pattern The pattern for the service we require.
     * @return A service address for the given pattern.
     */
    public RPCServiceAddress resolve(String pattern) {
        RPCService service = services.get().get(pattern);
        if (service == null) {
            service = new RPCService(net.getMirror(), pattern);
            services.get().put(pattern, service);
        }
        return service.resolve();
    }

    /**
     * Returns the number of services available in the pool. This number will never exceed the limit given at
     * construction time.
     *
     * @return The current size of this pool.
     */
    public int getSize() {
        return services.get().size();
    }

    /**
     * Returns whether or not there is a service available in the pool the corresponds to the given pattern.
     *
     * @param pattern The pattern to check for.
     * @return True if a corresponding service is in the pool.
     */
    public boolean hasService(String pattern) {
        return services.get().containsKey(pattern);
    }

    private class ThreadLocalCache extends ThreadLocal<ServiceLRUCache> {

        @Override
        protected ServiceLRUCache initialValue() {
            return new ServiceLRUCache();
        }
    }

    private class ServiceLRUCache extends LinkedHashMap<String, RPCService> {

        ServiceLRUCache() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RPCService> entry) {
            return size() > maxSize;
        }
    }
}
