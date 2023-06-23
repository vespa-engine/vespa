// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
        records.computeIfAbsent(hostname, (k) -> new HashSet<>())
               .addAll(Arrays.asList(ipAddress));
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
     * {@link MockNameResolver#resolveHostname(String)}. Otherwise the latter returns the IP address by reversing the lookup
     * implicitly.
     */
    public MockNameResolver explicitReverseRecords() {
        this.explicitReverseRecords = true;
        return this;
    }

    @Override
    public Set<String> resolveAll(String name) {
        if (records.containsKey(name)) {
            return records.get(name);
        }
        if (mockAnyLookup) {
            records.computeIfAbsent(name, (k) -> new HashSet<>())
                   .add(randomIpAddress());
            return records.get(name);
        }
        throw new RuntimeException(new UnknownHostException("Could not resolve: " + name));
    }

    @Override
    public Set<String> resolve(String name, RecordType first, RecordType... rest) {
        var types = EnumSet.of(first, rest);

        return resolveAll(name)
                .stream()
                .filter(addressString -> {
                    if (types.contains(RecordType.A) && IP.isV4(addressString)) return true;
                    if (types.contains(RecordType.AAAA) && IP.isV6(addressString)) return true;
                    return false;
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<String> resolveHostname(String ipAddress) {
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
