package com.yahoo.vespa.hosted.controller.certificate;

import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Looks up stored endpoint certificate metadata, provisions new certificates if none is found,
 * re-provisions if zone is not covered, and uses refreshed certificates if a newer version is available.
 *
 * @author andreer
 */
public class EndpointCertificateManager {

    private static final Logger log = Logger.getLogger(EndpointCertificateManager.class.getName());

    private final ZoneRegistry zoneRegistry;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Clock clock;
    private final BooleanFlag validateEndpointCertificates;
    private final StringFlag deleteUnusedEndpointCertificates;
    private final BooleanFlag endpointCertInSharedRouting;

    public EndpointCertificateManager(ZoneRegistry zoneRegistry,
                                      CuratorDb curator,
                                      SecretStore secretStore,
                                      EndpointCertificateProvider endpointCertificateProvider,
                                      Clock clock, FlagSource flagSource) {
        this.zoneRegistry = zoneRegistry;
        this.curator = curator;
        this.secretStore = secretStore;
        this.endpointCertificateProvider = endpointCertificateProvider;
        this.clock = clock;
        this.validateEndpointCertificates = Flags.VALIDATE_ENDPOINT_CERTIFICATES.bindTo(flagSource);
        this.deleteUnusedEndpointCertificates = Flags.DELETE_UNUSED_ENDPOINT_CERTIFICATES.bindTo(flagSource);
        this.endpointCertInSharedRouting = Flags.ENDPOINT_CERT_IN_SHARED_ROUTING.bindTo(flagSource);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.deleteUnusedCertificates();
            } catch (Throwable t) {
                log.log(Level.INFO, "Unexpected Throwable caught while deleting unused endpoint certificates", t);
            }
        }, 1, 10, TimeUnit.MINUTES);
    }

    public Optional<EndpointCertificateMetadata> getEndpointCertificateMetadata(Instance instance, ZoneId zone, Optional<DeploymentInstanceSpec> instanceSpec) {
        var t0 = Instant.now();
        Optional<EndpointCertificateMetadata> metadata = getOrProvision(instance, zone, instanceSpec);
        metadata.ifPresent(m -> curator.writeEndpointCertificateMetadata(instance.id(), m.withLastRequested(clock.instant().getEpochSecond())));
        Duration duration = Duration.between(t0, Instant.now());
        if (duration.toSeconds() > 30)
            log.log(Level.INFO, String.format("Getting endpoint certificate metadata for %s took %d seconds!", instance.id().serializedForm(), duration.toSeconds()));
        return metadata;
    }

    @NotNull
    private Optional<EndpointCertificateMetadata> getOrProvision(Instance instance, ZoneId zone, Optional<DeploymentInstanceSpec> instanceSpec) {
        boolean endpointCertInSharedRouting = this.endpointCertInSharedRouting.with(FetchVector.Dimension.APPLICATION_ID, instance.id().serializedForm()).value();
        if (!zoneRegistry.zones().directlyRouted().ids().contains(zone) && !endpointCertInSharedRouting)
            return Optional.empty();

        final var currentCertificateMetadata = curator.readEndpointCertificateMetadata(instance.id());

        if (currentCertificateMetadata.isEmpty()) {
            var provisionedCertificateMetadata = provisionEndpointCertificate(instance, Optional.empty(), zone, instanceSpec);
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeEndpointCertificateMetadata(instance.id(), provisionedCertificateMetadata);
            return Optional.of(provisionedCertificateMetadata);
        }

        // Reprovision certificate if it is missing SANs for the zone we are deploying to
        var sansInCertificate = currentCertificateMetadata.get().requestedDnsSans();
        var requiredSansForZone = dnsNamesOf(instance.id(), zone);
        if (sansInCertificate.isPresent() && !sansInCertificate.get().containsAll(requiredSansForZone)) {
            var reprovisionedCertificateMetadata = provisionEndpointCertificate(instance, currentCertificateMetadata, zone, instanceSpec);
            curator.writeEndpointCertificateMetadata(instance.id(), reprovisionedCertificateMetadata);
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            validateEndpointCertificate(reprovisionedCertificateMetadata, instance, zone);
            return Optional.of(reprovisionedCertificateMetadata);
        }

        // Look for and use refreshed certificate
        var latestAvailableVersion = latestVersionInSecretStore(currentCertificateMetadata.get());
        if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > currentCertificateMetadata.get().version()) {
            var refreshedCertificateMetadata = currentCertificateMetadata.get().withVersion(latestAvailableVersion.getAsInt());
            validateEndpointCertificate(refreshedCertificateMetadata, instance, zone);
            curator.writeEndpointCertificateMetadata(instance.id(), refreshedCertificateMetadata);
            return Optional.of(refreshedCertificateMetadata);
        }

        validateEndpointCertificate(currentCertificateMetadata.get(), instance, zone);
        return currentCertificateMetadata;
    }

    enum CleanupMode {
        DISABLE,
        DRYRUN,
        ENABLE
    }

    private void deleteUnusedCertificates() {
        CleanupMode mode = CleanupMode.valueOf(deleteUnusedEndpointCertificates.value().toUpperCase());
        if (mode == CleanupMode.DISABLE) return;

        var oneMonthAgo = clock.instant().minus(4, ChronoUnit.WEEKS);
        curator.readAllEndpointCertificateMetadata().forEach((applicationId, storedMetaData) -> {
            var lastRequested = Instant.ofEpochSecond(storedMetaData.lastRequested());
            if (lastRequested.isBefore(oneMonthAgo) && hasNoDeployments(applicationId)) {
                log.log(LogLevel.INFO, "Cert for app " + applicationId.serializedForm()
                        + " has not been requested in a month and app has no deployments"
                        + (mode == CleanupMode.ENABLE ? ", deleting from provider and ZK" : ""));
                if (mode == CleanupMode.ENABLE) {
                    endpointCertificateProvider.deleteCertificate(applicationId, storedMetaData);
                    curator.deleteEndpointCertificateMetadata(applicationId);
                }
            }
        });
    }

    private boolean hasNoDeployments(ApplicationId applicationId) {
        var deployments = curator.readApplication(TenantAndApplicationId.from(applicationId))
                .flatMap(app -> app.get(applicationId.instance()))
                .map(Instance::deployments);

        return deployments.isEmpty() || deployments.get().size() == 0;
    }

    private OptionalInt latestVersionInSecretStore(EndpointCertificateMetadata originalCertificateMetadata) {
        try {
            var certVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.certName()));
            var keyVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.keyName()));
            return Sets.intersection(certVersions, keyVersions).stream().mapToInt(Integer::intValue).max();
        } catch (SecretNotFoundException s) {
            return OptionalInt.empty(); // Likely because the certificate is very recently provisioned - keep current version
        }
    }

    private EndpointCertificateMetadata provisionEndpointCertificate(Instance instance, Optional<EndpointCertificateMetadata> currentMetadata, ZoneId deploymentZone, Optional<DeploymentInstanceSpec> instanceSpec) {

        List<String> currentlyPresentNames = currentMetadata.isPresent() ?
                currentMetadata.get().requestedDnsSans().orElseThrow(() -> new RuntimeException("Certificate metadata exists but SANs are not present!"))
                : Collections.emptyList();

        var requiredZones = new LinkedHashSet<>(Set.of(deploymentZone));

        var zoneCandidateList = zoneRegistry.zones().controllerUpgraded().zones().stream().map(ZoneApi::getId).collect(Collectors.toList());

        // If not deploying to a dev or perf zone, require all prod zones in deployment spec + test and staging
        if (!deploymentZone.environment().isManuallyDeployed()) {
            zoneCandidateList.stream()
                    .filter(z -> z.environment().isTest() || instanceSpec.isPresent() && instanceSpec.get().deploysTo(z.environment(), z.region()))
                    .forEach(requiredZones::add);
        }

        var requiredNames = requiredZones.stream()
                .flatMap(zone -> dnsNamesOf(instance.id(), zone).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Make sure all currently present names will remain present.
        // Instead of just adding "currently present names", we regenerate them in case the names for a zone have changed.
        zoneCandidateList.stream()
                .map(zone -> dnsNamesOf(instance.id(), zone))
                .filter(zoneNames -> zoneNames.stream().anyMatch(currentlyPresentNames::contains))
                .filter(currentlyPresentNames::containsAll)
                .forEach(requiredNames::addAll);

        // This check must be relaxed if we ever remove from the set of names generated.
        if (!requiredNames.containsAll(currentlyPresentNames))
            throw new RuntimeException("SANs to be requested do not cover all existing names! Missing names: "
                    + currentlyPresentNames.stream().filter(s -> !requiredNames.contains(s)).collect(Collectors.joining(", ")));

        return endpointCertificateProvider.requestCaSignedCertificate(instance.id(), List.copyOf(requiredNames), currentMetadata);
    }

    private void validateEndpointCertificate(EndpointCertificateMetadata endpointCertificateMetadata, Instance instance, ZoneId zone) {
        if (validateEndpointCertificates.value())
            try {
                var pemEncodedEndpointCertificate = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());

                if (pemEncodedEndpointCertificate == null)
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Secret store returned null for certificate");

                List<X509Certificate> x509CertificateList = X509CertificateUtils.certificateListFromPem(pemEncodedEndpointCertificate);

                if (x509CertificateList.isEmpty())
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Empty certificate list");
                if (x509CertificateList.size() < 2)
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Only a single certificate found in chain - intermediate certificates likely missing");

                Instant now = clock.instant();
                Instant firstExpiry = Instant.MAX;
                for (X509Certificate x509Certificate : x509CertificateList) {
                    Instant notBefore = x509Certificate.getNotBefore().toInstant();
                    Instant notAfter = x509Certificate.getNotAfter().toInstant();
                    if (now.isBefore(notBefore))
                        throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is not yet valid");
                    if (now.isAfter(notAfter))
                        throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate has expired");
                    if (notAfter.isBefore(firstExpiry)) firstExpiry = notAfter;
                }

                X509Certificate endEntityCertificate = x509CertificateList.get(0);
                Set<String> subjectAlternativeNames = X509CertificateUtils.getSubjectAlternativeNames(endEntityCertificate).stream()
                        .filter(san -> san.getType().equals(SubjectAlternativeName.Type.DNS_NAME))
                        .map(SubjectAlternativeName::getValue).collect(Collectors.toSet());

                var dnsNamesOfZone = dnsNamesOf(instance.id(), zone);
                if (!subjectAlternativeNames.containsAll(dnsNamesOfZone))
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is missing required SANs for zone " + zone.value());

            } catch (SecretNotFoundException s) {
                // Normally because the cert is in the process of being provisioned - this will cause a retry in InternalStepRunner
                throw new EndpointCertificateException(EndpointCertificateException.Type.CERT_NOT_AVAILABLE, "Certificate not found in secret store");
            } catch (EndpointCertificateException e) {
                log.log(Level.WARNING, "Certificate validation failure for " + instance.id().serializedForm(), e);
                throw e;
            } catch (Exception e) {
                log.log(Level.WARNING, "Certificate validation failure for " + instance.id().serializedForm(), e);
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate validation failure for app " + instance.id().serializedForm(), e);
            }
    }

    private List<String> dnsNamesOf(ApplicationId applicationId, ZoneId zone) {
        List<String> endpointDnsNames = new ArrayList<>();

        // We add first an endpoint name based on a hash of the applicationId,
        // as the certificate provider requires the first CN to be < 64 characters long.
        endpointDnsNames.add(commonNameHashOf(applicationId, zoneRegistry.system()));

        List<Endpoint.EndpointBuilder> endpoints = new ArrayList<>();

        if (zone.environment().isProduction()) {
            endpoints.add(Endpoint.of(applicationId).target(EndpointId.defaultId()));
            endpoints.add(Endpoint.of(applicationId).wildcard());
        }

        endpoints.add(Endpoint.of(applicationId).target(ClusterSpec.Id.from("default"), zone));
        endpoints.add(Endpoint.of(applicationId).wildcard(zone));

        endpoints.stream()
                .map(endpoint -> endpoint.routingMethod(RoutingMethod.exclusive))
                .map(endpoint -> endpoint.on(Endpoint.Port.tls()))
                .map(endpointBuilder -> endpointBuilder.in(zoneRegistry.system()))
                .map(Endpoint::dnsName).forEach(endpointDnsNames::add);

        return Collections.unmodifiableList(endpointDnsNames);
    }

    /** Create a common name based on a hash of the ApplicationId. This should always be less than 64 characters long. */
    @SuppressWarnings("UnstableApiUsage")
    private static String commonNameHashOf(ApplicationId application, SystemName system) {
        var hashCode = Hashing.sha1().hashString(application.serializedForm(), Charset.defaultCharset());
        var base32encoded = BaseEncoding.base32().omitPadding().lowerCase().encode(hashCode.asBytes());
        return 'v' + base32encoded + Endpoint.dnsSuffix(system);
    }
}
