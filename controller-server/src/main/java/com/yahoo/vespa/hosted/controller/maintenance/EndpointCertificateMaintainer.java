// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Sets;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateDetails;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequest;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;
import com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.EndpointSecretManager;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.certificate.AssignedCertificate;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Updates refreshed endpoint certificates and triggers redeployment, and deletes unused certificates.
 * <p>
 * See also class EndpointCertificates, which provisions, reprovisions and validates certificates on deploy
 *
 * @author andreer
 */
public class EndpointCertificateMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(EndpointCertificateMaintainer.class.getName());

    private final DeploymentTrigger deploymentTrigger;
    private final Clock clock;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointSecretManager endpointSecretManager;
    private final EndpointCertificateProvider endpointCertificateProvider;
    final Comparator<EligibleJob> oldestFirst = Comparator.comparing(e -> e.deployment.at());
    private final StringFlag endpointCertificateAlgo;
    private final BooleanFlag useAlternateCertProvider;
    private final IntFlag assignRandomizedIdRate;

    @Inject
    public EndpointCertificateMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
        this.deploymentTrigger = controller.applications().deploymentTrigger();
        this.clock = controller.clock();
        this.secretStore = controller.secretStore();
        this.endpointSecretManager = controller.serviceRegistry().secretManager();
        this.curator = controller().curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.assignRandomizedIdRate = Flags.ASSIGNED_RANDOMIZED_ID_RATE.bindTo(controller.flagSource());
    }

    @Override
    protected double maintain() {
        try {
            // In order of importance
            deployRefreshedCertificates();
            updateRefreshedCertificates();
            deleteUnusedCertificates();
            deleteOrReportUnmanagedCertificates();
            assignRandomizedIds();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception caught while maintaining endpoint certificates", e);
            return 1.0;
        }

        return 0.0;
    }

    private void updateRefreshedCertificates() {
        curator.readAssignedCertificates().forEach(assignedCertificate -> {
            // Look for and use refreshed certificate
            var latestAvailableVersion = latestVersionInSecretStore(assignedCertificate.certificate());
            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > assignedCertificate.certificate().version()) {
                var refreshedCertificateMetadata = assignedCertificate.certificate()
                        .withVersion(latestAvailableVersion.getAsInt())
                        .withLastRefreshed(clock.instant().getEpochSecond());

                try (Mutex lock = lock(assignedCertificate.application())) {
                    if (unchanged(assignedCertificate, lock)) {
                        try (NestedTransaction transaction = new NestedTransaction()) {
                            curator.writeAssignedCertificate(assignedCertificate.with(refreshedCertificateMetadata), transaction); // Certificate not validated here, but on deploy.
                            transaction.commit();
                        }
                    }
                }
            }
        });
    }

    private boolean unchanged(AssignedCertificate assignedCertificate, @SuppressWarnings("unused") Mutex lock) {
        return Optional.of(assignedCertificate).equals(curator.readAssignedCertificate(assignedCertificate.application(), assignedCertificate.instance()));
    }

    record EligibleJob(Deployment deployment, ApplicationId applicationId, JobType job) {}

    /**
     * If it's been four days since the cert has been refreshed, re-trigger prod deployment jobs (one at a time).
     */
    private void deployRefreshedCertificates() {
        var now = clock.instant();
        var eligibleJobs = new ArrayList<EligibleJob>();

        curator.readAssignedCertificates().forEach(assignedCertificate ->
                assignedCertificate.certificate().lastRefreshed().ifPresent(lastRefreshTime -> {
                    Instant refreshTime = Instant.ofEpochSecond(lastRefreshTime);
                    if (now.isAfter(refreshTime.plus(4, ChronoUnit.DAYS))) {
                        if (assignedCertificate.instance().isPresent()) {
                            ApplicationId applicationId = assignedCertificate.application().instance(assignedCertificate.instance().get());
                            controller().applications().getInstance(applicationId)
                                        .ifPresent(instance -> instance.productionDeployments().forEach((zone, deployment) -> {
                                            if (deployment.at().isBefore(refreshTime)) {
                                                JobType job = JobType.deploymentTo(zone);
                                                eligibleJobs.add(new EligibleJob(deployment, applicationId, job));
                                            }
                                        }));
                        } else {
                            // This is an application-wide certificate. Trigger all instances
                            controller().applications().getApplication(assignedCertificate.application()).ifPresent(application -> {
                                application.instances().forEach((ignored, i) -> {
                                    i.productionDeployments().forEach((zone, deployment) -> {
                                        if (deployment.at().isBefore(refreshTime)) {
                                            JobType job = JobType.deploymentTo(zone);
                                            eligibleJobs.add(new EligibleJob(deployment, i.id(), job));
                                        }
                                    });
                                });
                            });
                        }
                    }
                }));

        eligibleJobs.stream()
                .min(oldestFirst)
                .ifPresent(e -> {
                    deploymentTrigger.reTrigger(e.applicationId, e.job, "re-triggered by EndpointCertificateMaintainer");
                    log.info("Re-triggering deployment job " + e.job.jobName() + " for instance " +
                             e.applicationId.serializedForm() + " to roll out refreshed endpoint certificate");
                });
    }

    private OptionalInt latestVersionInSecretStore(EndpointCertificate originalCertificateMetadata) {
        try {
            var certVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.certName()));
            var keyVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.keyName()));
            return Sets.intersection(certVersions, keyVersions).stream().mapToInt(Integer::intValue).max();
        } catch (SecretNotFoundException s) {
            return OptionalInt.empty(); // Likely because the certificate is very recently provisioned - keep current version
        }
    }

    private void deleteUnusedCertificates() {
        var oneMonthAgo = clock.instant().minus(30, ChronoUnit.DAYS);
        curator.readAssignedCertificates().forEach(assignedCertificate -> {
            EndpointCertificate certificate = assignedCertificate.certificate();
            var lastRequested = Instant.ofEpochSecond(certificate.lastRequested());
            if (lastRequested.isBefore(oneMonthAgo) && hasNoDeployments(assignedCertificate.application())) {
                try (Mutex lock = lock(assignedCertificate.application())) {
                    if (unchanged(assignedCertificate, lock)) {
                        log.log(Level.INFO, "Cert for app " + asString(assignedCertificate.application(), assignedCertificate.instance())
                                + " has not been requested in a month and app has no deployments, deleting from provider, ZK and secret store");
                        endpointCertificateProvider.deleteCertificate(certificate.rootRequestId());
                        curator.removeAssignedCertificate(assignedCertificate.application(), assignedCertificate.instance());
                        endpointSecretManager.deleteSecret(certificate.certName());
                        endpointSecretManager.deleteSecret(certificate.keyName());
                    }
                }
            }
        });
    }

    private Mutex lock(TenantAndApplicationId application) {
        return curator.lock(application);
    }

    private boolean hasNoDeployments(TenantAndApplicationId application) {
        Optional<Application> app = controller().applications().getApplication(application);
        if (app.isEmpty()) return true;
        for (var instance : app.get().instances().values()) {
            if (!instance.deployments().isEmpty()) return false;
        }
        return true;
    }

    private void deleteOrReportUnmanagedCertificates() {
        List<EndpointCertificateRequest> requests = endpointCertificateProvider.listCertificates();
        List<AssignedCertificate> assignedCertificates = curator.readAssignedCertificates();

        List<String> leafRequestIds = assignedCertificates.stream().map(AssignedCertificate::certificate).flatMap(m -> m.leafRequestId().stream()).toList();
        List<String> rootRequestIds = assignedCertificates.stream().map(AssignedCertificate::certificate).map(EndpointCertificate::rootRequestId).toList();
        List<UnassignedCertificate> unassignedCertificates = curator.readUnassignedCertificates();
        List<String> certPoolRootIds = unassignedCertificates.stream().map(p -> p.certificate().leafRequestId()).flatMap(Optional::stream).toList();
        List<String> certPoolLeafIds = unassignedCertificates.stream().map(p -> p.certificate().rootRequestId()).toList();

        var managedIds = new HashSet<String>();
        managedIds.addAll(leafRequestIds);
        managedIds.addAll(rootRequestIds);
        managedIds.addAll(certPoolRootIds);
        managedIds.addAll(certPoolLeafIds);

        for (var request : requests) {
            if (!managedIds.contains(request.requestId())) {

                // It could just be a refresh we're not aware of yet. See if it matches the cert/keyname of any known cert
                EndpointCertificateDetails unknownCertDetails = endpointCertificateProvider.certificateDetails(request.requestId());
                boolean matchFound = false;
                for (AssignedCertificate assignedCertificate : assignedCertificates) {
                    if (assignedCertificate.certificate().certName().equals(unknownCertDetails.certKeyKeyname())) {
                        matchFound = true;
                        try (Mutex lock = lock(assignedCertificate.application())) {
                            if (unchanged(assignedCertificate, lock)) {
                                log.log(Level.INFO, "Cert for app " + asString(assignedCertificate.application(), assignedCertificate.instance())
                                                    + " has a new leafRequestId " + unknownCertDetails.requestId() + ", updating in ZK");
                                try (NestedTransaction transaction = new NestedTransaction()) {
                                    EndpointCertificate updated = assignedCertificate.certificate().withLeafRequestId(Optional.of(unknownCertDetails.requestId()));
                                    curator.writeAssignedCertificate(assignedCertificate.with(updated), transaction);
                                    transaction.commit();
                                }
                            }
                            break;
                        }
                    }
                }
                if (!matchFound) {
                    // The certificate is not known - however it could be in the process of being requested by us or another controller.
                    // So we only delete if it was requested more than 7 days ago.
                    if (Instant.parse(request.createTime()).isBefore(Instant.now().minus(7, ChronoUnit.DAYS))) {
                        log.log(Level.INFO, String.format("Deleting unmaintained certificate with request_id %s and SANs %s",
                                request.requestId(),
                                request.dnsNames().stream().map(EndpointCertificateRequest.DnsNameStatus::dnsName).collect(Collectors.joining(", "))));
                        endpointCertificateProvider.deleteCertificate(request.requestId());
                    }
                }
            }
        }
    }

    private void assignRandomizedIds() {
        List<AssignedCertificate> assignedCertificates = curator.readAssignedCertificates();
            /*
                only assign randomized id if:
                * instance is present
                * randomized id is not already assigned
                * feature flag is enabled
            */
        assignedCertificates.stream()
                .filter(c -> c.instance().isPresent())
                .filter(c -> c.certificate().generatedId().isEmpty())
                .filter(c -> controller().applications().getApplication(c.application()).isPresent()) // In case application has been deleted, but certificate is pending deletion
                .limit(assignRandomizedIdRate.value())
                .forEach(c -> assignRandomizedId(c.application(), c.instance().get()));
    }

    /*
        Assign randomized id according to these rules:
        * Instance is not mentioned in the deployment spec for this application
            -> assume this is a manual deployment. Assign a randomized id to the certificate, save using instance only
        * Instance is mentioned in deployment spec:
            -> If there is a random endpoint assigned to tenant:application -> use this also for the "instance" certificate
            -> Otherwise assign a random endpoint and write to the application and the instance.
     */
    private void assignRandomizedId(TenantAndApplicationId tenantAndApplicationId, InstanceName instanceName) {
        Optional<AssignedCertificate> assignedCertificate = curator.readAssignedCertificate(tenantAndApplicationId, Optional.of(instanceName));
        if (assignedCertificate.isEmpty()) {
            log.log(Level.INFO, "Assigned certificate missing for " + tenantAndApplicationId.instance(instanceName).toFullString() + " when assigning randomized id");
        }
        // Verify that the assigned certificate still does not have randomized id assigned
        if (assignedCertificate.get().certificate().generatedId().isPresent()) return;

        controller().applications().lockApplicationOrThrow(tenantAndApplicationId, application -> {
            DeploymentSpec deploymentSpec = application.get().deploymentSpec();
            if (deploymentSpec.instance(instanceName).isPresent()) {
                Optional<AssignedCertificate> applicationLevelAssignedCertificate  = curator.readAssignedCertificate(tenantAndApplicationId, Optional.empty());
                assignApplicationRandomId(assignedCertificate.get(), applicationLevelAssignedCertificate);
            } else {
                assignInstanceRandomId(assignedCertificate.get());
            }
        });
    }

    private void assignApplicationRandomId(AssignedCertificate instanceLevelAssignedCertificate, Optional<AssignedCertificate> applicationLevelAssignedCertificate) {
        TenantAndApplicationId tenantAndApplicationId = instanceLevelAssignedCertificate.application();
        if (applicationLevelAssignedCertificate.isPresent()) {
            // Application level assigned certificate with randomized id already exists. Copy randomized id to instance level certificate and request with random names.
            EndpointCertificate withRandomNames = requestRandomNames(
                    tenantAndApplicationId,
                    instanceLevelAssignedCertificate.instance(),
                    applicationLevelAssignedCertificate.get().certificate().generatedId()
                            .orElseThrow(() -> new IllegalArgumentException("Application certificate already assigned to " + tenantAndApplicationId.toString() + ", but random id is missing")),
                    Optional.of(instanceLevelAssignedCertificate.certificate()));
            AssignedCertificate assignedCertWithRandomNames = instanceLevelAssignedCertificate.with(withRandomNames);
            curator.writeAssignedCertificate(assignedCertWithRandomNames);
        } else {
            // No application level certificate exists, generate new assigned certificate with the randomized id based names only, then request same names also for instance level cert
            String randomId = generateRandomId();
            EndpointCertificate applicationLevelEndpointCert = requestRandomNames(tenantAndApplicationId, Optional.empty(), randomId, Optional.empty());
            AssignedCertificate applicationLevelCert = new AssignedCertificate(tenantAndApplicationId, Optional.empty(), applicationLevelEndpointCert);

            EndpointCertificate instanceLevelEndpointCert = requestRandomNames(tenantAndApplicationId, instanceLevelAssignedCertificate.instance(), randomId, Optional.of(instanceLevelAssignedCertificate.certificate()));
            instanceLevelAssignedCertificate = instanceLevelAssignedCertificate.with(instanceLevelEndpointCert);

            // Save both in transaction
            try (NestedTransaction transaction = new NestedTransaction()) {
                curator.writeAssignedCertificate(instanceLevelAssignedCertificate, transaction);
                curator.writeAssignedCertificate(applicationLevelCert, transaction);
                transaction.commit();
            }
        }
    }

    private void assignInstanceRandomId(AssignedCertificate assignedCertificate) {
        String randomId = generateRandomId();
        EndpointCertificate withRandomNames = requestRandomNames(assignedCertificate.application(), assignedCertificate.instance(), randomId, Optional.of(assignedCertificate.certificate()));
        AssignedCertificate assignedCertWithRandomNames = assignedCertificate.with(withRandomNames);
        curator.writeAssignedCertificate(assignedCertWithRandomNames);
    }

    private EndpointCertificate requestRandomNames(TenantAndApplicationId tenantAndApplicationId, Optional<InstanceName> instanceName, String randomId, Optional<EndpointCertificate> previousRequest) {
        String dnsSuffix = Endpoint.dnsSuffix(controller().system());
        List<String> newSanDnsEntries = List.of(
                "*.%s.z%s".formatted(randomId, dnsSuffix),
                "*.%s.g%s".formatted(randomId, dnsSuffix),
                "*.%s.a%s".formatted(randomId, dnsSuffix));
        List<String> existingSanDnsEntries = previousRequest.map(EndpointCertificate::requestedDnsSans).orElse(List.of());
        List<String> requestNames = Stream.concat(existingSanDnsEntries.stream(), newSanDnsEntries.stream()).toList();
        String key = instanceName.map(tenantAndApplicationId::instance).map(ApplicationId::toFullString).orElseGet(tenantAndApplicationId::toString);
        return endpointCertificateProvider.requestCaSignedCertificate(
                        key,
                        requestNames,
                        previousRequest,
                        endpointCertificateAlgo.value(),
                        useAlternateCertProvider.value())
                .withGeneratedId(randomId);
    }

    private String generateRandomId() {
        List<String> unassignedIds = curator.readUnassignedCertificates().stream().map(UnassignedCertificate::id).toList();
        List<String> assignedIds = curator.readAssignedCertificates().stream().map(AssignedCertificate::certificate).map(EndpointCertificate::generatedId).filter(Optional::isPresent).map(Optional::get).toList();
        Set<String> allIds = Stream.concat(unassignedIds.stream(), assignedIds.stream()).collect(Collectors.toSet());
        String randomId;
        do {
            randomId = GeneratedEndpoint.createPart(controller().random(true));
        } while (allIds.contains(randomId));
        return randomId;
    }

    private static String asString(TenantAndApplicationId application, Optional<InstanceName> instanceName) {
        return application.toString() + instanceName.map(name -> "." + name.value()).orElse("");
    }

}
