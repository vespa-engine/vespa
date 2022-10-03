// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.aws;

import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AwsRole;
import com.yahoo.vespa.athenz.api.AwsTemporaryCredentials;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Retrieve {@link AwsTemporaryCredentials} through {@link ZtsClient}.
 *
 * @author tokle
 */
public class AwsCredentials implements AutoCloseable {

    private final static Duration MIN_EXPIRY = Duration.ofMinutes(5);
    private final AthenzDomain athenzDomain;
    private final AwsRole awsRole;
    private final ZtsClient ztsClient;
    private final String externalId;
    private final boolean close;
    private volatile AwsTemporaryCredentials credentials;

    public AwsCredentials(ZtsClient ztsClient, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(ztsClient, athenzDomain, awsRole, null);
    }

    public AwsCredentials(ZtsClient ztsClient, AthenzDomain athenzDomain, AwsRole awsRole, String externalId) {
        this(ztsClient, athenzDomain, awsRole, externalId, false);
    }

    private AwsCredentials(ZtsClient ztsClient, AthenzDomain athenzDomain, AwsRole awsRole, String externalId, boolean close) {
        this.ztsClient = ztsClient;
        this.athenzDomain = athenzDomain;
        this.awsRole = awsRole;
        this.externalId = externalId;
        this.close = close;
        this.credentials = get();
    }

    public AwsCredentials(URI ztsUrl, ServiceIdentityProvider identityProvider, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(ztsUrl, identityProvider.getIdentitySslContext(), athenzDomain, awsRole);
    }

    public AwsCredentials(URI ztsUrl, SSLContext sslContext, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(ztsUrl, sslContext, athenzDomain, awsRole, null);
    }

    public AwsCredentials(URI ztsUrl, SSLContext sslContext, AthenzDomain athenzDomain, AwsRole awsRole, String externalId) {
        this(new DefaultZtsClient.Builder(ztsUrl).withSslContext(sslContext).build(), athenzDomain, awsRole, externalId, true);
    }

    /**
     * Requests temporary credentials from ZTS or return cached credentials
     */
    public AwsTemporaryCredentials get() {
        if(shouldRefresh(credentials)) {
            this.credentials = ztsClient.getAwsTemporaryCredentials(athenzDomain, awsRole, externalId);
        }
        return credentials;
    }

    /*
     * Checks credential expiration, returns true if it will expire in the next MIN_EXPIRY minutes
     */
    static boolean shouldRefresh(AwsTemporaryCredentials credentials) {
        Instant expiration = Optional.ofNullable(credentials).map(AwsTemporaryCredentials::expiration).orElse(Instant.EPOCH);
        return Duration.between(Instant.now(), expiration).toMinutes() < MIN_EXPIRY.toMinutes();
    }

    @Override
    public void close() {
        if (close) ztsClient.close();
    }

}
