// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;
import static java.util.Collections.singleton;

/**
 * A service that provides method for initially registering the instance and refreshing it.
 *
 * @author bjorncs
 */
class AthenzCredentialsService {
    private final IdentityConfig identityConfig;
    private final ServiceIdentityProvider nodeIdentityProvider;
    private final File trustStoreJks;
    private final String hostname;
    private final InstanceCsrGenerator instanceCsrGenerator;

    AthenzCredentialsService(IdentityConfig identityConfig,
                             ServiceIdentityProvider nodeIdentityProvider,
                             File trustStoreJks,
                             String hostname) {
        this.identityConfig = identityConfig;
        this.nodeIdentityProvider = nodeIdentityProvider;
        this.trustStoreJks = trustStoreJks;
        this.hostname = hostname;
        this.instanceCsrGenerator = new InstanceCsrGenerator(identityConfig.athenzDnsSuffix());
    }

    AthenzCredentials registerInstance() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        IdentityDocumentClient identityDocumentClient = createIdentityDocumentClient(identityConfig, nodeIdentityProvider);
        SignedIdentityDocument document = identityDocumentClient.getTenantIdentityDocument(hostname);
        AthenzService tenantIdentity = new AthenzService(identityConfig.domain(), identityConfig.service());
        Pkcs10Csr csr = instanceCsrGenerator.generateCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                keyPair);

        try (com.yahoo.vespa.athenz.client.zts.ZtsClient ztsClient =
                     new DefaultZtsClient(URI.create(identityConfig.ztsUrl()), nodeIdentityProvider)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.registerInstance(
                            new AthenzService(identityConfig.configserverIdentityName()),
                            tenantIdentity,
                            null,
                            EntityBindingsMapper.toAttestationData(document),
                            true,
                            csr);
            return toAthenzCredentials(instanceIdentity, keyPair, document);
        }
    }

    AthenzCredentials updateCredentials(SignedIdentityDocument document, SSLContext sslContext) {
        AthenzService tenantIdentity = new AthenzService(identityConfig.domain(), identityConfig.service());
        KeyPair newKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        Pkcs10Csr csr = instanceCsrGenerator.generateCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                newKeyPair);

        try (com.yahoo.vespa.athenz.client.zts.ZtsClient ztsClient =
                     new DefaultZtsClient(URI.create(identityConfig.ztsUrl()), tenantIdentity, sslContext)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.refreshInstance(
                            new AthenzService(identityConfig.configserverIdentityName()),
                            tenantIdentity,
                            document.providerUniqueId().asDottedString(),
                            true,
                            csr);
            return toAthenzCredentials(instanceIdentity, newKeyPair, document);
        }
    }

    private AthenzCredentials toAthenzCredentials(InstanceIdentity instanceIdentity,
                                                  KeyPair keyPair,
                                                  SignedIdentityDocument identityDocument) {
        X509Certificate certificate = instanceIdentity.certificate();
        String serviceToken = instanceIdentity.nToken().get().getRawToken();
        SSLContext identitySslContext = createIdentitySslContext(keyPair.getPrivate(), certificate);
        return new AthenzCredentials(serviceToken, certificate, keyPair, identityDocument, identitySslContext);
    }

    private SSLContext createIdentitySslContext(PrivateKey privateKey, X509Certificate certificate) {
        return new SslContextBuilder()
                .withKeyStore(privateKey, certificate)
                .withTrustStore(trustStoreJks, JKS)
                .build();
    }

    private static DefaultIdentityDocumentClient createIdentityDocumentClient(IdentityConfig config,
                                                                              ServiceIdentityProvider nodeIdentityProvider) {
        return new DefaultIdentityDocumentClient(
                URI.create(config.loadBalancerAddress()),
                nodeIdentityProvider,
                new AthenzIdentityVerifier(singleton(new AthenzService(config.configserverIdentityName()))));
    }
}
