// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 *
 * See also {@link com.yahoo.vespa.hosted.controller.maintenance.EndpointCertificateMaintainer}, which handles
 * refreshes, deletions and triggers deployments.
 *
 * @author andreer
 */
public class EndpointCertificates {

    private static final Logger log = Logger.getLogger(EndpointCertificates.class.getName());

    private final Controller controller;
    private final CuratorDb curator;
    private final Clock clock;
    private final EndpointCertificateProvider certificateProvider;
    private final EndpointCertificateValidator certificateValidator;

    public EndpointCertificates(Controller controller, EndpointCertificateProvider certificateProvider,
                                EndpointCertificateValidator certificateValidator) {
        this.controller = controller;
        this.curator = controller.curator();
        this.clock = controller.clock();
        this.certificateProvider = certificateProvider;
        this.certificateValidator = certificateValidator;
    }

    /** Returns certificate metadata for endpoints of given instance and zone */
    public Optional<EndpointCertificateMetadata> getMetadata(Instance instance, ZoneId zone, Optional<DeploymentInstanceSpec> instanceSpec) {
        Instant start = clock.instant();
        Optional<EndpointCertificateMetadata> metadata = getOrProvision(instance, zone, instanceSpec);
        metadata.ifPresent(m -> curator.writeEndpointCertificateMetadata(instance.id(), m.withLastRequested(clock.instant().getEpochSecond())));
        Duration duration = Duration.between(start, clock.instant());
        if (duration.toSeconds() > 30)
            log.log(Level.INFO, String.format("Getting endpoint certificate metadata for %s took %d seconds!", instance.id().serializedForm(), duration.toSeconds()));
        return metadata;
    }

    private Optional<EndpointCertificateMetadata> getOrProvision(Instance instance, ZoneId zone, Optional<DeploymentInstanceSpec> instanceSpec) {
        final var currentCertificateMetadata = curator.readEndpointCertificateMetadata(instance.id());

        DeploymentId deployment = new DeploymentId(instance.id(), zone);

        if (currentCertificateMetadata.isEmpty()) {
            var provisionedCertificateMetadata = provisionEndpointCertificate(deployment, Optional.empty(), instanceSpec);
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeEndpointCertificateMetadata(instance.id(), provisionedCertificateMetadata);
            return Optional.of(provisionedCertificateMetadata);
        }

        // Re-provision certificate if it is missing SANs for the zone we are deploying to
        var requiredSansForZone = controller.routing().wildcardEndpoints(deployment);
        if (!currentCertificateMetadata.get().requestedDnsSans().containsAll(requiredSansForZone)) {
            var reprovisionedCertificateMetadata =
                    provisionEndpointCertificate(deployment, currentCertificateMetadata, instanceSpec)
                            .withRequestId(currentCertificateMetadata.get().requestId()); // We're required to keep the original request ID
            curator.writeEndpointCertificateMetadata(instance.id(), reprovisionedCertificateMetadata);
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            certificateValidator.validate(reprovisionedCertificateMetadata, instance.id().serializedForm(), zone, requiredSansForZone);
            return Optional.of(reprovisionedCertificateMetadata);
        }

        certificateValidator.validate(currentCertificateMetadata.get(), instance.id().serializedForm(), zone, requiredSansForZone);
        return currentCertificateMetadata;
    }

    private EndpointCertificateMetadata provisionEndpointCertificate(DeploymentId deployment, Optional<EndpointCertificateMetadata> currentMetadata, Optional<DeploymentInstanceSpec> instanceSpec) {

        List<String> currentlyPresentNames = currentMetadata.isPresent() ?
                currentMetadata.get().requestedDnsSans() : Collections.emptyList();

        var requiredZones = new LinkedHashSet<>(Set.of(deployment.zoneId()));

        var zoneCandidateList = controller.zoneRegistry().zones().controllerUpgraded().zones().stream().map(ZoneApi::getId).collect(Collectors.toList());

        // If not deploying to a dev or perf zone, require all prod zones in deployment spec + test and staging
        if (!deployment.zoneId().environment().isManuallyDeployed()) {
            zoneCandidateList.stream()
                             .filter(z -> z.environment().isTest() || instanceSpec.isPresent() && instanceSpec.get().deploysTo(z.environment(), z.region()))
                             .forEach(requiredZones::add);
        }

        var requiredNames = requiredZones.stream()
                                         .flatMap(zone -> controller.routing().wildcardEndpoints(new DeploymentId(deployment.applicationId(), zone)).stream())
                                         .collect(Collectors.toCollection(LinkedHashSet::new));

        // Make sure all currently present names will remain present.
        // Instead of just adding "currently present names", we regenerate them in case the names for a zone have changed.
        zoneCandidateList.stream()
                         .map(zone -> controller.routing().wildcardEndpoints(new DeploymentId(deployment.applicationId(), zone)))
                         .filter(zoneNames -> zoneNames.stream().anyMatch(currentlyPresentNames::contains))
                         .filter(currentlyPresentNames::containsAll)
                         .forEach(requiredNames::addAll);

        // This check must be relaxed if we ever remove from the set of names generated.
        if (!requiredNames.containsAll(currentlyPresentNames))
            throw new RuntimeException("SANs to be requested do not cover all existing names! Missing names: "
                                       + currentlyPresentNames.stream().filter(s -> !requiredNames.contains(s)).collect(Collectors.joining(", ")));

        return certificateProvider.requestCaSignedCertificate(deployment.applicationId(), List.copyOf(requiredNames), currentMetadata);
    }

}
