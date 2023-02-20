// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identityprovider.client;

import com.yahoo.container.core.identity.IdentityConfig;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.defaults.Defaults;

import javax.net.ssl.SSLContext;
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
    private final String hostname;
    private final CsrGenerator csrGenerator;
    private final Clock clock;

    AthenzCredentialsService(IdentityConfig identityConfig,
                             ServiceIdentityProvider nodeIdentityProvider,
                             String hostname,
                             Clock clock) {
        this.tenantIdentity = new AthenzService(identityConfig.domain(), identityConfig.service());
        this.configserverEndpoint = URI.create("https://" + identityConfig.loadBalancerAddress() + ":4443");
        this.ztsEndpoint = URI.create(identityConfig.ztsUrl());
        this.configserverIdentity = new AthenzService(identityConfig.configserverIdentityName());
        this.nodeIdentityProvider = nodeIdentityProvider;
        this.hostname = hostname;
        this.csrGenerator = new CsrGenerator(identityConfig.athenzDnsSuffix(), identityConfig.configserverIdentityName());
        this.clock = clock;
    }

    Path certificatePath() { return SiaUtils.getCertificateFile(VESPA_SIA_DIRECTORY, tenantIdentity); }
    Path privateKeyPath() { return SiaUtils.getPrivateKeyFile(VESPA_SIA_DIRECTORY, tenantIdentity); }

    AthenzCredentials registerInstance() {
        Optional<AthenzCredentials> athenzCredentialsFromDisk = tryReadCredentialsFromDisk();
        if (athenzCredentialsFromDisk.isPresent()) {
            return athenzCredentialsFromDisk.get();
        }
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        IdentityDocumentClient identityDocumentClient = createIdentityDocumentClient();
        SignedIdentityDocument document = identityDocumentClient.getTenantIdentityDocument(hostname);
        Pkcs10Csr csr = csrGenerator.generateInstanceCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                document.clusterType(),
                keyPair);

        try (ZtsClient ztsClient = new DefaultZtsClient.Builder(ztsEndpoint).withIdentityProvider(nodeIdentityProvider).build()) {
            InstanceIdentity instanceIdentity =
                    ztsClient.registerInstance(
                            configserverIdentity,
                            tenantIdentity,
                            EntityBindingsMapper.toAttestationData(document),
                            csr);
            X509Certificate certificate = instanceIdentity.certificate();
            writeCredentialsToDisk(keyPair.getPrivate(), certificate, document);
            return new AthenzCredentials(certificate, keyPair, document);
        }
    }

    AthenzCredentials updateCredentials(SignedIdentityDocument document, SSLContext sslContext) {
        KeyPair newKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        Pkcs10Csr csr = csrGenerator.generateInstanceCsr(
                tenantIdentity,
                document.providerUniqueId(),
                document.ipAddresses(),
                document.clusterType(),
                newKeyPair);

        try (ZtsClient ztsClient = new DefaultZtsClient.Builder(ztsEndpoint).withSslContext(sslContext).build()) {
            InstanceIdentity instanceIdentity =
                    ztsClient.refreshInstance(
                            configserverIdentity,
                            tenantIdentity,
                            document.providerUniqueId().asDottedString(),
                            csr);
            X509Certificate certificate = instanceIdentity.certificate();
            writeCredentialsToDisk(newKeyPair.getPrivate(), certificate, document);
            return new AthenzCredentials(certificate, newKeyPair, document);
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
        return Optional.of(new AthenzCredentials(certificate.get(), keyPair, signedIdentityDocument));
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

    private DefaultIdentityDocumentClient createIdentityDocumentClient() {
        return new DefaultIdentityDocumentClient(
                configserverEndpoint,
                nodeIdentityProvider,
                new AthenzIdentityVerifier(singleton(configserverIdentity)));
    }
}
