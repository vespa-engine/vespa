// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

/**
 * Creates gateway connections on request
 *
 * @author bratseth
 */
public interface GatewayConnectionFactory {

    GatewayConnection newConnection();

}
