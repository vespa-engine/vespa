// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import java.time.Instant;
import java.util.Objects;

/**
 * @author mortent
 */
public class TrustStoreItem {
    private final String fingerprint;
    private final Instant expiry;

    public TrustStoreItem(String fingerprint, Instant expiry) {
        this.fingerprint = fingerprint;
        this.expiry = expiry;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Instant expiry() {
        return expiry;
    }

    @Override
    public String toString() {
        return "TrustStoreItem{" +
               "fingerprint='" + fingerprint + '\'' +
               ", expiry=" + expiry +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrustStoreItem that = (TrustStoreItem) o;
        return Objects.equals(fingerprint, that.fingerprint) && Objects.equals(expiry, that.expiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint, expiry);
    }
}
