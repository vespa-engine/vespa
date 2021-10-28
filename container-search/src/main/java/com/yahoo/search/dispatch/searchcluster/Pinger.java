// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

/**
 * Send a ping and ensure that the pong is propagated to the ponghandler.
 * Should not wait as this should be done in parallel on all nodes.
 *
 * @author baldersheim
 */
public interface Pinger {

    void ping();

}
