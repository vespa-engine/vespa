// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.provision.node;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

import java.time.Instant;
import java.util.Objects;

/**
 * Contains the fingerprint and expiry of certificates in a hosts truststore.
 *
 * @author mortent
 */
public class TrustStoreItem {
    private static final String FINGERPRINT_FIELD = "fingerprint";
    private static final String EXPIRY_FIELD = "expiry";

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

    public void toSlime(Cursor trustedCertificatesRoot) {
        Cursor object = trustedCertificatesRoot.addObject();
        object.setString(FINGERPRINT_FIELD, fingerprint);
        object.setLong(EXPIRY_FIELD, expiry.toEpochMilli());
    }

    public static TrustStoreItem fromSlime(Inspector inspector) {
        String fingerprint = inspector.field(FINGERPRINT_FIELD).asString();
        Instant expiry = Instant.ofEpochMilli(inspector.field(EXPIRY_FIELD).asLong());
        return new TrustStoreItem(fingerprint, expiry);
    }

    @Override
    public String toString() {
        return "TrustedCertificate{" +
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
