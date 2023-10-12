// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.GcpSecretStore;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.routing.EndpointConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate.State;

/**
 * This provisions, assigns and updates the certificate for a given deployment.
 *
 * See also {@link com.yahoo.vespa.hosted.controller.maintenance.EndpointCertificateMaintainer}, which handles
 * refreshes, deletions and triggers deployments.
 *
 * @author andreer
 * @author mpolden
 */
public class EndpointCertificates {

    private static final Logger LOG = Logger.getLogger(EndpointCertificates.class.getName());
    private static final Duration GCP_CERTIFICATE_EXPIRY_TIME = Duration.ofDays(100); // 100 days, 10 more than notAfter time

    private final Controller controller;
    private final CuratorDb curator;
    private final Clock clock;
    private final EndpointCertificateProvider certificateProvider;
    private final EndpointCertificateValidator certificateValidator;
    private final BooleanFlag useAlternateCertProvider;
    private final StringFlag endpointCertificateAlgo;

    public EndpointCertificates(Controller controller, EndpointCertificateProvider certificateProvider,
                                EndpointCertificateValidator certificateValidator) {
        this.controller = controller;
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.curator = controller.curator();
        this.clock = controller.clock();
        this.certificateProvider = certificateProvider;
        this.certificateValidator = certificateValidator;
    }

    /** Returns a suitable certificate for endpoints of given deployment */
    public EndpointCertificate get(DeploymentId deployment, DeploymentSpec deploymentSpec, Mutex applicationLock) {
        Objects.requireNonNull(applicationLock);
        Instant start = clock.instant();
        EndpointConfig config = controller.routing().endpointConfig(deployment.applicationId());
        EndpointCertificate certificate = assignTo(deployment, deploymentSpec, config);
        Duration duration = Duration.between(start, clock.instant());
        if (duration.toSeconds() > 30) {
            LOG.log(Level.INFO, Text.format("Getting endpoint certificate for %s took %d seconds!", deployment.applicationId().serializedForm(), duration.toSeconds()));
        }
        if (isGcp(deployment)) {
            // This is needed until CKMS is available from GCP
            return validateGcpCertificate(deployment, deploymentSpec, certificate, config);
        }
        return certificate;
    }

    private boolean isGcp(DeploymentId deployment) {
        return controller.zoneRegistry().zones().all().in(CloudName.GCP).ids().contains(deployment.zoneId());
    }

    private EndpointCertificate validateGcpCertificate(DeploymentId deployment, DeploymentSpec deploymentSpec, EndpointCertificate certificate, EndpointConfig config) {
        // Validate before copying cert to GCP. This will ensure we don't bug out on the first deployment, but will take more time
        List<String> dnsNames = controller.routing().certificateDnsNames(deployment, deploymentSpec, certificate.generatedId().get(), config.supportsLegacy());
        certificateValidator.validate(certificate, deployment.applicationId().serializedForm(), deployment.zoneId(), dnsNames);
        GcpSecretStore gcpSecretStore = controller.serviceRegistry().gcpSecretStore();
        String mangledCertName = "endpointCert_" + certificate.certName().replace('.', '_') + "-v" + certificate.version(); // Google cloud does not accept dots in secrets, but they accept underscores
        String mangledKeyName = "endpointCert_" + certificate.keyName().replace('.', '_') + "-v" + certificate.version(); // Google cloud does not accept dots in secrets, but they accept underscores
        if (gcpSecretStore.getLatestSecretVersion(mangledCertName) == null) {
            gcpSecretStore.setSecret(mangledCertName,
                                     Optional.of(GCP_CERTIFICATE_EXPIRY_TIME),
                                     "endpoint-cert-accessor");
            gcpSecretStore.addSecretVersion(mangledCertName,
                                            controller.secretStore().getSecret(certificate.certName(), certificate.version()));
        }
        if (gcpSecretStore.getLatestSecretVersion(mangledKeyName) == null) {
            gcpSecretStore.setSecret(mangledKeyName,
                                     Optional.of(GCP_CERTIFICATE_EXPIRY_TIME),
                                     "endpoint-cert-accessor");
            gcpSecretStore.addSecretVersion(mangledKeyName,
                                            controller.secretStore().getSecret(certificate.keyName(), certificate.version()));
        }
        return certificate.withVersion(1).withKeyName(mangledKeyName).withCertName(mangledCertName);
    }

    private AssignedCertificate assignFromPool(TenantAndApplicationId application, Optional<InstanceName> instanceName, ZoneId zone) {
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            Optional<UnassignedCertificate> candidate = curator.readUnassignedCertificates().stream()
                                                               .filter(pc -> pc.state() == State.ready)
                                                               .min(Comparator.comparingLong(pc -> pc.certificate().lastRequested()));
            if (candidate.isEmpty()) {
                throw new IllegalArgumentException("No endpoint certificate available in pool, for deployment of " +
                                                   application + instanceName.map(i -> "." + i.value()).orElse("")
                                                   + " in " + zone);
            }
            try (NestedTransaction transaction = new NestedTransaction()) {
                curator.removeUnassignedCertificate(candidate.get(), transaction);
                AssignedCertificate assigned = new AssignedCertificate(application, instanceName, candidate.get().certificate(), false);
                curator.writeAssignedCertificate(assigned, transaction);
                transaction.commit();
                return assigned;
            }
        }
    }

    private AssignedCertificate instanceLevelCertificate(DeploymentId deployment, DeploymentSpec deploymentSpec, boolean allowPool) {
        TenantAndApplicationId application = TenantAndApplicationId.from(deployment.applicationId());
        Optional<InstanceName> instance = Optional.of(deployment.applicationId().instance());
        Optional<AssignedCertificate> currentCertificate = curator.readAssignedCertificate(application, instance);
        final AssignedCertificate assignedCertificate;
        if (currentCertificate.isEmpty()) {
            Optional<String> generatedId = Optional.empty();
            // Re-use the generated ID contained in an existing certificate (matching this application, this instance,
            // or any other instance present in deployment sec), if any. If this exists we provision a new certificate
            // containing the same ID
            if (!deployment.zoneId().environment().isManuallyDeployed()) {
                generatedId = curator.readAssignedCertificates().stream()
                                     .filter(ac -> {
                                         boolean matchingInstance = ac.instance().isPresent() &&
                                                                    deploymentSpec.instance(ac.instance().get()).isPresent();
                                         return (matchingInstance || ac.instance().isEmpty()) &&
                                                ac.application().equals(application);
                                     })
                                     .map(AssignedCertificate::certificate)
                                     .flatMap(ac -> ac.generatedId().stream())
                                     .findFirst();
            }
            if (allowPool && generatedId.isEmpty()) {
                assignedCertificate = assignFromPool(application, instance, deployment.zoneId());
            } else {
                if (generatedId.isEmpty()) {
                    generatedId = Optional.of(generateId());
                }
                EndpointCertificate provisionedCertificate = provision(deployment, Optional.empty(), deploymentSpec, generatedId.get());
                // We do not validate the certificate if one has never existed before - because we do not want to
                // wait for it to be available before we deploy. This allows the config server to start
                // provisioning nodes ASAP, and the risk is small for a new deployment.
                assignedCertificate = new AssignedCertificate(application, instance, provisionedCertificate, false);
            }
        } else {
            assignedCertificate = currentCertificate.get().withShouldValidate(!allowPool);
        }
        return assignedCertificate;
    }

    private AssignedCertificate applicationLevelCertificate(DeploymentId deployment) {
        if (deployment.zoneId().environment().isManuallyDeployed()) {
            throw new IllegalArgumentException(deployment + " is manually deployed and cannot assign an application-level certificate");
        }
        TenantAndApplicationId application = TenantAndApplicationId.from(deployment.applicationId());
        Optional<AssignedCertificate> applicationLevelCertificate = curator.readAssignedCertificate(application, Optional.empty());
        if (applicationLevelCertificate.isEmpty()) {
            Optional<AssignedCertificate> instanceLevelCertificate = curator.readAssignedCertificate(application, Optional.of(deployment.applicationId().instance()));
            // Migrate from instance-level certificate
            if (instanceLevelCertificate.isPresent()) {
                try (var transaction = new NestedTransaction()) {
                    AssignedCertificate assignedCertificate = instanceLevelCertificate.get().withoutInstance();
                    curator.removeAssignedCertificate(application, Optional.of(deployment.applicationId().instance()), transaction);
                    curator.writeAssignedCertificate(assignedCertificate, transaction);
                    transaction.commit();
                    return assignedCertificate;
                }
            } else {
                return assignFromPool(application, Optional.empty(), deployment.zoneId());
            }
        }
        return applicationLevelCertificate.get();
    }

    /** Assign a certificate to given deployment. A new certificate is provisioned (possibly from a pool) and reconfigured as necessary  */
    private EndpointCertificate assignTo(DeploymentId deployment, DeploymentSpec deploymentSpec, EndpointConfig config) {
        // Assign certificate based on endpoint config
        AssignedCertificate assignedCertificate = switch (config) {
            case legacy, combined -> instanceLevelCertificate(deployment, deploymentSpec, false);
            case generated -> deployment.zoneId().environment().isManuallyDeployed()
                    ? instanceLevelCertificate(deployment, deploymentSpec, true)
                    : applicationLevelCertificate(deployment);
        };

        // Generate ID if not already present in certificate
        Optional<String> generatedId = assignedCertificate.certificate().generatedId();
        if (generatedId.isEmpty()) {
            generatedId = Optional.of(generateId());
            assignedCertificate = assignedCertificate.with(assignedCertificate.certificate().withGeneratedId(generatedId.get()));
        }

        // Ensure all wanted names are present in certificate
        List<String> wantedNames = controller.routing().certificateDnsNames(deployment, deploymentSpec, generatedId.get(), config.supportsLegacy());
        Set<String> currentNames = Set.copyOf(assignedCertificate.certificate().requestedDnsSans());
        // TODO(mpolden): Consider requiring exact match for generated as we likely want to remove any legacy names in this case
        if (!currentNames.containsAll(wantedNames)) {
            EndpointCertificate updatedCertificate = provision(deployment, Optional.of(assignedCertificate.certificate()), deploymentSpec, generatedId.get());
            // Validation is unlikely to succeed in this case, as certificate must be available first. Controller will retry
            assignedCertificate = assignedCertificate.with(updatedCertificate)
                                                     .withShouldValidate(true);
        }

        // Require that generated ID is always set, for any kind of certificate
        if (assignedCertificate.certificate().generatedId().isEmpty()) {
            throw new IllegalArgumentException("Certificate for " + deployment + " does not contain generated ID: " +
                                               assignedCertificate.certificate());
        }

        // Update the time we last requested this certificate. This field is used by EndpointCertificateMaintainer to
        // determine stale certificates
        assignedCertificate = assignedCertificate.with(assignedCertificate.certificate().withLastRequested(clock.instant().getEpochSecond()));
        curator.writeAssignedCertificate(assignedCertificate);

        // Validate if we're re-assigned an existing certificate, or if we updated the names of an existing certificate
        if (assignedCertificate.shouldValidate()) {
            certificateValidator.validate(assignedCertificate.certificate(), deployment.applicationId().serializedForm(),
                                          deployment.zoneId(), wantedNames);
        }

        return assignedCertificate.certificate();
    }

    private String generateId() {
        List<String> unassignedIds = curator.readUnassignedCertificates().stream()
                                            .map(UnassignedCertificate::id)
                                            .toList();
        List<String> assignedIds = curator.readAssignedCertificates().stream()
                                          .map(AssignedCertificate::certificate)
                                          .map(EndpointCertificate::generatedId)
                                          .flatMap(Optional::stream)
                                          .toList();
        Set<String> allIds = Stream.concat(unassignedIds.stream(), assignedIds.stream()).collect(Collectors.toSet());
        String id;
        do {
            id = GeneratedEndpoint.createPart(controller.random(true));
        } while (allIds.contains(id));
        return id;
    }

    private EndpointCertificate provision(DeploymentId deployment,
                                          Optional<EndpointCertificate> current,
                                          DeploymentSpec deploymentSpec,
                                          String generatedId) {
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
        Set<String> wantedNames = requiredZones.stream()
                                                 .flatMap(zone -> controller.routing().certificateDnsNames(new DeploymentId(deployment.applicationId(), zone),
                                                                                                           deploymentSpec, generatedId, true)
                                                                            .stream())
                                                 .collect(Collectors.toCollection(LinkedHashSet::new));

        // Preserve any currently present names that are still valid (i.e. the name points to a zone found in this system)
        Set<String> currentNames = current.map(EndpointCertificate::requestedDnsSans)
                                          .map(Set::copyOf)
                                          .orElseGet(Set::of);
        for (var zone : zonesInSystem) {
            List<String> wantedNamesZone = controller.routing().certificateDnsNames(new DeploymentId(deployment.applicationId(), zone),
                                                                                    deploymentSpec,
                                                                                    generatedId,
                                                                                    true);
            if (currentNames.containsAll(wantedNamesZone)) {
                wantedNames.addAll(wantedNamesZone);
            }
        }

        // Request certificate
        LOG.log(Level.INFO, String.format("Requesting new endpoint certificate for application %s", deployment.applicationId().serializedForm()));
        String algo = endpointCertificateAlgo.with(FetchVector.Dimension.INSTANCE_ID, deployment.applicationId().serializedForm()).value();
        boolean useAlternativeProvider = useAlternateCertProvider.with(FetchVector.Dimension.INSTANCE_ID, deployment.applicationId().serializedForm()).value();
        String keyPrefix = deployment.applicationId().toFullString();
        Instant t0 = controller.clock().instant();
        EndpointCertificate endpointCertificate = certificateProvider.requestCaSignedCertificate(keyPrefix, List.copyOf(wantedNames), current, algo, useAlternativeProvider);
        Instant t1 = controller.clock().instant();
        LOG.log(Level.INFO, String.format("Endpoint certificate request for application %s returned after %s", deployment.applicationId().serializedForm(), Duration.between(t0, t1)));
        return endpointCertificate.withGeneratedId(generatedId);
    }

}
