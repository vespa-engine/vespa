// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.identity;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Timer;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.Pkcs10Csr;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzRole;
import com.yahoo.vespa.athenz.client.zts.DefaultZtsClient;
import com.yahoo.vespa.athenz.client.zts.InstanceIdentity;
import com.yahoo.vespa.athenz.client.zts.ZtsClient;
import com.yahoo.vespa.athenz.client.zts.ZtsClientException;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import com.yahoo.vespa.athenz.identityprovider.api.EntityBindingsMapper;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityDocument;
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
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private static final String LEGACY_SIA_DIRECTORY = "/opt/vespa/var/vespa/sia";

    private final URI ztsEndpoint;
    private final Path ztsTrustStorePath;
    private final Timer timer;
    private final String certificateDnsSuffix;
    private final ServiceIdentityProvider hostIdentityProvider;
    private final IdentityDocumentClient identityDocumentClient;
    private final BooleanFlag tenantServiceIdentityFlag;
    private final BooleanFlag useNewIdentityDocumentLayout;

    // Used as an optimization to ensure ZTS is not DDoS'ed on continuously failing refresh attempts
    private final Map<ContainerName, Instant> lastRefreshAttempt = new ConcurrentHashMap<>();

    public AthenzCredentialsMaintainer(URI ztsEndpoint,
                                       Path ztsTrustStorePath,
                                       ConfigServerInfo configServerInfo,
                                       String certificateDnsSuffix,
                                       ServiceIdentityProvider hostIdentityProvider,
                                       FlagSource flagSource,
                                       Timer timer) {
        this.ztsEndpoint = ztsEndpoint;
        this.ztsTrustStorePath = ztsTrustStorePath;
        this.certificateDnsSuffix = certificateDnsSuffix;
        this.hostIdentityProvider = hostIdentityProvider;
        this.identityDocumentClient = new DefaultIdentityDocumentClient(
                configServerInfo.getLoadBalancerEndpoint(),
                hostIdentityProvider,
                new AthenzIdentityVerifier(Set.of(configServerInfo.getConfigServerIdentity())));
        this.timer = timer;
        this.tenantServiceIdentityFlag = Flags.NODE_ADMIN_TENANT_SERVICE_REGISTRY.bindTo(flagSource);
        this.useNewIdentityDocumentLayout = Flags.NEW_IDDOC_LAYOUT.bindTo(flagSource);
    }

    public boolean converge(NodeAgentContext context) {
        var modified = false;
        modified |= maintain(context, NODE);

        if (context.zone().getSystemName().isPublic())
            return modified;

        if (shouldWriteTenantServiceIdentity(context)) {
            modified |= maintain(context, TENANT);
        } else {
            modified |= deleteTenantCredentials(context);
        }
        return modified;
    }

    private boolean maintain(NodeAgentContext context, IdentityType identityType) {
        if (context.isDisabled(NodeAgentTask.CredentialsMaintainer)) return false;

        try {
            var modified = false;
            context.log(logger, Level.FINE, "Checking certificate");
            ContainerPath siaDirectory = context.paths().of(CONTAINER_SIA_DIRECTORY, context.users().vespa());
            ContainerPath identityDocumentFile = siaDirectory.resolve(identityType.getIdentityDocument());
            Optional<AthenzIdentity> optionalAthenzIdentity = getAthenzIdentity(context, identityType, identityDocumentFile);
            if (optionalAthenzIdentity.isEmpty())
                return false;
            AthenzIdentity athenzIdentity = optionalAthenzIdentity.get();
            ContainerPath privateKeyFile = (ContainerPath) SiaUtils.getPrivateKeyFile(siaDirectory, athenzIdentity);
            ContainerPath certificateFile = (ContainerPath) SiaUtils.getCertificateFile(siaDirectory, athenzIdentity);
            if (!Files.exists(privateKeyFile) || !Files.exists(certificateFile) || !Files.exists(identityDocumentFile)) {
                context.log(logger, "Certificate/private key/identity document file does not exist");
                Files.createDirectories(privateKeyFile.getParent());
                Files.createDirectories(certificateFile.getParent());
                Files.createDirectories(identityDocumentFile.getParent());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                modified = true;
            }

            X509Certificate certificate = readCertificateFromFile(certificateFile);
            Instant now = timer.currentTime();
            Instant expiry = certificate.getNotAfter().toInstant();
            var doc = EntityBindingsMapper.readSignedIdentityDocumentFromFile(identityDocumentFile);
            if (refreshIdentityDocument(doc, context)) {
                context.log(logger, "Identity document is outdated (version=%d)", doc.documentVersion());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                modified = true;
            } else if (isCertificateExpired(expiry, now)) {
                context.log(logger, "Certificate has expired (expiry=%s)", expiry.toString());
                registerIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, identityType, athenzIdentity);
                modified = true;
            }

            Duration age = Duration.between(certificate.getNotBefore().toInstant(), now);
            if (shouldRefreshCredentials(age)) {
                context.log(logger, "Certificate is ready to be refreshed (age=%s)", age.toString());
                if (shouldThrottleRefreshAttempts(context.containerName(), now)) {
                    context.log(logger, Level.WARNING, String.format(
                            "Skipping refresh attempt as last refresh was on %s (less than %s ago)",
                            lastRefreshAttempt.get(context.containerName()).toString(), REFRESH_BACKOFF.toString()));
                } else {
                    lastRefreshAttempt.put(context.containerName(), now);
                    refreshIdentity(context, privateKeyFile, certificateFile, identityDocumentFile, doc.identityDocument(), identityType, athenzIdentity);
                    modified = true;
                }
            }

            if (identityType == TENANT) {
                modified |= maintainRoleCertificates(context, siaDirectory, privateKeyFile, certificateFile, athenzIdentity, doc.identityDocument());
                copyCredsToLegacyPath(context, privateKeyFile, certificateFile);
            }
            return modified;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean maintainRoleCertificates(NodeAgentContext context,
                                             ContainerPath siaDirectory,
                                             ContainerPath privateKeyFile,
                                             ContainerPath certificateFile,
                                             AthenzIdentity identity,
                                             IdentityDocument identityDocument) {
        var modified = false;

        for (var role : getRoleList(context)) {
                try {
                    var roleCertificatePath = siaDirectory.resolve("certs")
                            .resolve(String.format("%s.cert.pem", role));
                    var roleKeyPath = siaDirectory.resolve("keys")
                            .resolve(String.format("%s.key.pem", role));
                    if (Files.notExists(roleCertificatePath)) {
                        writeRoleCredentials(context, privateKeyFile, certificateFile, roleCertificatePath, roleKeyPath, identity, identityDocument, role);
                        modified = true;
                    } else if (shouldRefreshCertificate(context, roleCertificatePath)) {
                        writeRoleCredentials(context, privateKeyFile, certificateFile, roleCertificatePath, roleKeyPath, identity, identityDocument, role);
                        modified = true;
                    }
                } catch (IOException e) {
                    context.log(logger, Level.WARNING, "Failed to maintain role certificate " + role, e);
                }
        }
        return modified;
    }

    private boolean shouldRefreshCertificate(NodeAgentContext context, ContainerPath certificatePath) throws IOException {
        var certificate = readCertificateFromFile(certificatePath);
        var now = timer.currentTime();
        var shouldRefresh = now.isAfter(certificate.getNotAfter().toInstant()) ||
                now.isAfter(certificate.getNotBefore().toInstant().plus(REFRESH_PERIOD));
        return !shouldThrottleRefreshAttempts(context.containerName(), now) &&
                shouldRefresh;
    }

    private void writeRoleCredentials(NodeAgentContext context,
                                      ContainerPath privateKeyFile,
                                      ContainerPath certificateFile,
                                      ContainerPath roleCertificatePath,
                                      ContainerPath roleKeyPath,
                                      AthenzIdentity identity,
                                      IdentityDocument identityDocument,
                                      String role) throws IOException {
        HostnameVerifier ztsHostNameVerifier = (hostname, sslSession) -> true;
        var keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        var athenzRole = AthenzRole.fromResourceNameString(role);

        var containerIdentitySslContext = new SslContextBuilder()
                .withKeyStore(privateKeyFile, certificateFile)
                .withTrustStore(ztsTrustStorePath)
                .build();
        try (ZtsClient ztsClient = new DefaultZtsClient.Builder(ztsEndpoint(identityDocument))
                .withSslContext(containerIdentitySslContext)
                .withHostnameVerifier(ztsHostNameVerifier)
                .build()) {
            var csrGenerator = new CsrGenerator(certificateDnsSuffix, identityDocument.providerService().getFullName());
            var csr = csrGenerator.generateRoleCsr(
                    identity, athenzRole, identityDocument.providerUniqueId(), identityDocument.clusterType(), keyPair);
            var roleCertificate = ztsClient.getRoleCertificate(athenzRole, csr);
            writePrivateKeyAndCertificate(roleKeyPath, keyPair.getPrivate(), roleCertificatePath, roleCertificate);
            context.log(logger, "Role certificate successfully retrieved written to file " + roleCertificatePath.pathInContainer());
        }
    }

    private boolean refreshIdentityDocument(SignedIdentityDocument signedIdentityDocument, NodeAgentContext context) {
        int expectedVersion = documentVersion(context);
        return signedIdentityDocument.outdated() || signedIdentityDocument.documentVersion() != expectedVersion;
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
            Instant now = timer.currentTime();
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

    private boolean deleteTenantCredentials(NodeAgentContext context) {
        var siaDirectory = context.paths().of(CONTAINER_SIA_DIRECTORY, context.users().vespa());
        var identityDocumentFile = siaDirectory.resolve(TENANT.getIdentityDocument());
        if (!Files.exists(identityDocumentFile)) return false;
        return getAthenzIdentity(context, TENANT, identityDocumentFile).map(athenzIdentity -> {
            var privateKeyFile = (ContainerPath) SiaUtils.getPrivateKeyFile(siaDirectory, athenzIdentity);
            var certificateFile = (ContainerPath) SiaUtils.getCertificateFile(siaDirectory, athenzIdentity);
            try {
                var modified = Files.deleteIfExists(identityDocumentFile);
                modified |= Files.deleteIfExists(privateKeyFile);
                modified |= Files.deleteIfExists(certificateFile);
                return modified;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).orElse(false);
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
        SignedIdentityDocument signedDoc = signedIdentityDocument(context, identityType);
        IdentityDocument doc = signedDoc.identityDocument();
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
                            EntityBindingsMapper.toAttestationData(signedDoc),
                            csr);
            EntityBindingsMapper.writeSignedIdentityDocumentToFile(identityDocumentFile, signedDoc);
            writePrivateKeyAndCertificate(privateKeyFile, keyPair.getPrivate(), certificateFile, instanceIdentity.certificate());
            context.log(logger, "Instance successfully registered and credentials written to file");
        }
    }

    /**
     * Return zts url from identity document, fallback to ztsEndpoint
     */
    private URI ztsEndpoint(IdentityDocument doc) {
        return Optional.ofNullable(doc.ztsUrl())
                .filter(s -> !s.isBlank())
                .map(URI::create)
                .orElse(ztsEndpoint);
    }
    private void refreshIdentity(NodeAgentContext context, ContainerPath privateKeyFile, ContainerPath certificateFile,
                                 ContainerPath identityDocumentFile, IdentityDocument doc, IdentityType identityType, AthenzIdentity identity) {
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
            case NODE -> identityDocumentClient.getNodeIdentityDocument(context.hostname().value(), documentVersion(context));
            case TENANT -> identityDocumentClient.getTenantIdentityDocument(context.hostname().value(), documentVersion(context)).get();
        };
    }

    private Optional<AthenzIdentity> getAthenzIdentity(NodeAgentContext context, IdentityType identityType, ContainerPath identityDocumentFile) {
        return switch (identityType) {
            case NODE -> Optional.of(context.identity());
            case TENANT -> getTenantIdentity(context, identityDocumentFile);
        };
    }

    private Optional<AthenzIdentity> getTenantIdentity(NodeAgentContext context, ContainerPath identityDocumentFile) {
        if (Files.exists(identityDocumentFile)) {
            return Optional.of(EntityBindingsMapper.readSignedIdentityDocumentFromFile(identityDocumentFile).identityDocument().serviceIdentity());
        } else {
            return identityDocumentClient.getTenantIdentityDocument(context.hostname().value(), documentVersion(context))
                    .map(doc -> doc.identityDocument().serviceIdentity());
        }
    }

    private boolean shouldWriteTenantServiceIdentity(NodeAgentContext context) {
        var version = context.node().currentVespaVersion()
                .orElse(context.node().wantedVespaVersion().orElse(Version.emptyVersion));
        var appId = context.node().owner().orElse(ApplicationId.defaultId());
        return tenantServiceIdentityFlag
                .with(FetchVector.Dimension.VESPA_VERSION, version.toFullString())
                .with(FetchVector.Dimension.APPLICATION_ID, appId.serializedForm())
                .value();
    }

    private void copyCredsToLegacyPath(NodeAgentContext context, ContainerPath privateKeyFile, ContainerPath certificateFile) throws IOException {
        var legacySiaDirectory = context.paths().of(LEGACY_SIA_DIRECTORY, context.users().vespa());
        var keysDirectory = legacySiaDirectory.resolve("keys");
        var certsDirectory = legacySiaDirectory.resolve("certs");
        Files.createDirectories(keysDirectory);
        Files.createDirectories(certsDirectory);
        writeFile(certsDirectory.resolve(certificateFile.getFileName()), new String(Files.readAllBytes(certificateFile)));
        writeFile(keysDirectory.resolve(privateKeyFile.getFileName()), new String(Files.readAllBytes(privateKeyFile)));
    }

    /*
    Get the document version to ask for
     */
    private int documentVersion(NodeAgentContext context) {
        var version = context.node().currentVespaVersion()
                .orElse(context.node().wantedVespaVersion().orElse(Version.emptyVersion));
        var appId = context.node().owner().orElse(ApplicationId.defaultId());
        return useNewIdentityDocumentLayout
                .with(FetchVector.Dimension.HOSTNAME, context.hostname().value())
                .with(FetchVector.Dimension.VESPA_VERSION, version.toFullString())
                .with(FetchVector.Dimension.APPLICATION_ID, appId.serializedForm())
                .value()
                ? SignedIdentityDocument.DEFAULT_DOCUMENT_VERSION
                : SignedIdentityDocument.LEGACY_DEFAULT_DOCUMENT_VERSION;
    }

    private List<String> getRoleList(NodeAgentContext context) {
        try {
            return identityDocumentClient.getNodeRoles(context.hostname().value());
        } catch (Exception e) {
            context.log(logger, Level.WARNING, "Failed to retrieve role list", e);
            return List.of();
        }
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
