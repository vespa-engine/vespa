// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model;

import static com.yahoo.config.provision.NetworkPorts.Allocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.logging.Logger;
import java.util.logging.Level;

public class PortFinder {

    private static final Logger log = Logger.getLogger(PortFinder.class.getName());

    private final Map<String, Allocation> byKeys = new HashMap<>();
    private final Map<Integer, Allocation> byPorts = new TreeMap<>();

    /** force add the given allocation, removing any conflicting ones */
    public void use(Allocation allocation) {
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

    public int findPort(Allocation request, String host) {
        String key = request.key();
        if (byKeys.containsKey(key)) {
            int port = byKeys.get(key).port;
            log.log(Level.FINE, () -> "Re-using port "+port+" for allocation " + request + " on " + host);
            return port;
        }
        int port = request.port;
        while (byPorts.containsKey(port)) {
            ++port;
        }
        return port;
    }

    public boolean isFree(int port) {
        return !byPorts.containsKey(port);
    }

    public PortFinder(Collection<Allocation> allocations) {
        for (Allocation a : allocations) {
            use(a);
        }
    }

    public Collection<Allocation> allocations() {
        return byPorts.values();
    }

}
