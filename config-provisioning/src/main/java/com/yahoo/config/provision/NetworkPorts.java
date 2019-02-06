
package com.yahoo.config.provision;

import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map;
import java.util.Optional;
import com.google.common.collect.ImmutableMap;

/**
 * Contains information about a port (port number and a collection of tags).
 *
 */
public class NetworkPorts {

    public static Optional<NetworkPorts> empty() { return Optional.empty(); }

    public static class Allocation {
        public final int port;
        public final String serviceType;
        public final String configId;
        public final String portSuffix;

        public Allocation(int port, String serviceType, String configId, String portSuffix) {
            this.port = port;
            this.serviceType = serviceType;
            this.configId = configId;
            this.portSuffix = portSuffix;
        }
        public String key() {
            StringBuilder buf = new StringBuilder();
            buf.append("t=").append(serviceType);
            buf.append(" cfg=").append(configId);
            buf.append(" suf=").append(portSuffix);
            return buf.toString();
        }
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("[port=").append(port);
            buf.append(" serviceType=").append(serviceType);
            buf.append(" configId=").append(configId);
            buf.append(" suffix=").append(portSuffix);
            buf.append("]");
            return buf.toString();
        }
    }

    private Map<Integer, Allocation> byPorts = new TreeMap<>();
    private Map<String, Allocation> byKeys = new HashMap<>();

    public Map<Integer, Allocation> byPortMap() {
        return ImmutableMap.copyOf(byPorts);
    }
    public Map<String, Allocation> byKeyMap() {
        return ImmutableMap.copyOf(byKeys);
    }
    public Collection<Allocation> allocations() {
        return ImmutableMap.copyOf(byPorts).values();
    }

    public NetworkPorts() {}

    /** allocate a port for a service; fail on conflict */
    public void add(Allocation allocation) {
        String key = allocation.key();
        if (byKeys.containsKey(key)) {
            Allocation oldAlloc = byKeys.get(key);
            int oldPort = oldAlloc.port;
            if (allocation.port == oldPort) {
                return; // already OK
            }
            throw new IllegalArgumentException("cannot add allocation "+allocation+" because it already uses port "+oldPort);
        }
        if (byPorts.containsKey(allocation.port)) {
            Allocation oldAlloc = byPorts.get(allocation.port);
            throw new IllegalArgumentException("cannot add allocation "+allocation+" because port is already in use by "+oldAlloc);
        }
        byPorts.put(allocation.port, allocation);
        byKeys.put(key, allocation);
    }

    /** force add the given allocation, removing any conflicting ones */
    public void override(Allocation allocation) {
        String key = allocation.key();
        if (byKeys.containsKey(key)) {
            if (byKeys.get(key).port == allocation.port) {
                return; // already OK
            }
            Allocation toRemove = byKeys.remove(key);
            byPorts.remove(toRemove.port);
        }
        if (byPorts.containsKey(allocation.port)) {
            Allocation toRemove = byPorts.remove(allocation.port);
            byKeys.remove(toRemove.key());
        }
        byPorts.put(allocation.port, allocation);
        byKeys.put(key, allocation);
    }

}
