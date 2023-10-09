// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class SingleRequestWaiter implements RequestWaiter {

    private boolean done = false;

    public synchronized void handleRequestDone(Request req) {
        done = true;
        notify();
    }

    public synchronized void waitDone() {
        while (!done) {
            try { wait(); } catch (InterruptedException e) {}
        }
    }
}
