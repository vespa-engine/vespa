// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

/**
 * A ping, typically to ask whether backend is alive.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Ping {

    /** How long to wait for a pong */
    private long timeout;

    public Ping() {
        this(500);
    }

    public Ping(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public String toString() {
        return "Ping(timeout = " + timeout + ")";
    }

}
