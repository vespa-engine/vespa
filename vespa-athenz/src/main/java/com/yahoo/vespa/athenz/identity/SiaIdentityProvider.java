// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzIdentityCertificate;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.AthenzSslContextBuilder;
import com.yahoo.vespa.athenz.tls.KeyStoreType;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author mortent
 */
public class SiaIdentityProvider implements AthenzIdentityProvider {

    private final AthenzDomain domain;
    private final AthenzService service;
    private final String path;
    private final String trustStorePath;

    public SiaIdentityProvider(SiaProviderConfig siaProviderConfig) {
        this.domain = new AthenzDomain(siaProviderConfig.athenzDomain());
        this.service = new AthenzService(domain, siaProviderConfig.athenzService());
        this.path = siaProviderConfig.keyPathPrefix();
        this.trustStorePath = siaProviderConfig.trustStorePath();
    }

    @Override
    public String getDomain() {
        return domain.getName();
    }

    @Override
    public String getService() {
        return service.getName();
    }

    @Override
    public SSLContext getIdentitySslContext() {
        X509Certificate certificate = Crypto.loadX509Certificate(Paths.get(path, "certs", String.format("%s.%s.cert.pem", getDomain(),getService())).toFile());
        PrivateKey privateKey = Crypto.loadPrivateKey(Paths.get(path, "keys", String.format("%s.%s.key.pem", getDomain(),getService())).toFile());

        return new AthenzSslContextBuilder()
                .withTrustStore(new File(trustStorePath), KeyStoreType.JKS)
                .withIdentityCertificate(new AthenzIdentityCertificate(certificate, privateKey))
                .build();
    }
}
