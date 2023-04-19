// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Sets;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateDetails;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateRequestMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.EndpointSecretManager;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
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
import java.util.Map;
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
        curator.readAllEndpointCertificateMetadata().forEach(((applicationId, endpointCertificateMetadata) -> {
            // Look for and use refreshed certificate
            var latestAvailableVersion = latestVersionInSecretStore(endpointCertificateMetadata);
            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > endpointCertificateMetadata.version()) {
                var refreshedCertificateMetadata = endpointCertificateMetadata
                        .withVersion(latestAvailableVersion.getAsInt())
                        .withLastRefreshed(clock.instant().getEpochSecond());
                try (Mutex lock = lock(applicationId)) {
                    if (Optional.of(endpointCertificateMetadata).equals(curator.readEndpointCertificateMetadata(applicationId))) {
                        curator.writeEndpointCertificateMetadata(applicationId, refreshedCertificateMetadata); // Certificate not validated here, but on deploy.
                    }
                }
            }
        }));
    }

    record EligibleJob(Deployment deployment, ApplicationId applicationId, JobType job) {}
    /**
     * If it's been four days since the cert has been refreshed, re-trigger prod deployment jobs (one at a time).
     */
    private void deployRefreshedCertificates() {
        var now = clock.instant();
        var eligibleJobs = new ArrayList<EligibleJob>();

        curator.readAllEndpointCertificateMetadata().forEach((applicationId, endpointCertificateMetadata) ->
                endpointCertificateMetadata.lastRefreshed().ifPresent(lastRefreshTime -> {
                    Instant refreshTime = Instant.ofEpochSecond(lastRefreshTime);
                    if (now.isAfter(refreshTime.plus(4, ChronoUnit.DAYS))) {
                        controller().applications().getInstance(applicationId)
                                .ifPresent(instance -> instance.productionDeployments().forEach((zone, deployment) -> {
                                    if (deployment.at().isBefore(refreshTime)) {
                                        JobType job = JobType.deploymentTo(zone);
                                        eligibleJobs.add(new EligibleJob(deployment, applicationId, job));
                                    }
                                }));
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
        curator.readAllEndpointCertificateMetadata().forEach((applicationId, storedMetaData) -> {
            var lastRequested = Instant.ofEpochSecond(storedMetaData.lastRequested());
            if (lastRequested.isBefore(oneMonthAgo) && hasNoDeployments(applicationId)) {
                try (Mutex lock = lock(applicationId)) {
                    if (Optional.of(storedMetaData).equals(curator.readEndpointCertificateMetadata(applicationId))) {
                        log.log(Level.INFO, "Cert for app " + applicationId.serializedForm()
                                + " has not been requested in a month and app has no deployments, deleting from provider, ZK and secret store");
                        endpointCertificateProvider.deleteCertificate(applicationId, storedMetaData.rootRequestId());
                        curator.deleteEndpointCertificateMetadata(applicationId);
                        endpointSecretManager.deleteSecret(storedMetaData.certName());
                        endpointSecretManager.deleteSecret(storedMetaData.keyName());
                    }
                }
            }
        });
    }

    private Mutex lock(ApplicationId applicationId) {
        return curator.lock(TenantAndApplicationId.from(applicationId));
    }

    private boolean hasNoDeployments(ApplicationId applicationId) {
        return controller().applications().getInstance(applicationId)
                .map(Instance::deployments)
                .orElseGet(Map::of)
                .isEmpty();
    }

    private void deleteOrReportUnmanagedCertificates() {
        List<EndpointCertificateRequestMetadata> endpointCertificateMetadata = endpointCertificateProvider.listCertificates();
        Map<ApplicationId, EndpointCertificateMetadata> storedEndpointCertificateMetadata = curator.readAllEndpointCertificateMetadata();

        List<String> leafRequestIds = storedEndpointCertificateMetadata.values().stream().flatMap(m -> m.leafRequestId().stream()).toList();
        List<String> rootRequestIds = storedEndpointCertificateMetadata.values().stream().map(EndpointCertificateMetadata::rootRequestId).toList();

        for (var providerCertificateMetadata : endpointCertificateMetadata) {
            if (!rootRequestIds.contains(providerCertificateMetadata.requestId()) && !leafRequestIds.contains(providerCertificateMetadata.requestId())) {

                // It could just be a refresh we're not aware of yet. See if it matches the cert/keyname of any known cert
                EndpointCertificateDetails unknownCertDetails = endpointCertificateProvider.certificateDetails(providerCertificateMetadata.requestId());
                boolean matchFound = false;
                for (Map.Entry<ApplicationId, EndpointCertificateMetadata> storedAppEntry : storedEndpointCertificateMetadata.entrySet()) {
                    ApplicationId storedApp = storedAppEntry.getKey();
                    EndpointCertificateMetadata storedAppMetadata = storedAppEntry.getValue();
                    if (storedAppMetadata.certName().equals(unknownCertDetails.cert_key_keyname())) {
                        matchFound = true;
                        try (Mutex lock = lock(storedApp)) {
                            if (Optional.of(storedAppMetadata).equals(curator.readEndpointCertificateMetadata(storedApp))) {
                                log.log(Level.INFO, "Cert for app " + storedApp.serializedForm()
                                        + " has a new leafRequestId " + unknownCertDetails.request_id() + ", updating in ZK");
                                curator.writeEndpointCertificateMetadata(storedApp, storedAppMetadata.withLeafRequestId(Optional.of(unknownCertDetails.request_id())));
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
                        endpointCertificateProvider.deleteCertificate(ApplicationId.fromSerializedForm("applicationid:is:unknown"), providerCertificateMetadata.requestId());
                    }
                }
            }
        }
    }
}
