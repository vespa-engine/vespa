// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


/**
 * Base class for event proxies, which are used to cache and group
 * events internally.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
abstract class Proxy {
    private long timestamp;
    private String name;

    Proxy(String name) {
        this(name, System.currentTimeMillis());
    }

    Proxy(String name, long timestamp) {
        this.timestamp = timestamp;
        this.name = name;
    }

    long getTimestamp() {
        return timestamp;
    }

    String getName() {
        return name;
    }
}
