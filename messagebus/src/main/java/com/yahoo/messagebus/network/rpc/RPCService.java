// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.slobrok.api.IMirror;

/**
 * An RPCService represents a set of remote sessions matching a service pattern. The sessions are monitored using the
 * slobrok. If multiple sessions are available, round robin is used to balance load between them.
 *
 * @author havardpe
 */
public interface RPCService {

    static RPCService create(IMirror mirror, String pattern) {
        if (pattern.startsWith("tcp/")) {
            return new TcpRPCService(pattern);
        }
        return new NamedRPCService(mirror, pattern);
    }

    /**
     * Resolve a concrete address from this service. This service may represent multiple remote sessions, so this will
     * select one that is online.
     *
     * @return A concrete service address.
     */
    RPCServiceAddress resolve();

}
