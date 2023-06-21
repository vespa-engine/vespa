// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Sets;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateDetails;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequestMetadata;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    @Inject
    public EndpointCertificateMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
        this.deploymentTrigger = controller.applications().deploymentTrigger();
        this.clock = controller.clock();
        this.secretStore = controller.secretStore();
        this.endpointSecretManager = controller.serviceRegistry().secretManager();
        this.curator = controller().curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
    }

    @Override
    protected double maintain() {
        try {
            // In order of importance
            deployRefreshedCertificates();
            updateRefreshedCertificates();
            deleteUnusedCertificates();
            deleteOrReportUnmanagedCertificates();
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

    private OptionalInt latestVersionInSecretStore(EndpointCertificateMetadata originalCertificateMetadata) {
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
            EndpointCertificateMetadata certificate = assignedCertificate.certificate();
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
        List<EndpointCertificateRequestMetadata> endpointCertificateMetadata = endpointCertificateProvider.listCertificates();
        List<AssignedCertificate> assignedCertificates = curator.readAssignedCertificates();

        List<String> leafRequestIds = assignedCertificates.stream().map(AssignedCertificate::certificate).flatMap(m -> m.leafRequestId().stream()).toList();
        List<String> rootRequestIds = assignedCertificates.stream().map(AssignedCertificate::certificate).map(EndpointCertificateMetadata::rootRequestId).toList();
        List<UnassignedCertificate> unassignedCertificates = curator.readUnassignedCertificates();
        List<String> certPoolRootIds = unassignedCertificates.stream().map(p -> p.certificate().leafRequestId()).flatMap(Optional::stream).toList();
        List<String> certPoolLeafIds = unassignedCertificates.stream().map(p -> p.certificate().rootRequestId()).toList();

        var managedIds = new HashSet<String>();
        managedIds.addAll(leafRequestIds);
        managedIds.addAll(rootRequestIds);
        managedIds.addAll(certPoolRootIds);
        managedIds.addAll(certPoolLeafIds);

        for (var providerCertificateMetadata : endpointCertificateMetadata) {
            if (!managedIds.contains(providerCertificateMetadata.requestId())) {

                // It could just be a refresh we're not aware of yet. See if it matches the cert/keyname of any known cert
                EndpointCertificateDetails unknownCertDetails = endpointCertificateProvider.certificateDetails(providerCertificateMetadata.requestId());
                boolean matchFound = false;
                for (AssignedCertificate assignedCertificate : assignedCertificates) {
                    if (assignedCertificate.certificate().certName().equals(unknownCertDetails.cert_key_keyname())) {
                        matchFound = true;
                        try (Mutex lock = lock(assignedCertificate.application())) {
                            if (unchanged(assignedCertificate, lock)) {
                                log.log(Level.INFO, "Cert for app " + asString(assignedCertificate.application(), assignedCertificate.instance())
                                        + " has a new leafRequestId " + unknownCertDetails.request_id() + ", updating in ZK");
                                try (NestedTransaction transaction = new NestedTransaction()) {
                                    EndpointCertificateMetadata updated = assignedCertificate.certificate().withLeafRequestId(Optional.of(unknownCertDetails.request_id()));
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
                    if (Instant.parse(providerCertificateMetadata.createTime()).isBefore(Instant.now().minus(7, ChronoUnit.DAYS))) {
                        log.log(Level.INFO, String.format("Deleting unmaintained certificate with request_id %s and SANs %s",
                                providerCertificateMetadata.requestId(),
                                providerCertificateMetadata.dnsNames().stream().map(d -> d.dnsName).collect(Collectors.joining(", "))));
                        endpointCertificateProvider.deleteCertificate(providerCertificateMetadata.requestId());
                    }
                }
            }
        }
    }

    private static String asString(TenantAndApplicationId application, Optional<InstanceName> instanceName) {
        return application.toString() + instanceName.map(name -> "." + name.value()).orElse("");
    }

}
