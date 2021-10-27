// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.jrt.Request;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link RpcAuthorizer} that allow all RPC requests.
 *
 * @author bjorncs
 */
public class NoopRpcAuthorizer implements RpcAuthorizer {

    @Override
    public CompletableFuture<Void> authorizeConfigRequest(Request request) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> authorizeFileRequest(Request request) {
        return CompletableFuture.completedFuture(null);
    }
}
