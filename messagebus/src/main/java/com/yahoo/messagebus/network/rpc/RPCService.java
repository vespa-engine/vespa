// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.Random;

/**
 * An RPCService represents a set of remote sessions matching a service pattern. The sessions are monitored using the
 * slobrok. If multiple sessions are available, round robin is used to balance load between them.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class RPCService {

    private final IMirror mirror;
    private final String pattern;
    private int addressIdx = new Random().nextInt(Integer.MAX_VALUE);
    private int addressGen = 0;
    private Mirror.Entry[] addressList = null;

    /**
     * Create a new RPCService backed by the given network and using the given service pattern.
     *
     * @param mirror  The naming server to send queries to.
     * @param pattern The pattern to use when querying.
     */
    public RPCService(IMirror mirror, String pattern) {
        this.mirror = mirror;
        this.pattern = pattern;
    }

    /**
     * Resolve a concrete address from this service. This service may represent multiple remote sessions, so this will
     * select one that is online.
     *
     * @return A concrete service address.
     */
    public RPCServiceAddress resolve() {
        if (pattern.startsWith("tcp/")) {
            int pos = pattern.lastIndexOf('/');
            if (pos > 0 && pos < pattern.length() - 1) {
                RPCServiceAddress ret = new RPCServiceAddress(pattern, pattern.substring(0, pos));
                if (!ret.isMalformed()) {
                    return ret;
                }
            }
        } else {
            if (addressGen != mirror.updates()) {
                addressGen = mirror.updates();
                addressList = mirror.lookup(pattern);
            }
            if (addressList != null && addressList.length > 0) {
                addressIdx = ++addressIdx % addressList.length;
                Mirror.Entry entry = addressList[addressIdx];
                return new RPCServiceAddress(entry.getName(), entry.getSpec());
            }
        }
        return null;
    }

    /**
     * Returns the pattern used when querying for the naming server for addresses. This is given at construtor time.
     *
     * @return The service pattern.
     */
    String getPattern() {
        return pattern;
    }
}
