// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

/**
 * A store of certificates. An implementation can be plugged in to provide certificates to components who use it.
 *
 * @author bratseth
 */
public interface CertificateStore {

    /** Returns a certificate for a given appid, using the default TTL and retry time */
    default String getCertificate(String appid) { return getCertificate(appid, 0L, 0L); }

    /** Returns a certificate for a given appid, using a TTL and default retry time */
    default String getCertificate(String appid, long ttl) { return getCertificate(appid, ttl, 0L); }

    /**
     * Returns a certificate for a given appid, using a TTL and default retry time
     *
     * @param ttl certificate TTL in ms. Use the default TTL if set to 0
     * @param retry if no certificate is found, allow access to cert DB again in
     *              "retry" ms. Use the default retry time if set to 0.
     */
    String getCertificate(String appid, long ttl, long retry);

}
