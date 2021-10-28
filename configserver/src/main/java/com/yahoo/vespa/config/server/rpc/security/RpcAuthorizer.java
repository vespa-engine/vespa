// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc.security;

import com.yahoo.jrt.Request;

import java.util.concurrent.CompletableFuture;

/**
 * Authorization logic for configserver's RPC method
 *
 * @author bjorncs
 */
public interface RpcAuthorizer {

    CompletableFuture<Void> authorizeConfigRequest(Request request);

    CompletableFuture<Void> authorizeFileRequest(Request request);

}
