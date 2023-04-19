// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.identity;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClientException;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocumentClient;
import com.yahoo.vespa.athenz.identityprovider.api.SignedIdentityDocument;
import com.yahoo.vespa.athenz.identityprovider.client.CsrGenerator;
import com.yahoo.vespa.athenz.identityprovider.client.DefaultIdentityDocumentClient;
import com.yahoo.vespa.athenz.tls.AthenzIdentityVerifier;
import com.yahoo.vespa.athenz.utils.SiaUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.node.admin.component.ConfigServerInfo;
import com.yahoo.vespa.hosted.node.admin.container.ContainerName;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentTask;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;
import com.yahoo.vespa.hosted.node.admin.task.util.fs.ContainerPath;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.maintenance.identity.AthenzCredentialsMaintainer.IdentityType.NODE;
import static com.yahoo.vespa.hosted.node.admin.maintenance.identity.AthenzCredentialsMaintainer.IdentityType.TENANT;

/**
 * A maintainer that is responsible for providing and refreshing Athenz credentials for a container.
 *
 * @author bjorncs
 */
public class AthenzCredentialsMaintainer implements CredentialsMaintainer {

    private static final Logger logger = Logger.getLogger(AthenzCredentialsMaintainer.class.getName());

    private static final Duration EXPIRY_MARGIN = Duration.ofDays(1);
    private static final Duration REFRESH_PERIOD = Duration.ofDays(1);
    private static final Duration REFRESH_BACKOFF = Duration.ofHours(1); // Backoff when refresh fails to ensure ZTS is not DDoS'ed.

    private static final String CONTAINER_SIA_DIRECTORY = "/var/lib/sia";

    private final URI ztsEndpoint;
    private final Path ztsTrustStorePath;
    private final Clock clock;
    private final String certificateDnsSuffix;
    private final ServiceIdentityProvider hostIdentityProvider;
    private final IdentityDocumentClient identityDocumentClient;
    private final BooleanFlag tenantServiceIdentityFlag;

    // Used as an optimization to ensure ZTS is not DDoS'ed on continuously failing refresh attempts
    private final Map<ContainerName, Instant> lastRefreshAttempt = new ConcurrentHashMap<>();

    public AthenzCredentialsMaintainer(URI ztsEndpoint,
                                       Path ztsTrustStorePath,
                                       ConfigServerInfo configServerInfo,
                                       String certificateDnsSuffix,
                                       ServiceIdentityProvider hostIdentityProvider,
                                       FlagSource flagSource,
                                       Clock clock) {
        this.ztsEndpoint = ztsEndpoint;
        this.ztsTrustStorePath = ztsTrustStorePath;
        this.certificateDnsSuffix = certificateDnsSuffix;
        this.hostIdentityProvider = hostIdentityProvider;
        this.identityDocumentClient = new DefaultIdentityDocumentClient(
                configServerInfo.getLoadBalancerEndpoint(),
                hostIdentityProvider,
                new AthenzIdentityVerifier(Set.of(configServerInfo.getConfigServerIdentity())));
        this.clock = clock;
        this.tenantServiceIdentityFlag = Flags.NODE_ADMIN_TENANT_SERVICE_REGISTRY.bindTo(flagSource);
    }

    public boolean converge(NodeAgentContext context) {
        var modified = false;
        modified |= maintain(context, NODE);
        if (shouldWriteTenantServiceIdentity(context))
            modified |= maintain(context, TENANT);
        return modified;
    }

    private boolean maintain(NodeAgentContext context, IdentityType identityType) {
        if (context.isDisabled(NodeAgentTask.CredentialsMaintainer)) return false;

        try {
            context.log(logger, Level.FINE, "Checking certificate");
            ContainerPath siaDirectory = context.paths().of(CONTAINER_SIA_DIRECTORY, context.users().vespa());
            ContainerPath identityDocumentFile = siaDirectory.resolve(identityType.getIdentityDocument());
            AthenzIdentity athenzIdentity = getAthenzIdentity(context, identityType, identityDocumentFile);
            ContainerPath privateKeyFile = (ContainerPath) SiaUtils.getPrivateKeyFile(siaDirectory, athenzIdentity);
            ContainerPath certificateFile = (ContainerPath) SiaUtils.getCertificateFile(siaDirectory, athenzIdentity);
            if (!Files.exists(privateKeyFile) || !Files.exists(certificateFile) || !Files.exists(identityDocumentFile)) {
                context.log(logger, "Certificate/private key/identity document file does not exist");
                Files.createDirectories(privateKeyFile.getParent());
                Files.createDirectories(certificateFile.getParent());
                Files.createDirectories(identityDocumentFile.getParent());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                return true;
            }

            X509Certificate certificate = readCertificateFromFile(certificateFile);
            Instant now = clock.instant();
            Instant expiry = certificate.getNotAfter().toInstant();
            var doc = EntityBindingsMapper.readSignedIdentityDocumentFromFile(identityDocumentFile);
            if (doc.outdated()) {
                context.log(logger, "Identity document is outdated (version=%d)", doc.documentVersion());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                return true;
            } else if (isCertificateExpired(expiry, now)) {
                context.log(logger, "Certificate has expired (expiry=%s)", expiry.toString());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                return true;
            }

            Duration age = Duration.between(certificate.getNotBefore().toInstant(), now);
            if (shouldRefreshCredentials(age)) {
                context.log(logger, "Certificate is ready to be refreshed (age=%s)", age.toString());
                if (shouldThrottleRefreshAttempts(context.containerName(), now)) {
                    context.log(logger, Level.WARNING, String.format(
                            "Skipping refresh attempt as last refresh was on %s (less than %s ago)",
                            lastRefreshAttempt.get(context.containerName()).toString(), REFRESH_BACKOFF.toString()));
                    return false;
                } else {
                    lastRefreshAttempt.put(context.containerName(), now);
                    refreshIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, doc, identityType, athenzIdentity);
                    return true;
                }
            }
            context.log(logger, Level.FINE, "Certificate is still valid");
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clearCredentials(NodeAgentContext context) {
        FileFinder.files(context.paths().of(CONTAINER_SIA_DIRECTORY))
                .deleteRecursively(context);
        lastRefreshAttempt.remove(context.containerName());
    }

    @Override
    public Duration certificateLifetime(NodeAgentContext context) {
        ContainerPath containerSiaDirectory = context.paths().of(CONTAINER_SIA_DIRECTORY);
        ContainerPath certificateFile = (ContainerPath) SiaUtils.getCertificateFile(containerSiaDirectory, context.identity());
        try {
            X509Certificate certificate = readCertificateFromFile(certificateFile);
            Instant now = clock.instant();
            Instant expiry = certificate.getNotAfter().toInstant();
            return Duration.between(now, expiry);
        } catch (IOException e) {
            context.log(logger, Level.SEVERE, "Unable to read certificate at " + certificateFile, e);
            return Duration.ZERO;
        }
    }

    @Override
    public String name() {
        return "node-certificate";
    }

    private boolean shouldRefreshCredentials(Duration age) {
        return age.compareTo(REFRESH_PERIOD) >= 0;
    }

    private boolean shouldThrottleRefreshAttempts(ContainerName containerName, Instant now) {
        return REFRESH_BACKOFF.compareTo(
                Duration.between(
                        lastRefreshAttempt.getOrDefault(containerName, Instant.EPOCH),
                        now)) > 0;
    }

    private void registerIdentity(NodeAgentContext context, ContainerPath privateKeyFile, ContainerPath certificateFile, ContainerPath identityDocumentFile, IdentityType identityType, AthenzIdentity identity) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        SignedIdentityDocument doc = signedIdentityDocument(context, identityType);
        CsrGenerator csrGenerator = new CsrGenerator(certificateDnsSuffix, doc.providerService().getFullName());
        Pkcs10Csr csr = csrGenerator.generateInstanceCsr(
                identity, doc.providerUniqueId(), doc.ipAddresses(), doc.clusterType(), keyPair);

        // Allow all zts hosts while removing SIS
        HostnameVerifier ztsHostNameVerifier = (hostname, sslSession) -> true;
        try (ZtsClient ztsClient = new DefaultZtsClient.Builder(ztsEndpoint(doc)).withIdentityProvider(hostIdentityProvider).withHostnameVerifier(ztsHostNameVerifier).build()) {
            InstanceIdentity instanceIdentity =
                    ztsClient.registerInstance(
                            doc.providerService(),
                            identity,
                            EntityBindingsMapper.toAttestationData(doc),
                            csr);
            EntityBindingsMapper.writeSignedIdentityDocumentToFile(identityDocumentFile, doc);
            writePrivateKeyAndCertificate(privateKeyFile, keyPair.getPrivate(), certificateFile, instanceIdentity.certificate());
            context.log(logger, "Instance successfully registered and credentials written to file");
        }
    }

    /**
     * Return zts url from identity document, fallback to ztsEndpoint
     */
    private URI ztsEndpoint(SignedIdentityDocument doc) {
        return Optional.ofNullable(doc.ztsUrl())
                .filter(s -> !s.isBlank())
                .map(URI::create)
                .orElse(ztsEndpoint);
    }
    private void refreshIdentity(NodeAgentContext context, ContainerPath privateKeyFile, ContainerPath certificateFile,
                                 ContainerPath identityDocumentFile, SignedIdentityDocument doc, IdentityType identityType, AthenzIdentity identity) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        CsrGenerator csrGenerator = new CsrGenerator(certificateDnsSuffix, doc.providerService().getFullName());
        Pkcs10Csr csr = csrGenerator.generateInstanceCsr(
                identity, doc.providerUniqueId(), doc.ipAddresses(), doc.clusterType(), keyPair);

        SSLContext containerIdentitySslContext = new SslContextBuilder().withKeyStore(privateKeyFile, certificateFile)
                                                                        .withTrustStore(ztsTrustStorePath)
                                                                        .build();

        try {
            // Allow all zts hosts while removing SIS
            HostnameVerifier ztsHostNameVerifier = (hostname, sslSession) -> true;
            try (ZtsClient ztsClient = new DefaultZtsClient.Builder(ztsEndpoint(doc)).withSslContext(containerIdentitySslContext).withHostnameVerifier(ztsHostNameVerifier).build()) {
                InstanceIdentity instanceIdentity =
                        ztsClient.refreshInstance(
                                doc.providerService(),
                                identity,
                                doc.providerUniqueId().asDottedString(),
                                csr);
                writePrivateKeyAndCertificate(privateKeyFile, keyPair.getPrivate(), certificateFile, instanceIdentity.certificate());
                context.log(logger, "Instance successfully refreshed and credentials written to file");
            } catch (ZtsClientException e) {
                if (e.getErrorCode() == 403 && e.getDescription().startsWith("Certificate revoked")) {
                    context.log(logger, Level.SEVERE, "Certificate cannot be refreshed as it is revoked by ZTS - re-registering the instance now", e);
                    registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, identity);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            context.log(logger, Level.SEVERE, "Certificate refresh failed: " + e.getMessage(), e);
        }
    }


    private static void writePrivateKeyAndCertificate(ContainerPath privateKeyFile,
                                                      PrivateKey privateKey,
                                                      ContainerPath certificateFile,
                                                      X509Certificate certificate) {
        writeFile(privateKeyFile, KeyUtils.toPem(privateKey));
        writeFile(certificateFile, X509CertificateUtils.toPem(certificate));
    }

    private static void writeFile(ContainerPath path, String utf8Content) {
        new UnixPath(path.resolveSibling(path.getFileName() + ".tmp"))
                .writeUtf8File(utf8Content, "r--------")
                .atomicMove(path);
    }

    private static X509Certificate readCertificateFromFile(ContainerPath certificateFile) throws IOException {
        String pemEncodedCertificate = new String(Files.readAllBytes(certificateFile));
        return X509CertificateUtils.fromPem(pemEncodedCertificate);
    }

    private static boolean isCertificateExpired(Instant expiry, Instant now) {
        return now.isAfter(expiry.minus(EXPIRY_MARGIN));
    }

    private SignedIdentityDocument signedIdentityDocument(NodeAgentContext context, IdentityType identityType) {
        return switch (identityType) {
            case NODE -> identityDocumentClient.getNodeIdentityDocument(context.hostname().value());
            case TENANT -> identityDocumentClient.getTenantIdentityDocument(context.hostname().value());
        };
    }

    private AthenzIdentity getAthenzIdentity(NodeAgentContext context, IdentityType identityType, ContainerPath identityDocumentFile) {
        return switch (identityType) {
            case NODE -> context.identity();
            case TENANT -> getTenantIdentity(context, identityDocumentFile);
        };
    }

    private AthenzIdentity getTenantIdentity(NodeAgentContext context, ContainerPath identityDocumentFile) {
        if (Files.exists(identityDocumentFile)) {
            return EntityBindingsMapper.readSignedIdentityDocumentFromFile(identityDocumentFile).serviceIdentity();
        } else {
            return identityDocumentClient.getTenantIdentityDocument(context.hostname().value()).serviceIdentity();
        }
    }

    private boolean shouldWriteTenantServiceIdentity(NodeAgentContext context) {
        return tenantServiceIdentityFlag
                .with(FetchVector.Dimension.HOSTNAME, context.hostname().value())
                .value();
    }

    enum IdentityType {
        NODE("vespa-node-identity-document.json"),
        TENANT("vespa-tenant-identity-document.json");

        private String identityDocument;
        IdentityType(String identityDocument) {
            this.identityDocument = identityDocument;
        }

        public String getIdentityDocument() {
            return identityDocument;
        }
    }
}
