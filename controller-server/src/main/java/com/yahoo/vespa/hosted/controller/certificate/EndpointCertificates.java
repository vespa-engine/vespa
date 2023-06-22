// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.certificate;

import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.GcpSecretStore;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate.*;

/**
 * Looks up stored endpoint certificate metadata, provisions new certificates if none is found,
 * and re-provisions the certificate if the deploying-to zone is not covered.
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
    private final BooleanFlag useRandomizedCert;
    private final BooleanFlag useAlternateCertProvider;
    private final StringFlag endpointCertificateAlgo;

    public EndpointCertificates(Controller controller, EndpointCertificateProvider certificateProvider,
                                EndpointCertificateValidator certificateValidator) {
        this.controller = controller;
        this.useRandomizedCert = Flags.RANDOMIZED_ENDPOINT_NAMES.bindTo(controller.flagSource());
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.curator = controller.curator();
        this.clock = controller.clock();
        this.certificateProvider = certificateProvider;
        this.certificateValidator = certificateValidator;
    }

    /** Returns certificate metadata for endpoints of given instance and zone */
    public Optional<EndpointCertificateMetadata> getMetadata(Instance instance, ZoneId zone, DeploymentSpec deploymentSpec) {
        Instant start = clock.instant();
        Optional<EndpointCertificateMetadata> metadata = getOrProvision(instance, zone, deploymentSpec);
        Duration duration = Duration.between(start, clock.instant());
        if (duration.toSeconds() > 30)
            log.log(Level.INFO, Text.format("Getting endpoint certificate metadata for %s took %d seconds!", instance.id().serializedForm(), duration.toSeconds()));

        if (controller.zoneRegistry().zones().all().in(CloudName.GCP).ids().contains(zone)) { // Until CKMS is available from GCP
            if (metadata.isPresent()) {
                // Validate metadata before copying cert to GCP. This will ensure we don't bug out on the first deployment, but will take more time
                certificateValidator.validate(metadata.get(), instance.id().serializedForm(), zone, controller.routing().certificateDnsNames(new DeploymentId(instance.id(), zone), deploymentSpec));
                var m = metadata.get();
                GcpSecretStore gcpSecretStore = controller.serviceRegistry().gcpSecretStore();
                String mangledCertName = "endpointCert_" + m.certName().replace('.', '_') + "-v" + m.version(); // Google cloud does not accept dots in secrets, but they accept underscores
                String mangledKeyName = "endpointCert_" + m.keyName().replace('.', '_') + "-v" + m.version(); // Google cloud does not accept dots in secrets, but they accept underscores
                if (gcpSecretStore.getSecret(mangledCertName, m.version()) == null)
                    gcpSecretStore.createSecret(mangledCertName, controller.secretStore().getSecret(m.certName(), m.version()));
                if (gcpSecretStore.getSecret(mangledKeyName, m.version()) == null)
                    gcpSecretStore.createSecret(mangledKeyName, controller.secretStore().getSecret(m.keyName(), m.version()));

                return Optional.of(m.withVersion(1).withKeyName(mangledKeyName).withCertName(mangledCertName));
            }
        }

        return metadata;
    }

    private EndpointCertificateMetadata assignFromPool(Instance instance, ZoneId zone) {
        // Assign certificate per instance only in manually deployed environments. In other environments, we share the
        // certificate because application endpoints can span instances
        Optional<InstanceName> instanceName = zone.environment().isManuallyDeployed() ? Optional.of(instance.name()) : Optional.empty();
        TenantAndApplicationId application = TenantAndApplicationId.from(instance.id());
        Optional<AssignedCertificate> assignedCertificate = curator.readAssignedCertificate(application, instanceName);
        if (assignedCertificate.isPresent()) {
            AssignedCertificate updated = assignedCertificate.get().with(assignedCertificate.get().certificate().withLastRequested(clock.instant().getEpochSecond()));
            curator.writeAssignedCertificate(updated);
            return updated.certificate();
        }
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            Optional<UnassignedCertificate> candidate = curator.readUnassignedCertificates().stream()
                                                               .filter(pc -> pc.state() == State.ready)
                                                               .min(Comparator.comparingLong(pc -> pc.certificate().lastRequested()));
            if (candidate.isEmpty()) {
                throw new IllegalArgumentException("No endpoint certificate available in pool, for deployment of " + instance.id() + " in " + zone);
            }
            try (NestedTransaction transaction = new NestedTransaction()) {
                curator.removeUnassignedCertificate(candidate.get(), transaction);
                curator.writeAssignedCertificate(new AssignedCertificate(application, instanceName, candidate.get().certificate()),
                                                 transaction);
                transaction.commit();
                return candidate.get().certificate();
            }
        }
    }

    private Optional<EndpointCertificateMetadata> getOrProvision(Instance instance, ZoneId zone, DeploymentSpec deploymentSpec) {
        if (useRandomizedCert.with(FetchVector.Dimension.APPLICATION_ID, instance.id().toFullString()).value()) {
            return Optional.of(assignFromPool(instance, zone));
        }
        Optional<AssignedCertificate> assignedCertificate = curator.readAssignedCertificate(TenantAndApplicationId.from(instance.id()), Optional.of(instance.id().instance()));
        DeploymentId deployment = new DeploymentId(instance.id(), zone);

        if (assignedCertificate.isEmpty()) {
            var provisionedCertificateMetadata = provisionEndpointCertificate(deployment, Optional.empty(), deploymentSpec);
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeAssignedCertificate(new AssignedCertificate(TenantAndApplicationId.from(instance.id()), Optional.of(instance.id().instance()), provisionedCertificateMetadata));
            return Optional.of(provisionedCertificateMetadata);
        } else {
            AssignedCertificate updated = assignedCertificate.get().with(assignedCertificate.get().certificate().withLastRequested(clock.instant().getEpochSecond()));
            curator.writeAssignedCertificate(updated);
        }

        // Re-provision certificate if it is missing SANs for the zone we are deploying to
        // Skip this validation for now if the cert has a randomized id
        Optional<EndpointCertificateMetadata> currentCertificateMetadata = assignedCertificate.map(AssignedCertificate::certificate);
        var requiredSansForZone = currentCertificateMetadata.get().randomizedId().isEmpty() ?
                controller.routing().certificateDnsNames(deployment, deploymentSpec) :
                List.<String>of();

        if (!currentCertificateMetadata.get().requestedDnsSans().containsAll(requiredSansForZone)) {
            var reprovisionedCertificateMetadata =
                    provisionEndpointCertificate(deployment, currentCertificateMetadata, deploymentSpec)
                            .withRootRequestId(currentCertificateMetadata.get().rootRequestId()); // We're required to keep the original request ID
            curator.writeAssignedCertificate(assignedCertificate.get().with(reprovisionedCertificateMetadata));
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            certificateValidator.validate(reprovisionedCertificateMetadata, instance.id().serializedForm(), zone, requiredSansForZone);
            return Optional.of(reprovisionedCertificateMetadata);
        }

        certificateValidator.validate(currentCertificateMetadata.get(), instance.id().serializedForm(), zone, requiredSansForZone);
        return currentCertificateMetadata;
    }

    private EndpointCertificateMetadata provisionEndpointCertificate(DeploymentId deployment,
                                                                     Optional<EndpointCertificateMetadata> currentMetadata,
                                                                     DeploymentSpec deploymentSpec) {
        List<ZoneId> zonesInSystem = controller.zoneRegistry().zones().controllerUpgraded().ids();
        Set<ZoneId> requiredZones = new LinkedHashSet<>();
        requiredZones.add(deployment.zoneId());
        if (!deployment.zoneId().environment().isManuallyDeployed()) {
            // If not deploying to a dev or perf zone, require all prod zones in deployment spec + test and staging
            Optional<DeploymentInstanceSpec> instanceSpec = deploymentSpec.instance(deployment.applicationId().instance());
            zonesInSystem.stream()
                         .filter(zone -> zone.environment().isTest() ||
                                         (instanceSpec.isPresent() &&
                                          instanceSpec.get().deploysTo(zone.environment(), zone.region())))
                         .forEach(requiredZones::add);
        }
        Set<String> requiredNames = requiredZones.stream()
                                                 .flatMap(zone -> controller.routing().certificateDnsNames(new DeploymentId(deployment.applicationId(), zone),
                                                                                                           deploymentSpec)
                                                                            .stream())
                                                 .collect(Collectors.toCollection(LinkedHashSet::new));

        // Preserve any currently present names that are still valid
        List<String> currentNames = currentMetadata.map(EndpointCertificateMetadata::requestedDnsSans)
                                                   .orElseGet(List::of);
        zonesInSystem.stream()
                     .map(zone -> controller.routing().certificateDnsNames(new DeploymentId(deployment.applicationId(), zone), deploymentSpec))
                     .filter(currentNames::containsAll)
                     .forEach(requiredNames::addAll);

        log.log(Level.INFO, String.format("Requesting new endpoint certificate from Cameo for application %s", deployment.applicationId().serializedForm()));
        String algo = this.endpointCertificateAlgo.with(FetchVector.Dimension.APPLICATION_ID, deployment.applicationId().serializedForm()).value();
        boolean useAlternativeProvider = useAlternateCertProvider.with(FetchVector.Dimension.APPLICATION_ID, deployment.applicationId().serializedForm()).value();
        String keyPrefix = deployment.applicationId().toFullString();
        var t0 = Instant.now();
        EndpointCertificateMetadata endpointCertificateMetadata = certificateProvider.requestCaSignedCertificate(keyPrefix, List.copyOf(requiredNames), currentMetadata, algo, useAlternativeProvider);
        var t1 = Instant.now();
        log.log(Level.INFO, String.format("Endpoint certificate request for application %s returned after %s", deployment.applicationId().serializedForm(), Duration.between(t0, t1)));
        return endpointCertificateMetadata;
    }

}
