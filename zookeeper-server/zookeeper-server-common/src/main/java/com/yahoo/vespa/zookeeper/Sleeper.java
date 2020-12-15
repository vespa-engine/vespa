// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.time.Duration;

/**
 * Wrapper around {@link Thread#sleep(long)} that can be overridden in unit tests.
 *
 * @author mpolden
 */
public class Sleeper {

    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

}
