// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import java.util.concurrent.DelayQueue;

/**
 * Queue for requests that have no corresponding config in cache and which we are awaiting response from server for
 *
 * @author hmusum
 */
class DelayedResponses {

    private final DelayQueue<DelayedResponse> delayedResponses = new DelayQueue<>();

    void add(DelayedResponse response) {
        delayedResponses.add(response);
    }

    boolean remove(DelayedResponse response) {
        return delayedResponses.remove(response);
    }

    DelayQueue<DelayedResponse> responses() {
        return delayedResponses;
    }

    int size() {
        return responses().size();
    }

}
