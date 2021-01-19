// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTrigger;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Updates refreshed endpoint certificates and triggers redeployment, and deletes unused certificates
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
    private final BooleanFlag useEndpointCertificateMaintainer;

    public EndpointCertificateMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, SystemName.allOf(Predicate.not(SystemName::isPublic)));
        this.deploymentTrigger = controller.applications().deploymentTrigger();
        this.clock = controller.clock();
        this.secretStore = controller.secretStore();
        this.curator = controller().curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
        this.useEndpointCertificateMaintainer = Flags.USE_ENDPOINT_CERTIFICATE_MAINTAINER.bindTo(controller().flagSource());
    }

    @Override
    protected boolean maintain() {

        if(!useEndpointCertificateMaintainer.value())
            return true; // handled by EndpointCertificateManager for now

        try {
            deleteUnusedCertificates();
            updateRefreshedCertificates();
            // TODO andreer: triggerDeploymentOfExpiringCertificates(); // if less than a week remains and a refreshed cert exist, trigger prod deployments
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "failed lol", e);
            return false;
        }

        return true;
    }

    private void updateRefreshedCertificates() {
        curator.readAllEndpointCertificateMetadata().forEach(((applicationId, endpointCertificateMetadata) -> {
            // Look for and use refreshed certificate
            var latestAvailableVersion = latestVersionInSecretStore(endpointCertificateMetadata);
            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > endpointCertificateMetadata.version()) {
                var refreshedCertificateMetadata = endpointCertificateMetadata.withVersion(latestAvailableVersion.getAsInt());
                curator.writeEndpointCertificateMetadata(applicationId, refreshedCertificateMetadata);
                // TODO andreer: We need to store refreshed timestamp to know whether this has been deployed(?)
                // TODO andreer: Certificate not validated here! Is that OK?
                deploymentTrigger.triggerChange(applicationId, Change.empty()); // TODO andreer: No idea if this does what I want, or anything at all. NO: trigger prod jobs directly, when required
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
                log.log(Level.INFO, "Cert for app " + applicationId.serializedForm()
                        + " has not been requested in a month and app has no deployments, deleting from provider and ZK");
                endpointCertificateProvider.deleteCertificate(applicationId, storedMetaData);
                curator.deleteEndpointCertificateMetadata(applicationId);
            }
        });
    }

    private boolean hasNoDeployments(ApplicationId applicationId) {
        var deployments = curator.readApplication(TenantAndApplicationId.from(applicationId))
                .flatMap(app -> app.get(applicationId.instance()))
                .map(Instance::deployments);

        return deployments.isEmpty() || deployments.get().size() == 0;
    }

}
