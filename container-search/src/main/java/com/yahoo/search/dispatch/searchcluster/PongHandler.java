// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.prelude.Pong;

/**
 * Handle the Pong result of a Ping.
 *
 * @author baldersheim
 */
public interface PongHandler {

    void handle(Pong pong);

}
