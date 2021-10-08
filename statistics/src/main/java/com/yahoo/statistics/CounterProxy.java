// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


/**
 * To be able to cache events concerning Counters internally, group them
 * together and similar.
 *
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class CounterProxy extends Proxy {
    private long raw;
    private boolean hasRaw = false;

    CounterProxy(String name) {
        super(name);
    }

    boolean hasRaw() {
        return hasRaw;
    }
    long getRaw() {
        return raw;
    }
    void setRaw(long raw) {
        hasRaw = true;
        this.raw = raw;
    }

}

