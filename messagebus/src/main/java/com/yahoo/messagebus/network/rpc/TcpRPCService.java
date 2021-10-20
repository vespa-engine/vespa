// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

public class TcpRPCService implements RPCService {
    private final RPCServiceAddress blueprint;

    TcpRPCService(String pattern) {
        if ( ! pattern.startsWith("tcp/")) {
            throw new IllegalArgumentException("Expect tcp adress to start with 'tcp/', was: " + pattern);
        }
        RPCServiceAddress ret = null;
        int pos = pattern.lastIndexOf('/');
        if (pos > 0 && pos < pattern.length() - 1) {
            ret = new RPCServiceAddress(pattern, pattern.substring(0, pos));
            if ( ret.isMalformed()) {
                ret = null;
            }
        }
        blueprint = ret;
    }
    public RPCServiceAddress resolve() {
        return blueprint != null ? new RPCServiceAddress(blueprint) : null;
    }
}
