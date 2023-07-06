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
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
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

import static com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate.State;

/**
 * Looks up stored endpoint certificate, provisions new certificates if none is found,
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
    private final BooleanFlag useAlternateCertProvider;
    private final StringFlag endpointCertificateAlgo;
    private final static Duration GCP_CERTIFICATE_EXPIRY_TIME = Duration.ofDays(100); // 100 days, 10 more than notAfter time

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

    /** Returns a suitable certificate for endpoints of given instance and zone */
    public Optional<EndpointCertificate> get(Instance instance, ZoneId zone, DeploymentSpec deploymentSpec) {
        Instant start = clock.instant();
        Optional<EndpointCertificate> cert = getOrProvision(instance, zone, deploymentSpec);
        Duration duration = Duration.between(start, clock.instant());
        if (duration.toSeconds() > 30)
            log.log(Level.INFO, Text.format("Getting endpoint certificate for %s took %d seconds!", instance.id().serializedForm(), duration.toSeconds()));

        if (controller.zoneRegistry().zones().all().in(CloudName.GCP).ids().contains(zone)) { // Until CKMS is available from GCP
            if (cert.isPresent()) {
                // Validate before copying cert to GCP. This will ensure we don't bug out on the first deployment, but will take more time
                certificateValidator.validate(cert.get(), instance.id().serializedForm(), zone, controller.routing().certificateDnsNames(new DeploymentId(instance.id(), zone), deploymentSpec));
                GcpSecretStore gcpSecretStore = controller.serviceRegistry().gcpSecretStore();
                String mangledCertName = "endpointCert_" + cert.get().certName().replace('.', '_') + "-v" + cert.get().version(); // Google cloud does not accept dots in secrets, but they accept underscores
                String mangledKeyName = "endpointCert_" + cert.get().keyName().replace('.', '_') + "-v" + cert.get().version(); // Google cloud does not accept dots in secrets, but they accept underscores
                if (gcpSecretStore.getLatestSecretVersion(mangledCertName) == null) {
                    gcpSecretStore.setSecret(mangledCertName,
                                             Optional.of(GCP_CERTIFICATE_EXPIRY_TIME),
                                             "endpoint-cert-accessor");
                    gcpSecretStore.addSecretVersion(mangledCertName,
                                                    controller.secretStore().getSecret(cert.get().certName(), cert.get().version()));
                }
                if (gcpSecretStore.getLatestSecretVersion(mangledKeyName) == null) {
                    gcpSecretStore.setSecret(mangledKeyName,
                                             Optional.of(GCP_CERTIFICATE_EXPIRY_TIME),
                                             "endpoint-cert-accessor");
                    gcpSecretStore.addSecretVersion(mangledKeyName,
                                                    controller.secretStore().getSecret(cert.get().keyName(), cert.get().version()));
                }

                return Optional.of(cert.get().withVersion(1).withKeyName(mangledKeyName).withCertName(mangledCertName));
            }
        }

        return cert;
    }

    private EndpointCertificate assignFromPool(Instance instance, ZoneId zone) {
        // Assign certificate per instance only in manually deployed environments. In other environments, we share the
        // certificate because application endpoints can span instances
        Optional<InstanceName> instanceName = zone.environment().isManuallyDeployed() ? Optional.of(instance.name()) : Optional.empty();
        TenantAndApplicationId application = TenantAndApplicationId.from(instance.id());
        // Re-use existing certificate if it contains a randomized ID
        Optional<AssignedCertificate> assignedCertificate = curator.readAssignedCertificate(application, instanceName);
        if (assignedCertificate.isPresent() && assignedCertificate.get().certificate().randomizedId().isPresent()) {
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

    private Optional<EndpointCertificate> getOrProvision(Instance instance, ZoneId zone, DeploymentSpec deploymentSpec) {
        if (controller.routing().randomizedEndpointsEnabled(instance.id())) {
            return Optional.of(assignFromPool(instance, zone));
        }
        Optional<AssignedCertificate> assignedCertificate = curator.readAssignedCertificate(TenantAndApplicationId.from(instance.id()), Optional.of(instance.id().instance()));
        DeploymentId deployment = new DeploymentId(instance.id(), zone);

        if (assignedCertificate.isEmpty()) {
            var provisionedCertificate = provisionEndpointCertificate(deployment, Optional.empty(), deploymentSpec);
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeAssignedCertificate(new AssignedCertificate(TenantAndApplicationId.from(instance.id()), Optional.of(instance.id().instance()), provisionedCertificate));
            return Optional.of(provisionedCertificate);
        } else {
            AssignedCertificate updated = assignedCertificate.get().with(assignedCertificate.get().certificate().withLastRequested(clock.instant().getEpochSecond()));
            curator.writeAssignedCertificate(updated);
        }

        // Re-provision certificate if it is missing SANs for the zone we are deploying to
        // Skip this validation for now if the cert has a randomized id
        Optional<EndpointCertificate> currentCertificate = assignedCertificate.map(AssignedCertificate::certificate);
        var requiredSansForZone = currentCertificate.get().randomizedId().isEmpty() ?
                controller.routing().certificateDnsNames(deployment, deploymentSpec) :
                List.<String>of();

        if (!currentCertificate.get().requestedDnsSans().containsAll(requiredSansForZone)) {
            var reprovisionedCertificate =
                    provisionEndpointCertificate(deployment, currentCertificate, deploymentSpec)
                            .withRootRequestId(currentCertificate.get().rootRequestId()); // We're required to keep the original request ID
            curator.writeAssignedCertificate(assignedCertificate.get().with(reprovisionedCertificate));
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            certificateValidator.validate(reprovisionedCertificate, instance.id().serializedForm(), zone, requiredSansForZone);
            return Optional.of(reprovisionedCertificate);
        }

        certificateValidator.validate(currentCertificate.get(), instance.id().serializedForm(), zone, requiredSansForZone);
        return currentCertificate;
    }

    private EndpointCertificate provisionEndpointCertificate(DeploymentId deployment,
                                                             Optional<EndpointCertificate> currentCert,
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
        /* TODO(andreer/mpolden): To allow a seamless transition of existing deployments to using generated endpoints,
                                  we need to something like this:
                                  1) All current certificates must be re-provisioned to contain the same wildcard names
                                     as CertificatePoolMaintainer, and a randomized ID
                                  2) Generated endpoints must be exposed *before* switching deployment to a
                                     pre-provisioned certificate
                                  3) Tenants must shift their traffic to generated endpoints
                                  4) We can switch to the pre-provisioned certificate. This will invalidate
                                     non-generated endpoints
         */
        Set<String> requiredNames = requiredZones.stream()
                                                 .flatMap(zone -> controller.routing().certificateDnsNames(new DeploymentId(deployment.applicationId(), zone),
                                                                                                           deploymentSpec)
                                                                            .stream())
                                                 .collect(Collectors.toCollection(LinkedHashSet::new));

        // Preserve any currently present names that are still valid
        List<String> currentNames = currentCert.map(EndpointCertificate::requestedDnsSans)
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
        EndpointCertificate endpointCertificate = certificateProvider.requestCaSignedCertificate(keyPrefix, List.copyOf(requiredNames), currentCert, algo, useAlternativeProvider);
        var t1 = Instant.now();
        log.log(Level.INFO, String.format("Endpoint certificate request for application %s returned after %s", deployment.applicationId().serializedForm(), Duration.between(t0, t1)));
        return endpointCertificate;
    }

}
