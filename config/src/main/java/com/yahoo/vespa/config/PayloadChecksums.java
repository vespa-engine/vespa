// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.vespa.config.protocol.Payload;

import java.util.Objects;

/**
 * Checksums for config payload, only xxhash64 at the moment.
 *
 * @author hmusum
 */
public class PayloadChecksums {

    private final PayloadChecksum xxhash64;

    private PayloadChecksums(PayloadChecksum xxhash64) {
        this.xxhash64 = Objects.requireNonNull(xxhash64);
    }

    public static PayloadChecksums empty() { return new PayloadChecksums(PayloadChecksum.empty()); }

    public static PayloadChecksums from(PayloadChecksum xxhash64) {
        return new PayloadChecksums(xxhash64);
    }

    public static PayloadChecksums from(String xxhash64) {
        return new PayloadChecksums(new PayloadChecksum(xxhash64));
    }

    public static PayloadChecksums fromPayload(Payload payload) {
        return new PayloadChecksums(PayloadChecksum.fromPayload(payload));
    }

    public PayloadChecksum xxhash64() { return xxhash64; }

    public boolean valid() { return xxhash64.valid(); }

    public boolean isEmpty() { return xxhash64.isEmpty(); }

    public boolean matches(PayloadChecksums other) { return xxhash64.equals(other.xxhash64); }

    @Override
    public String toString() {
        return xxhash64.isEmpty() ? "" : xxhash64.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayloadChecksums that = (PayloadChecksums) o;
        return Objects.equals(xxhash64, that.xxhash64);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xxhash64);
    }

}
