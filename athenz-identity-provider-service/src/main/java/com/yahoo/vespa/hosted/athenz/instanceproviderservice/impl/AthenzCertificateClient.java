// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.athenz.auth.impl.PrincipalAuthority;
import com.yahoo.athenz.auth.impl.SimpleServiceIdentityProvider;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.zts.InstanceRefreshRequest;
import com.yahoo.athenz.zts.ZTSClient;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;

import javax.net.ssl.SSLContext;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.concurrent.TimeUnit;

/**
 * @author bjorncs
 */
public class AthenzCertificateClient {

    private final AthenzProviderServiceConfig config;
    private final AthenzProviderServiceConfig.Zones zoneConfig;
    private final AthenzIdentityProvider bootstrapIdentity;

    public AthenzCertificateClient(AthenzIdentityProvider bootstrapIdentity,
                                   AthenzProviderServiceConfig config,
                                   AthenzProviderServiceConfig.Zones zoneConfig) {
        this.bootstrapIdentity = bootstrapIdentity;
        this.config = config;
        this.zoneConfig = zoneConfig;
    }

    public X509Certificate updateCertificate(PrivateKey privateKey) {
        SSLContext bootstrapSslContext = bootstrapIdentity.getIdentitySslContext();
        ZTSClient ztsClient = new ZTSClient(config.ztsUrl(), bootstrapSslContext);
        InstanceRefreshRequest req =
                ZTSClient.generateInstanceRefreshRequest(
                        zoneConfig.domain(), zoneConfig.serviceName(), privateKey, zoneConfig.certDnsSuffix(), /*expiryTime*/0);
        req.setKeyId(Integer.toString(zoneConfig.secretVersion()));
        String pemEncoded = ztsClient.postInstanceRefreshRequest(zoneConfig.domain(), zoneConfig.serviceName(), req)
                .getCertificate();
        return Crypto.loadX509Certificate(pemEncoded);
    }

}
