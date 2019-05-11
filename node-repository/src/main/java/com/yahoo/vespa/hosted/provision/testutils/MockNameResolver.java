// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A mock DNS resolver. Can be configured to only answer specific lookups or any lookup.
 *
 * @author mpolden
 */
public class MockNameResolver implements NameResolver {

    private final Map<String, Set<String>> records = new HashMap<>();

    private boolean mockAnyLookup = false;
    private boolean explicitReverseRecords = false;

    public MockNameResolver addReverseRecord(String ipAddress, String hostname) {
        addRecord(ipAddress, hostname);
        return this;
    }

    public MockNameResolver addRecord(String hostname, String... ipAddress) {
        Objects.requireNonNull(hostname, "hostname must be non-null");
        Arrays.stream(ipAddress).forEach(ip -> Objects.requireNonNull(ip, "ipAddress must be non-null"));
        records.putIfAbsent(hostname, new HashSet<>());
        records.get(hostname).addAll(Arrays.asList(ipAddress));
        return this;
    }

    public MockNameResolver removeRecord(String name) {
        records.remove(name);
        return this;
    }

    public MockNameResolver mockAnyLookup() {
        this.mockAnyLookup = true;
        return this;
    }

    /**
     * When true, only records added with {@link MockNameResolver#addReverseRecord(String, String)} are considered by
     * {@link MockNameResolver#getHostname(String)}. Otherwise the latter returns the IP address by reversing the lookup
     * implicitly.
     */
    public MockNameResolver explicitReverseRecords() {
        this.explicitReverseRecords = true;
        return this;
    }

    @Override
    public Set<String> getAllByNameOrThrow(String hostname) {
        if (records.containsKey(hostname)) {
            return records.get(hostname);
        }
        if (mockAnyLookup) {
            Set<String> ipAddresses = Collections.singleton(randomIpAddress());
            records.put(hostname, ipAddresses);
            return ipAddresses;
        }
        throw new RuntimeException(new UnknownHostException("Could not resolve: " + hostname));
    }

    @Override
    public Optional<String> getHostname(String ipAddress) {
        if (!explicitReverseRecords) {
            return records.entrySet().stream()
                          .filter(kv -> kv.getValue().contains(ipAddress))
                          .map(Map.Entry::getKey)
                          .findFirst();
        }
        return Optional.ofNullable(records.get(ipAddress))
                       .flatMap(values -> values.stream().findFirst());
    }

    private static String randomIpAddress() {
        // Generate a random IP in 127/8 (loopback block)
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format("127.%d.%d.%d",
                random.nextInt(1, 255 + 1),
                random.nextInt(1, 255 + 1),
                random.nextInt(1, 255 + 1));
    }

}
