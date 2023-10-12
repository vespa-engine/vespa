// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import ai.vespa.metrics.ControllerMetrics;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificate;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.application.GeneratedEndpoint;
import com.yahoo.vespa.hosted.controller.certificate.AssignedCertificate;
import com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages a pool of ready-to-use endpoint certificates.
 *
 * @author andreer
 */
public class CertificatePoolMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(CertificatePoolMaintainer.class.getName());

    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Metric metric;
    private final Controller controller;
    private final IntFlag certPoolSize;
    private final StringFlag endpointCertificateAlgo;
    private final BooleanFlag useAlternateCertProvider;

    public CertificatePoolMaintainer(Controller controller, Metric metric, Duration interval) {
        super(controller, interval);
        this.controller = controller;
        this.secretStore = controller.secretStore();
        this.certPoolSize = PermanentFlags.CERT_POOL_SIZE.bindTo(controller.flagSource());
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.curator = controller.curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
        this.metric = metric;
    }

    protected double maintain() {
        try {
            moveRequestedCertsToReady();
            List<UnassignedCertificate> certificatePool = curator.readUnassignedCertificates();

            // Create metric for available certificates in the pool as a fraction of configured size
            int poolSize = certPoolSize.value();
            long available = certificatePool.stream().filter(c -> c.state() == UnassignedCertificate.State.ready).count();
            metric.set(ControllerMetrics.CERTIFICATE_POOL_AVAILABLE.baseName(), (poolSize > 0 ? ((double)available/poolSize) : 1.0), metric.createContext(Map.of()));

            if (certificatePool.size() < poolSize) {
                provisionCertificate();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to maintain certificate pool", e);
            return 1.0;
        }
        return 0.0;
    }

    private void moveRequestedCertsToReady() {
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            for (UnassignedCertificate cert : curator.readUnassignedCertificates()) {
                if (cert.state() == UnassignedCertificate.State.ready) continue;
                try {
                    OptionalInt maxKeyVersion = secretStore.listSecretVersions(cert.certificate().keyName()).stream().mapToInt(i -> i).max();
                    OptionalInt maxCertVersion = secretStore.listSecretVersions(cert.certificate().certName()).stream().mapToInt(i -> i).max();
                    if (maxKeyVersion.isPresent() && maxCertVersion.equals(maxKeyVersion)) {
                        curator.writeUnassignedCertificate(cert.withState(UnassignedCertificate.State.ready));
                        log.log(Level.INFO, "Readied certificate %s".formatted(cert.id()));
                    }
                } catch (SecretNotFoundException s) {
                    // Likely because the certificate is very recently provisioned - ignore till next time - should we log?
                    log.log(Level.INFO, "Cannot ready certificate %s yet, will retry in %s".formatted(cert.id(), interval()));
                }
            }
        }
    }

    private void provisionCertificate() {
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            Set<String> existingNames = controller.curator().readUnassignedCertificates().stream().map(UnassignedCertificate::id).collect(Collectors.toSet());

            curator.readAssignedCertificates().stream()
                   .map(AssignedCertificate::certificate)
                   .map(EndpointCertificate::generatedId)
                   .forEach(id -> id.ifPresent(existingNames::add));

            String id = generateId();
            while (existingNames.contains(id)) id = generateId();
            List<String> dnsNames = wildcardDnsNames(id);
            EndpointCertificate cert = endpointCertificateProvider.requestCaSignedCertificate(
                    "preprovisioned.%s".formatted(id),
                    dnsNames,
                    Optional.empty(),
                    endpointCertificateAlgo.value(),
                    useAlternateCertProvider.value()).withGeneratedId(id);

            UnassignedCertificate certificate = new UnassignedCertificate(cert, UnassignedCertificate.State.requested);
            curator.writeUnassignedCertificate(certificate);
        }
    }

    private List<String> wildcardDnsNames(String id) {
        DeploymentId defaultDeployment = new DeploymentId(ApplicationId.defaultId(), ZoneId.defaultId());
        return controller.routing().certificateDnsNames(defaultDeployment,    // Not used for non-legacy names
                                                        DeploymentSpec.empty, // Not used for non-legacy names
                                                        id,
                                                        false);
    }

    private String generateId() {
        return GeneratedEndpoint.createPart(controller.random(true));
    }

}
