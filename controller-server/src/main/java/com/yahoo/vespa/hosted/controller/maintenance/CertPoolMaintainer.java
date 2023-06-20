// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import static com.yahoo.vespa.hosted.controller.maintenance.CertPoolMaintainer.CertificatePool.ready_to_use;
import static com.yahoo.vespa.hosted.controller.maintenance.CertPoolMaintainer.CertificatePool.requested;

/**
 * Manages pool of ready-to-use randomized endpoint certificates
 *
 * @author andreer
 */
public class CertPoolMaintainer extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(CertPoolMaintainer.class.getName());

    private final RandomGenerator random;
    private final Clock clock;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Metric metric;
    private final Controller controller;
    private final IntFlag certPoolSize;
    private final String dnsSuffix;

    @Inject
    public CertPoolMaintainer(Controller controller, Metric metric, Duration interval) {
        this(controller, metric, interval, new SecureRandom());
    }

    public CertPoolMaintainer(Controller controller, Metric metric, Duration interval, RandomGenerator rng) {
        super(controller, interval);
        this.controller = controller;
        this.clock = controller.clock();
        this.secretStore = controller.secretStore();
        this.certPoolSize = Flags.CERT_POOL_SIZE.bindTo(controller.flagSource());
        this.curator = controller.curator();
        this.endpointCertificateProvider = controller.serviceRegistry().endpointCertificateProvider();
        this.metric = metric;
        this.dnsSuffix = Endpoint.dnsSuffix(controller.system());
        this.random = rng;
    }

    protected double maintain() {
        try {
            moveRequestedCertsToReady();

            // So we can alert if the pool goes too low
            metric.set("preprovisioned.endpoint.certificates", pool(ready_to_use).size(), metric.createContext(Map.of()));

            if (pool(ready_to_use).size() + pool(requested).size() < certPoolSize.value()) {
                provisionRandomizedCertificate();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception caught while maintaining pool of unused randomized endpoint certs", e);
            return 1.0;
        }
        return 0.0;
    }

    private void moveRequestedCertsToReady() {
        for (var cert : pool(requested).entrySet()) {
            try {
                OptionalInt maxKeyVersion = secretStore.listSecretVersions(cert.getValue().keyName()).stream().mapToInt(i -> i).max();
                OptionalInt maxCertVersion = secretStore.listSecretVersions(cert.getValue().certName()).stream().mapToInt(i -> i).max();

                if (maxKeyVersion.isPresent() && maxCertVersion.equals(maxKeyVersion)) {
                    try (Mutex lock = controller.curator().lockCertificatePool()) {
                        curator.removeFromCertificatePool(cert.getKey(), requested.name());
                        curator.addToCertificatePool(cert.getKey(), cert.getValue(), ready_to_use.name());

                        log.log(Level.INFO, "Randomized endpoint cert %s now ready for use".formatted(cert.getKey()));
                    }
                }
            } catch (SecretNotFoundException s) {
                // Likely because the certificate is very recently provisioned - ignore till next time - should we log?
                log.log(Level.INFO, "Could not yet read secrets for randomized endpoint cert %s - maybe next time ...".formatted(cert.getKey()));
            }
        }
    }

    enum CertificatePool {
        ready_to_use,
        requested
    }

    private Map<String, EndpointCertificateMetadata> pool(CertificatePool pool) {
        return curator.readCertificatePool(pool.name());
    }

    private void provisionRandomizedCertificate() {
        try (Mutex lock = controller.curator().lockCertificatePool()) {
            HashSet<String> existingNames = new HashSet<>();
            existingNames.addAll(pool(ready_to_use).keySet());
            existingNames.addAll(pool(requested).keySet());
            curator.readAllEndpointCertificateMetadata().values().stream()
                    .map(EndpointCertificateMetadata::randomizedId)
                    .forEach(id -> id.ifPresent(existingNames::add));

            String s = randomIdentifier();
            while (existingNames.contains(s)) s = randomIdentifier();

            EndpointCertificateMetadata f = endpointCertificateProvider.requestCaSignedCertificate(
                            ApplicationId.from("randomized", "endpoint", s), // TODO andreer: remove applicationId from this interface
                            List.of(
                                    "*.%s.z%s".formatted(s, dnsSuffix),
                                    "*.%s.g%s".formatted(s, dnsSuffix),
                                    "*.%s.a%s".formatted(s, dnsSuffix)
                            ),
                            Optional.empty())
                    .withRandomizedId(s);

            curator.addToCertificatePool(s, f, requested.name());
        }
    }

    private String randomIdentifier() {
        String alphabet = "abcdef0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(alphabet.charAt(random.nextInt(6))); // start with letter
        for (int i = 0; i < 7; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
