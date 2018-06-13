// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static com.yahoo.vespa.athenz.tls.KeyStoreType.JKS;
import static java.util.Collections.singleton;

/**
 * A service that provides method for initially registering the instance and refreshing it.
 *
 * @author bjorncs
 */
class AthenzCredentialsService {
    private static final Duration EXPIRATION_MARGIN = Duration.ofDays(2);
    private static final Path VESPA_SIA_DIRECTORY = Paths.get(Defaults.getDefaults().underVespaHome("var/vespa/sia"));
    private static final Path IDENTITY_DOCUMENT_FILE = VESPA_SIA_DIRECTORY.resolve("vespa-tenant-identity-document.json");

    private final AthenzService tenantIdentity;
    private final URI configserverEndpoint;
    private final URI ztsEndpoint;
    private final AthenzService configserverIdentity;
    private final ServiceIdentityProvider nodeIdentityProvider;
    private final File trustStoreJks;
    private final String hostname;
    private final InstanceCsrGenerator instanceCsrGenerator;
    private final Clock clock;

    AthenzCredentialsService(IdentityConfig identityConfig,
                             ServiceIdentityProvider nodeIdentityProvider,
                             File trustStoreJks,
                             String hostname,
                             Clock clock) {
        this.tenantIdentity = new AthenzService(identityConfig.domain(), identityConfig.service());
        this.configserverEndpoint = URI.create(identityConfig.loadBalancerAddress());
        this.ztsEndpoint = URI.create(identityConfig.ztsUrl());
        this.configserverIdentity = new AthenzService(identityConfig.configserverIdentityName());
        this.nodeIdentityProvider = nodeIdentityProvider;
        this.trustStoreJks = trustStoreJks;
        this.hostname = hostname;
        this.instanceCsrGenerator = new InstanceCsrGenerator(identityConfig.athenzDnsSuffix());
        this.clock = clock;
    }

    AthenzCredentials registerInstance() {
        Optional<AthenzCredentials> athenzCredentialsFromDisk = tryReadCredentialsFromDisk();
        if (athenzCredentialsFromDisk.isPresent()) {
            return athenzCredentialsFromDisk.get();
        }
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        IdentityDocumentClient identityDocumentClient = createIdentityDocumentClient();
        SignedIdentityDocument document = identityDocumentClient.getTenantIdentityDocument(hostname);
        Pkcs10Csr csr = instanceCsrGenerator.generateCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                keyPair);

        try (ZtsClient ztsClient = new DefaultZtsClient(ztsEndpoint, nodeIdentityProvider)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.registerInstance(
                            configserverIdentity,
                            tenantIdentity,
                            null,
                            EntityBindingsMapper.toAttestationData(document),
                            false,
                            csr);
            X509Certificate certificate = instanceIdentity.certificate();
            SSLContext identitySslContext = createIdentitySslContext(keyPair.getPrivate(), certificate);
            writeCredentialsToDisk(keyPair.getPrivate(), certificate, document);
            return new AthenzCredentials(certificate, keyPair, document, identitySslContext);
        }
    }

    AthenzCredentials updateCredentials(SignedIdentityDocument document, SSLContext sslContext) {
        KeyPair newKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        Pkcs10Csr csr = instanceCsrGenerator.generateCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                newKeyPair);

        try (ZtsClient ztsClient = new DefaultZtsClient(ztsEndpoint, tenantIdentity, sslContext)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.refreshInstance(
                            configserverIdentity,
                            tenantIdentity,
                            document.providerUniqueId().asDottedString(),
                            false,
                            csr);
            X509Certificate certificate = instanceIdentity.certificate();
            SSLContext identitySslContext = createIdentitySslContext(newKeyPair.getPrivate(), certificate);
            writeCredentialsToDisk(newKeyPair.getPrivate(), certificate, document);
            return new AthenzCredentials(certificate, newKeyPair, document, identitySslContext);
        }
    }

    private Optional<AthenzCredentials> tryReadCredentialsFromDisk() {
        Optional<PrivateKey> privateKey = SiaUtils.readPrivateKeyFile(VESPA_SIA_DIRECTORY, tenantIdentity);
        if (!privateKey.isPresent()) return Optional.empty();
        Optional<X509Certificate> certificate = SiaUtils.readCertificateFile(VESPA_SIA_DIRECTORY, tenantIdentity);
        if (!certificate.isPresent()) return Optional.empty();
        if (isExpired(certificate.get())) {
            return Optional.empty();
        }
        if (Files.notExists(IDENTITY_DOCUMENT_FILE)) return Optional.empty();
        SignedIdentityDocument signedIdentityDocument = EntityBindingsMapper.readSignedIdentityDocumentFromFile(IDENTITY_DOCUMENT_FILE);
        KeyPair keyPair = new KeyPair(KeyUtils.extractPublicKey(privateKey.get()), privateKey.get());
        SSLContext sslContext = createIdentitySslContext(privateKey.get(), certificate.get());
        return Optional.of(new AthenzCredentials(certificate.get(), keyPair, signedIdentityDocument, sslContext));
    }

    private boolean isExpired(X509Certificate certificate) {
        return clock.instant().isAfter(certificate.getNotAfter().toInstant().minus(EXPIRATION_MARGIN));
    }

    private void writeCredentialsToDisk(PrivateKey privateKey,
                                       X509Certificate certificate,
                                       SignedIdentityDocument identityDocument) {
        SiaUtils.writePrivateKeyFile(VESPA_SIA_DIRECTORY, tenantIdentity, privateKey);
        SiaUtils.writeCertificateFile(VESPA_SIA_DIRECTORY, tenantIdentity, certificate);
        EntityBindingsMapper.writeSignedIdentityDocumentToFile(IDENTITY_DOCUMENT_FILE, identityDocument);
    }

    private SSLContext createIdentitySslContext(PrivateKey privateKey, X509Certificate certificate) {
        return new SslContextBuilder()
                .withKeyStore(privateKey, certificate)
                .withTrustStore(trustStoreJks, JKS)
                .build();
    }

    private DefaultIdentityDocumentClient createIdentityDocumentClient() {
        return new DefaultIdentityDocumentClient(
                configserverEndpoint,
                nodeIdentityProvider,
                new AthenzIdentityVerifier(singleton(configserverIdentity)));
    }
}
