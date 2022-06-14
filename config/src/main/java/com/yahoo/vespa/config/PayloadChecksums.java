// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

/**
 * Checksums for config payload, typically 1 for each PayloadChecksum type (md5 and xxhash64).
 * Initialized with empty checksum for each existing type.
 *
 * @author hmusum
 */
public class PayloadChecksums {

    private final Map<PayloadChecksum.Type, PayloadChecksum> checksums = new LinkedHashMap<>();

    private PayloadChecksums() {
        this(false);
    }

    private PayloadChecksums(boolean addEmptyChecksumForAllTypes) {
        if (addEmptyChecksumForAllTypes)
            Arrays.stream(PayloadChecksum.Type.values())
                  .forEach(type -> checksums.put(type, PayloadChecksum.empty(type)));
    }

    public static PayloadChecksums empty() { return new PayloadChecksums(true); }

    public static PayloadChecksums from(PayloadChecksum... checksums) {
        PayloadChecksums payloadChecksums = new PayloadChecksums();
        Arrays.stream(checksums).filter(Objects::nonNull).forEach(payloadChecksums::add);
        return payloadChecksums;
    }

    public static PayloadChecksums from(String configMd5, String configXxhash64) {
        return new PayloadChecksums()
                .add(new PayloadChecksum(configMd5, MD5))
                .add(new PayloadChecksum(configXxhash64, XXHASH64));
    }

    public static PayloadChecksums fromPayload(Payload payload) {
        return new PayloadChecksums()
                .add(new PayloadChecksum(ConfigUtils.getMd5(payload.getData()), MD5))
                .add(new PayloadChecksum(ConfigUtils.getXxhash64(payload.getData()), XXHASH64));
    }

    private PayloadChecksums add(PayloadChecksum checksum) {
        checksums.put(checksum.type(), checksum);
        return this;
    }

    public void removeChecksumsOfType(PayloadChecksum.Type type) { checksums.remove(type); }

    public PayloadChecksum getForType(PayloadChecksum.Type type) {
        return checksums.get(type);
    }

    public boolean valid() {
        return checksums.values().stream().allMatch(PayloadChecksum::valid);
    }

    public boolean isEmpty() { return this.equals(empty()); }

    public boolean matches(PayloadChecksums other) {
        if (getForType(XXHASH64) != null) return getForType(XXHASH64).equals(other.getForType(XXHASH64));
        if (getForType(MD5) != null) return getForType(MD5).equals(other.getForType(MD5));
        return true;
    }

    @Override
    public String toString() {
        return checksums.values().stream()
                        .map(checksum -> checksum.type().name() + ":" + checksum.asString())
                        .collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayloadChecksums that = (PayloadChecksums) o;
        return Objects.equals(checksums, that.checksums);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checksums);
    }

}
