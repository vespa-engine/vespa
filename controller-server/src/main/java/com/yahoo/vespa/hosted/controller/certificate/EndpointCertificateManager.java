package com.yahoo.vespa.hosted.controller.certificate;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Looks up stored endpoint certificate metadata, provisions new certificates if none is found,
 * re-provisions if zone is not covered, and uses refreshed certificates if a newer version is available.
 * <p>
 * See also EndpointCertificateMaintainer, which handles refreshes, deletions and triggers deployments
 *
 * @author andreer
 */
public class EndpointCertificateManager {

    private static final Logger log = Logger.getLogger(EndpointCertificateManager.class.getName());

    private final ZoneRegistry zoneRegistry;
    private final CuratorDb curator;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Clock clock;
    private final EndpointCertificateValidator endpointCertificateValidator;

    public EndpointCertificateManager(ZoneRegistry zoneRegistry,
                                      CuratorDb curator,
                                      EndpointCertificateProvider endpointCertificateProvider,
                                      EndpointCertificateValidator endpointCertificateValidator,
                                      Clock clock) {
        this.zoneRegistry = zoneRegistry;
        this.curator = curator;
        this.endpointCertificateProvider = endpointCertificateProvider;
        this.clock = clock;
        this.endpointCertificateValidator = endpointCertificateValidator;
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
        final var currentCertificateMetadata = curator.readEndpointCertificateMetadata(instance.id());

        if (currentCertificateMetadata.isEmpty()) {
            var provisionedCertificateMetadata = provisionEndpointCertificate(instance, Optional.empty(), zone, instanceSpec);
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeEndpointCertificateMetadata(instance.id(), provisionedCertificateMetadata);
            return Optional.of(provisionedCertificateMetadata);
        }

        // Re-provision certificate if it is missing SANs for the zone we are deploying to
        var requiredSansForZone = dnsNamesOf(instance.id(), zone);
        if (!currentCertificateMetadata.get().requestedDnsSans().containsAll(requiredSansForZone)) {
            var reprovisionedCertificateMetadata =
                    provisionEndpointCertificate(instance, currentCertificateMetadata, zone, instanceSpec)
                    .withRequestId(currentCertificateMetadata.get().request_id()); // We're required to keep the original request_id
            curator.writeEndpointCertificateMetadata(instance.id(), reprovisionedCertificateMetadata);
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            endpointCertificateValidator.validate(reprovisionedCertificateMetadata, instance.id().serializedForm(), zone, requiredSansForZone);
            return Optional.of(reprovisionedCertificateMetadata);
        }

        endpointCertificateValidator.validate(currentCertificateMetadata.get(), instance.id().serializedForm(), zone, requiredSansForZone);
        return currentCertificateMetadata;
    }

    private EndpointCertificateMetadata provisionEndpointCertificate(Instance instance, Optional<EndpointCertificateMetadata> currentMetadata, ZoneId deploymentZone, Optional<DeploymentInstanceSpec> instanceSpec) {

        List<String> currentlyPresentNames = currentMetadata.isPresent() ?
                currentMetadata.get().requestedDnsSans() : Collections.emptyList();

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
