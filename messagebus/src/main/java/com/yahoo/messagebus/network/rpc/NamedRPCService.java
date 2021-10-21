// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NamedRPCService implements RPCService {
    private final IMirror mirror;
    private final String pattern;
    private int addressIdx = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    private int addressGen = 0;
    private List<Mirror.Entry> addressList = null;

    /**
     * Create a new RPCService backed by the given network and using the given service pattern.
     *
     * @param mirror  The naming server to send queries to.
     * @param pattern The pattern to use when querying.
     */
    public NamedRPCService(IMirror mirror, String pattern) {
        this.mirror = mirror;
        this.pattern = pattern;
    }

    /**
     * Resolve a concrete address from this service. This service may represent multiple remote sessions, so this will
     * select one that is online.
     *
     * @return A concrete service address.
     */
    public synchronized RPCServiceAddress resolve() {
        if (addressGen != mirror.updates()) {
            addressGen = mirror.updates();
            addressList = mirror.lookup(pattern);
        }
        if (addressList != null && !addressList.isEmpty()) {
            ++addressIdx;
            if (addressIdx >= addressList.size()) {
                addressIdx = 0;
            }
            Mirror.Entry entry = addressList.get(addressIdx);
            return new RPCServiceAddress(entry.getName(), entry.getSpec());
        }
        return null;
    }
}
