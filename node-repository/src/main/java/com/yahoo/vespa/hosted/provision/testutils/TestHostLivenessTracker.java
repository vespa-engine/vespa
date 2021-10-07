// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.HostLivenessTracker;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** This is a fully functional implementation */
public class TestHostLivenessTracker implements HostLivenessTracker {

    private final Clock clock;
    private final Map<String, Instant> lastRequestFromHost = new HashMap<>();

    public TestHostLivenessTracker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void receivedRequestFrom(String hostname) {
        lastRequestFromHost.put(hostname, clock.instant());
    }

    @Override
    public Optional<Instant> lastRequestFrom(String hostname) {
        return Optional.ofNullable(lastRequestFromHost.get(hostname));
    }

}
