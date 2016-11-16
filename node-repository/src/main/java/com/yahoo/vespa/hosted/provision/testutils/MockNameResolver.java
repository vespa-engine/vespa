// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A mock DNS resolver. Can be configured to only answer specific lookups or any lookup.
 *
 * @author mpolden
 */
public class MockNameResolver implements NameResolver {

    private boolean allowInvocation = true;
    private boolean mockAnyLookup = false;
    private final Map<String, String> records = new HashMap<>();

    public MockNameResolver addRecord(String hostname, String ipAddress) {
        records.put(hostname, ipAddress);
        return this;
    }

    public MockNameResolver reset() {
        this.allowInvocation = true;
        this.mockAnyLookup = false;
        this.records.clear();
        return this;
    }

    public MockNameResolver failIfInvoked() {
        this.allowInvocation = false;
        return this;
    }

    public MockNameResolver mockAnyLookup() {
        this.mockAnyLookup = true;
        return this;
    }

    @Override
    public String getByNameOrThrow(String hostname) {
        if (!allowInvocation) {
            throw new IllegalStateException("Expected getByName to not be invoked for hostname: " + hostname);
        }
        if (mockAnyLookup) {
            return UUID.randomUUID().toString();
        }
        if (records.containsKey(hostname)) {
            return records.get(hostname);
        }
        throw new RuntimeException(new UnknownHostException("Could not resolve: " + hostname));
    }
}
