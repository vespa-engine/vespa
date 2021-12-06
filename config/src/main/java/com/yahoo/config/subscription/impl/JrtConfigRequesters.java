// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.vespa.config.TimingValues;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps track of requesters per config subscriber
 *
 * @author hmusum
 */
public class JrtConfigRequesters {

    /**
     * Reuse requesters for equal source sets, limit number if many subscriptions.
     */
    protected Map<ConfigSourceSet, JRTConfigRequester> requesters = new HashMap<>();

    public JRTConfigRequester getRequester(ConfigSourceSet source, TimingValues timingValues) {
        JRTConfigRequester requester = requesters.get(source);
        if (requester == null) {
            requester = JRTConfigRequester.create(source, timingValues);
            requesters.put(source, requester);
        }
        return requester;
    }

    /**
     * Closes all open requesters
     */
    public void close() {
        requesters.values().forEach(JRTConfigRequester::close);
    }

}
