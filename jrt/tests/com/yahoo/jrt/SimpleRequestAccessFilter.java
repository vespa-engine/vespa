package com.yahoo.jrt;// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @author bjorncs
 */
class SimpleRequestAccessFilter implements RequestAccessFilter {
    volatile boolean invoked = false, allowed = true;
    @Override public boolean allow(Request r) { invoked = true; return allowed; }
}
