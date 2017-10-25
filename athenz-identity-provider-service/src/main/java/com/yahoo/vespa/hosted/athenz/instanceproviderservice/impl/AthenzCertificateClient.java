// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.athenz.auth.impl.PrincipalAuthority;
import com.yahoo.athenz.auth.impl.SimpleServiceIdentityProvider;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zts.InstanceRefreshRequest;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.TimeUnit;

/**
 * @author bjorncs
 */
public class AthenzCertificateClient implements CertificateClient {

    private final AthenzProviderServiceConfig config;
    private final AthenzPrincipalAuthority authority;

    public AthenzCertificateClient(AthenzProviderServiceConfig config) {
        this.config = config;
        this.authority = new AthenzPrincipalAuthority(config.athenzPrincipalHeaderName());
    }

    @Override
    public X509Certificate updateCertificate(PrivateKey privateKey, TemporalAmount expiryTime) {
        SimpleServiceIdentityProvider identityProvider = new SimpleServiceIdentityProvider(
                authority, config.domain(), config.serviceName(),
                privateKey, Integer.toString(config.keyVersion()), TimeUnit.MINUTES.toSeconds(10));
        ZTSClient ztsClient = new ZTSClient(
                config.ztsUrl(), config.domain(), config.serviceName(), identityProvider);
        InstanceRefreshRequest req =
                ZTSClient.generateInstanceRefreshRequest(
                        config.domain(), config.serviceName(), privateKey,
                        config.certDnsSuffix(), (int)expiryTime.get(ChronoUnit.SECONDS));
        String pemEncoded = ztsClient.postInstanceRefreshRequest(config.domain(), config.serviceName(), req)
                .getCertificate();
        return Crypto.loadX509Certificate(pemEncoded);
    }

    private static class AthenzPrincipalAuthority extends PrincipalAuthority {
        private final String headerName;

        public AthenzPrincipalAuthority(String headerName) {
            this.headerName = headerName;
        }

        @Override
        public String getHeader() {
            return headerName;
        }
    }

}
