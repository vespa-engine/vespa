// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.identity;

import com.yahoo.log.LogLevel;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyStoreType;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClientException;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.client.DefaultIdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.client.CsrGenerator;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
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
import java.util.logging.Logger;

import static java.util.Collections.singleton;

/**
 * A maintainer that is responsible for providing and refreshing Athenz credentials for a container.
 *
 * @author bjorncs
 */
public class AthenzCredentialsMaintainer {

    private static final Logger logger = Logger.getLogger(AthenzCredentialsMaintainer.class.getName());

    private static final Duration EXPIRY_MARGIN = Duration.ofDays(1);
    private static final Duration REFRESH_PERIOD = Duration.ofDays(1);
    private static final Duration REFRESH_BACKOFF = Duration.ofHours(1); // Backoff when refresh fails to ensure ZTS is not DDoS'ed.

    private static final Path CONTAINER_SIA_DIRECTORY = Paths.get("/var/lib/sia");

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
    private final CsrGenerator csrGenerator;
    private final AthenzService configserverIdentity;

    private Instant lastRefreshAttempt = Instant.EPOCH; // Used as an optimization to ensure ZTS is not DDoS'ed on continuously failing refresh attempts

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
        this.csrGenerator = new CsrGenerator(environment.getCertificateDnsSuffix(), configserverIdentity.getFullName());
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

    public void converge(NodeAgentContext context) {
        try {
            context.log(logger, LogLevel.DEBUG, "Checking certificate");
            if (!Files.exists(privateKeyFile) || !Files.exists(certificateFile) || !Files.exists(identityDocumentFile)) {
                context.log(logger, "Certificate/private key/identity document file does not exist");
                Files.createDirectories(privateKeyFile.getParent());
                Files.createDirectories(certificateFile.getParent());
                Files.createDirectories(identityDocumentFile.getParent());
                registerIdentity(context);
                return;
            }

            X509Certificate certificate = readCertificateFromFile(certificateFile);
            Instant now = clock.instant();
            Instant expiry = certificate.getNotAfter().toInstant();
            if (isCertificateExpired(expiry, now)) {
                context.log(logger, "Certificate has expired (expiry=%s)", expiry.toString());
                registerIdentity(context);
                return;
            }

            Duration age = Duration.between(certificate.getNotBefore().toInstant(), now);
            if (shouldRefreshCredentials(age)) {
                context.log(logger, "Certificate is ready to be refreshed (age=%s)", age.toString());
                if (shouldThrottleRefreshAttempts(now)) {
                    context.log(logger, LogLevel.WARNING, String.format(
                            "Skipping refresh attempt as last refresh was on %s (less than %s ago)",
                            lastRefreshAttempt.toString(), REFRESH_BACKOFF.toString()));
                    return;
                } else {
                    lastRefreshAttempt = now;
                    refreshIdentity(context);
                    return;
                }
            }
            context.log(logger, LogLevel.DEBUG, "Certificate is still valid");
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
            if (Files.deleteIfExists(identityDocumentFile))
                log.info(String.format("Deleted identity document file (path=%s)", certificateFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean shouldRefreshCredentials(Duration age) {
        return age.compareTo(REFRESH_PERIOD) >= 0;
    }

    private boolean shouldThrottleRefreshAttempts(Instant now) {
        return REFRESH_BACKOFF.compareTo(Duration.between(lastRefreshAttempt, now)) > 0;
    }

    @SuppressWarnings("deprecation")
    private void registerIdentity(NodeAgentContext context) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        SignedIdentityDocument signedIdentityDocument = identityDocumentClient.getNodeIdentityDocument(hostname);
        com.yahoo.vespa.athenz.tls.Pkcs10Csr csr = csrGenerator.generateInstanceCsr(
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
            EntityBindingsMapper.writeSignedIdentityDocumentToFile(identityDocumentFile, signedIdentityDocument);
            writePrivateKeyAndCertificate(keyPair.getPrivate(), instanceIdentity.certificate());
            context.log(logger, "Instance successfully registered and credentials written to file");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshIdentity(NodeAgentContext context) {
        SignedIdentityDocument identityDocument = EntityBindingsMapper.readSignedIdentityDocumentFromFile(identityDocumentFile);
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        com.yahoo.vespa.athenz.tls.Pkcs10Csr csr = csrGenerator.generateInstanceCsr(containerIdentity, identityDocument.providerUniqueId(), identityDocument.ipAddresses(), keyPair);
        SSLContext containerIdentitySslContext =
                new SslContextBuilder()
                        .withKeyStore(privateKeyFile, certificateFile)
                        .withTrustStore(trustStorePath, KeyStoreType.JKS)
                        .build();
        try {
            try (ZtsClient ztsClient = new DefaultZtsClient(ztsEndpoint, containerIdentity, containerIdentitySslContext)) {
                InstanceIdentity instanceIdentity =
                        ztsClient.refreshInstance(
                                configserverIdentity,
                                containerIdentity,
                                identityDocument.providerUniqueId().asDottedString(),
                                false,
                                csr);
                writePrivateKeyAndCertificate(keyPair.getPrivate(), instanceIdentity.certificate());
                context.log(logger, "Instance successfully refreshed and credentials written to file");
            } catch (ZtsClientException e) {
                if (e.getErrorCode() == 403 && e.getDescription().startsWith("Certificate revoked")) {
                    context.log(logger, LogLevel.ERROR, "Certificate cannot be refreshed as it is revoked by ZTS - re-registering the instance now", e);
                    registerIdentity(context);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            context.log(logger, LogLevel.ERROR, "Certificate refresh failed: " + e.getMessage(), e);
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

    private static X509Certificate readCertificateFromFile(Path certificateFile) throws IOException {
        String pemEncodedCertificate = new String(Files.readAllBytes(certificateFile));
        return X509CertificateUtils.fromPem(pemEncodedCertificate);
    }

    private static boolean isCertificateExpired(Instant expiry, Instant now) {
        return now.isAfter(expiry.minus(EXPIRY_MARGIN));
    }
}
