// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.api.bindings.SignedIdentityDocumentEntity;
import com.yahoo.vespa.athenz.identityprovider.client.DefaultIdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.client.InstanceCsrGenerator;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.tls.KeyAlgorithm;
import com.yahoo.vespa.athenz.tls.KeyStoreType;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.Pkcs10Csr;
import com.yahoo.vespa.athenz.tls.SslContextBuilder;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static java.util.Collections.singleton;

/**
 * A maintainer that is responsible for providing and refreshing Athenz credentials for a container.
 *
 * @author bjorncs
 */
public class AthenzCredentialsMaintainer {

    private static final Duration EXPIRY_MARGIN = Duration.ofDays(1);
    private static final Duration REFRESH_PERIOD = Duration.ofDays(1);
    private static final Path CONTAINER_SIA_DIRECTORY = Paths.get("/var/lib/sia");

    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final boolean enabled;
    private final PrefixLogger log;
    private final String hostname;
    private final Path trustStorePath;
    private final Path privateKeyFile;
    private final Path certificateFile;
    private final Path identityDocumentFile;
    private final AthenzService containerIdentity;
    private final URI ztsEndpoint;
    private final Clock clock;
    private final ServiceIdentityProvider hostIdentityProvider;
    private final IdentityDocumentClient identityDocumentClient;
    private final InstanceCsrGenerator csrGenerator;
    private final AthenzService configserverIdentity;

    public AthenzCredentialsMaintainer(String hostname,
                                       Environment environment,
                                       ServiceIdentityProvider hostIdentityProvider) {
        ContainerName containerName = ContainerName.fromHostname(hostname);
        Path containerSiaDirectory = environment.pathInNodeAdminFromPathInNode(containerName, CONTAINER_SIA_DIRECTORY);
        this.enabled = environment.isNodeAgentCertEnabled();
        this.log = PrefixLogger.getNodeAgentLogger(AthenzCredentialsMaintainer.class, containerName);
        this.hostname = hostname;
        this.containerIdentity = environment.getNodeAthenzIdentity();
        this.ztsEndpoint = environment.getZtsUri();
        this.configserverIdentity = environment.getConfigserverAthenzIdentity();
        this.csrGenerator = new InstanceCsrGenerator(environment.getCertificateDnsSuffix());
        this.trustStorePath = environment.getTrustStorePath();
        this.privateKeyFile = SiaUtils.getPrivateKeyFile(containerSiaDirectory, containerIdentity);
        this.certificateFile = SiaUtils.getCertificateFile(containerSiaDirectory, containerIdentity);
        this.identityDocumentFile = containerSiaDirectory.resolve("vespa-node-identity-document.json");
        this.hostIdentityProvider = hostIdentityProvider;
        this.identityDocumentClient =
                new DefaultIdentityDocumentClient(
                        environment.getConfigserverLoadBalancerEndpoint(),
                        hostIdentityProvider,
                        new AthenzIdentityVerifier(singleton(configserverIdentity)));
        this.clock = Clock.systemUTC();
    }

    /**
     * @return Returns true if credentials were updated
     */
    public boolean converge() {
        try {
            if (!enabled) {
                log.debug("Feature disabled on this host - not fetching certificate");
                return false;
            }
            log.debug("Checking certificate");
            Instant now = clock.instant();
            if (!Files.exists(privateKeyFile) || !Files.exists(certificateFile) || !Files.exists(identityDocumentFile)) {
                log.info("Certificate/private key/identity document file does not exist");
                Files.createDirectories(privateKeyFile.getParent());
                Files.createDirectories(certificateFile.getParent());
                Files.createDirectories(identityDocumentFile.getParent());
                registerIdentity();
                return true;
            }
            X509Certificate certificate = readCertificateFromFile();
            Instant expiry = certificate.getNotAfter().toInstant();
            if (isCertificateExpired(expiry, now)) {
                log.info(String.format("Certificate has expired (expiry=%s)", expiry.toString()));
                registerIdentity();
                return true;
            }
            Duration age = Duration.between(certificate.getNotBefore().toInstant(), now);
            if (shouldRefreshCredentials(age)) {
                log.info(String.format("Certificate is ready to be refreshed (age=%s)", age.toString()));
                refreshIdentity();
                return true;
            }
            log.debug("Certificate is still valid");
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clearCredentials() {
        if (!enabled) return;
        try {
            if (Files.deleteIfExists(privateKeyFile))
                log.info(String.format("Deleted private key file (path=%s)", privateKeyFile));
            if (Files.deleteIfExists(certificateFile))
                log.info(String.format("Deleted certificate file (path=%s)", certificateFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean shouldRefreshCredentials(Duration age) {
        return age.compareTo(REFRESH_PERIOD) >= 0;
    }

    private X509Certificate readCertificateFromFile() throws IOException {
        String pemEncodedCertificate = new String(Files.readAllBytes(certificateFile));
        return X509CertificateUtils.fromPem(pemEncodedCertificate);
    }

    private boolean isCertificateExpired(Instant expiry, Instant now) {
        return now.isAfter(expiry.minus(EXPIRY_MARGIN));
    }

    private void registerIdentity() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        SignedIdentityDocument signedIdentityDocument = identityDocumentClient.getNodeIdentityDocument(hostname);
        Pkcs10Csr csr = csrGenerator.generateCsr(
                containerIdentity, signedIdentityDocument.providerUniqueId(), signedIdentityDocument.ipAddresses(), keyPair);
        try (ZtsClient ztsClient = new DefaultZtsClient(ztsEndpoint, hostIdentityProvider)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.registerInstance(
                            configserverIdentity,
                            containerIdentity,
                            signedIdentityDocument.providerUniqueId().asDottedString(),
                            EntityBindingsMapper.toAttestationData(signedIdentityDocument),
                            false,
                            csr);
            writeIdentityDocument(signedIdentityDocument);
            writePrivateKeyAndCertificate(keyPair.getPrivate(), instanceIdentity.certificate());
            log.info("Instance successfully registered and credentials written to file");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void refreshIdentity() {
        SignedIdentityDocument identityDocument = readIdentityDocument();
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        Pkcs10Csr csr = csrGenerator.generateCsr(containerIdentity, identityDocument.providerUniqueId(), identityDocument.ipAddresses(), keyPair);
        SSLContext containerIdentitySslContext =
                new SslContextBuilder()
                        .withKeyStore(privateKeyFile.toFile(), certificateFile.toFile())
                        .withTrustStore(trustStorePath.toFile(), KeyStoreType.JKS)
                        .build();
        try (ZtsClient ztsClient = new DefaultZtsClient(ztsEndpoint, containerIdentity, containerIdentitySslContext)) {
            InstanceIdentity instanceIdentity =
                    ztsClient.refreshInstance(
                            configserverIdentity,
                            containerIdentity,
                            identityDocument.providerUniqueId().asDottedString(),
                            false,
                            csr);
            writePrivateKeyAndCertificate(keyPair.getPrivate(), instanceIdentity.certificate());
            log.info("Instance successfully refreshed and credentials written to file");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SignedIdentityDocument readIdentityDocument() {
        try {
            SignedIdentityDocumentEntity entity = mapper.readValue(identityDocumentFile.toFile(), SignedIdentityDocumentEntity.class);
            return EntityBindingsMapper.toSignedIdentityDocument(entity);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeIdentityDocument(SignedIdentityDocument signedIdentityDocument) {
        try {
            SignedIdentityDocumentEntity entity =
                    EntityBindingsMapper.toSignedIdentityDocumentEntity(signedIdentityDocument);
            Path tempIdentityDocumentFile = toTempPath(identityDocumentFile);
            mapper.writeValue(tempIdentityDocumentFile.toFile(), entity);
            Files.move(tempIdentityDocumentFile, identityDocumentFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writePrivateKeyAndCertificate(PrivateKey privateKey, X509Certificate certificate) throws IOException {
        Path tempPrivateKeyFile = toTempPath(privateKeyFile);
        Files.write(tempPrivateKeyFile, KeyUtils.toPem(privateKey).getBytes());
        Path tempCertificateFile = toTempPath(certificateFile);
        Files.write(tempCertificateFile, X509CertificateUtils.toPem(certificate).getBytes());

        Files.move(tempPrivateKeyFile, privateKeyFile, StandardCopyOption.ATOMIC_MOVE);
        Files.move(tempCertificateFile, certificateFile, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Path toTempPath(Path file) {
        return Paths.get(file.toAbsolutePath().toString() + ".tmp");
    }

}
