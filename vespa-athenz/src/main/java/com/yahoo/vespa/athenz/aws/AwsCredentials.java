// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class AwsCredentials {

    private final static Duration MIN_EXPIRY = Duration.ofMinutes(5);
    private final AthenzDomain athenzDomain;
    private final AwsRole awsRole;
    private final ZtsClient ztsClient;
    private volatile AwsTemporaryCredentials credentials;

    public AwsCredentials(ZtsClient ztsClient, AthenzDomain athenzDomain, AwsRole awsRole) {
        this.ztsClient = ztsClient;
        this.athenzDomain = athenzDomain;
        this.awsRole = awsRole;
        this.credentials = get();
    }

    public AwsCredentials(URI ztsUrl, ServiceIdentityProvider identityProvider, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(new DefaultZtsClient.Builder(ztsUrl).withIdentityProvider(identityProvider).build(), athenzDomain, awsRole);
    }

    public AwsCredentials(URI ztsUrl, SSLContext sslContext, AthenzDomain athenzDomain, AwsRole awsRole) {
        this(new DefaultZtsClient.Builder(ztsUrl).withSslContext(sslContext).build(), athenzDomain, awsRole);
    }

    /**
     * Requests temporary credentials from ZTS or return cached credentials
     */
    public AwsTemporaryCredentials get() {
        if(shouldRefresh(credentials)) {
            this.credentials = ztsClient.getAwsTemporaryCredentials(athenzDomain, awsRole);
        }
        return credentials;
    }

    /*
     * Checks credential expiration, returns true if it will expipre in the next MIN_EXPIRY minutes
     */
    static boolean shouldRefresh(AwsTemporaryCredentials credentials) {
        Instant expiration = Optional.ofNullable(credentials).map(AwsTemporaryCredentials::expiration).orElse(Instant.EPOCH);
        return Duration.between(Instant.now(), expiration).toMinutes() < MIN_EXPIRY.toMinutes();
    }

}
