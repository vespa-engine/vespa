// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.rpc;

import com.yahoo.jrt.*;

import java.util.logging.Logger;


/**
 * A handler that can be used to register RPC function calls,
 * using Vespa JRT. To enable an RPC server, first call addMethod() any number of times,
 * then start().
 */
public class RPCHandler {
    private final static Logger log = Logger.getLogger(RPCHandler.class.getName());

    private final int port;
    private final Supervisor supervisor;
    private Acceptor acceptor;

    public RPCHandler(int port) {
        supervisor = new Supervisor(new Transport());
        this.port = port;
    }

    public void start() {
        try {
            acceptor = supervisor.listen(new Spec(port));
            log.info("Listening for RPC requests on port " + port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addMethod(Method method) {
        supervisor.addMethod(method);
    }

}
