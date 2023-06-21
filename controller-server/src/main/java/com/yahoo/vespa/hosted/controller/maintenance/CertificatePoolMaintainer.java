// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.certificate.UnassignedCertificate;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.certificate.AssignedCertificate;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Manages pool of ready-to-use randomized endpoint certificates
 *
 * @author andreer
 */
public class CertificatePoolMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(CertificatePoolMaintainer.class.getName());

    private final RandomGenerator random;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Metric metric;
    private final Controller controller;
    private final IntFlag certPoolSize;
    private final String dnsSuffix;
    private final StringFlag endpointCertificateAlgo;
    private final BooleanFlag useAlternateCertProvider;

    public CertificatePoolMaintainer(Controller controller, Metric metric, Duration interval, RandomGenerator random) {
        super(controller, interval, null, Set.of(SystemName.Public, SystemName.PublicCd));
        this.controller = controller;
        this.secretStore = controller.secretStore();
        this.certPoolSize = Flags.CERT_POOL_SIZE.bindTo(controller.flagSource());
        this.useAlternateCertProvider = PermanentFlags.USE_ALTERNATIVE_ENDPOINT_CERTIFICATE_PROVIDER.bindTo(controller.flagSource());
        this.endpointCertificateAlgo = PermanentFlags.ENDPOINT_CERTIFICATE_ALGORITHM.bindTo(controller.flagSource());
        this.curator = controller.curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
        this.metric = metric;
        this.dnsSuffix = Endpoint.dnsSuffix(controller.system());
        this.random = random;
    }

    protected double maintain() {
        try {
            moveRequestedCertsToReady();
            List<UnassignedCertificate> certificatePool = curator.readUnassignedCertificates();
            // So we can alert if the pool goes too low
            metric.set("preprovisioned.endpoint.certificates", certificatePool.stream().filter(c -> c.state() == UnassignedCertificate.State.ready).count(), metric.createContext(Map.of()));
            if (certificatePool.size() < certPoolSize.value()) {
                provisionRandomizedCertificate();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception caught while maintaining pool of unused randomized endpoint certs", e);
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
                        log.log(Level.INFO, "Randomized endpoint cert %s now ready for use".formatted(cert.id()));
                    }
                } catch (SecretNotFoundException s) {
                    // Likely because the certificate is very recently provisioned - ignore till next time - should we log?
                    log.log(Level.INFO, "Could not yet read secrets for randomized endpoint cert %s - maybe next time ...".formatted(cert.id()));
                }
            }
        }
    }

    private void provisionRandomizedCertificate() {
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            Set<String> existingNames = controller.curator().readUnassignedCertificates().stream().map(UnassignedCertificate::id).collect(Collectors.toSet());

            curator.readAssignedCertificates().stream()
                   .map(AssignedCertificate::certificate)
                   .map(EndpointCertificateMetadata::randomizedId)
                   .forEach(id -> id.ifPresent(existingNames::add));

            String id = generateRandomId();
            while (existingNames.contains(id)) id = generateRandomId();

            EndpointCertificateMetadata f = endpointCertificateProvider.requestCaSignedCertificate(
                            "preprovisioned.%s".formatted(id),
                            List.of(
                                    "*.%s.z%s".formatted(id, dnsSuffix),
                                    "*.%s.g%s".formatted(id, dnsSuffix),
                                    "*.%s.a%s".formatted(id, dnsSuffix)
                            ),
                            Optional.empty(),
                            endpointCertificateAlgo.value(),
                            useAlternateCertProvider.value())
                    .withRandomizedId(id);

            UnassignedCertificate certificate = new UnassignedCertificate(f, UnassignedCertificate.State.requested);
            curator.writeUnassignedCertificate(certificate);
        }
    }

    private String generateRandomId() {
        String alphabet = "abcdef0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(alphabet.charAt(random.nextInt(6))); // start with letter
        for (int i = 0; i < 7; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
