// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Updates refreshed endpoint certificates and triggers redeployment, and deletes unused certificates.
 *
 * See also EndpointCertificateManager, which provisions, reprovisions and validates certificates on deploy
 *
 * @author andreer
 */
public class EndpointCertificateMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(EndpointCertificateMaintainer.class.getName());

    private final DeploymentTrigger deploymentTrigger;
    private final Clock clock;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;

    public EndpointCertificateMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.all());
        this.deploymentTrigger = controller.applications().deploymentTrigger();
        this.clock = controller.clock();
        this.secretStore = controller.secretStore();
        this.curator = controller().curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
    }

    @Override
    protected boolean maintain() {
        try {
            // In order of importance
            deployRefreshedCertificates();
            updateRefreshedCertificates();
            deleteUnusedCertificates();
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Exception caught while maintaining endpoint certificates", e);
            return false;
        }

        return true;
    }

    private void updateRefreshedCertificates() {
        curator.readAllEndpointCertificateMetadata().forEach(((applicationId, endpointCertificateMetadata) -> {
            // Look for and use refreshed certificate
            var latestAvailableVersion = latestVersionInSecretStore(endpointCertificateMetadata);
            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > endpointCertificateMetadata.version()) {
                var refreshedCertificateMetadata = endpointCertificateMetadata
                        .withVersion(latestAvailableVersion.getAsInt())
                        .withLastRefreshed(clock.instant().getEpochSecond());
                try (Lock lock = lock(applicationId)) {
                    if (Optional.of(endpointCertificateMetadata).equals(curator.readEndpointCertificateMetadata(applicationId))) {
                        curator.writeEndpointCertificateMetadata(applicationId, refreshedCertificateMetadata); // Certificate not validated here, but on deploy.
                    }
                }
            }
        }));
    }

    /**
     * If it's been a week since the cert has been refreshed, re-trigger all prod deployment jobs.
     */
    private void deployRefreshedCertificates() {
        var now = clock.instant();
        curator.readAllEndpointCertificateMetadata().forEach((applicationId, endpointCertificateMetadata) ->
                                                                     endpointCertificateMetadata.lastRefreshed().ifPresent(lastRefreshTime -> {
                                                                         Instant refreshTime = Instant.ofEpochSecond(lastRefreshTime);
                                                                         if (now.isAfter(refreshTime.plus(7, ChronoUnit.DAYS))) {
                                                                             controller().applications().getInstance(applicationId)
                                                                                         .ifPresent(instance -> instance.productionDeployments().forEach((zone, deployment) -> {
                                                                                             if (deployment.at().isBefore(refreshTime)) {
                                                                                                 JobType job = JobType.from(controller().system(), zone).get();
                                                                                                 deploymentTrigger.reTrigger(applicationId, job);
                                                                                                 log.info("Re-triggering deployment job " + job.jobName() + " for instance " +
                                                                                                          applicationId.serializedForm() + " to roll out refreshed endpoint certificate");
                                                                                             }
                                                                                         }));
                                                                         }
                                                                     }));
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
                try (Lock lock = lock(applicationId)) {
                    if (Optional.of(storedMetaData).equals(curator.readEndpointCertificateMetadata(applicationId))) {
                        log.log(Level.INFO, "Cert for app " + applicationId.serializedForm()
                                + " has not been requested in a month and app has no deployments, deleting from provider and ZK");
                        endpointCertificateProvider.deleteCertificate(applicationId, storedMetaData);
                        curator.deleteEndpointCertificateMetadata(applicationId);
                    }
                }
            }
        });
    }

    private Lock lock(ApplicationId applicationId) {
        return curator.lock(TenantAndApplicationId.from(applicationId));
    }

    private boolean hasNoDeployments(ApplicationId applicationId) {
        var deployments = curator.readApplication(TenantAndApplicationId.from(applicationId))
                .flatMap(app -> app.get(applicationId.instance()))
                .map(Instance::deployments);

        return deployments.isEmpty() || deployments.get().size() == 0;
    }

}
