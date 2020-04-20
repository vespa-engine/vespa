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
