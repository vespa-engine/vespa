// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.jrt.Request;

import java.util.concurrent.CompletableFuture;

/**
 * An {@link RpcAuthorizer} that denies all file RPC requests.
 *
 * @author hmusum
 */
public class DenyFileRequestsAuthorizer implements RpcAuthorizer {

    @Override
    public CompletableFuture<Void> authorizeConfigRequest(Request request) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> authorizeFileRequest(Request request) {
        return CompletableFuture.failedFuture(new SecurityException("File request '" + request.methodName() + "' is denied"));
    }
}
