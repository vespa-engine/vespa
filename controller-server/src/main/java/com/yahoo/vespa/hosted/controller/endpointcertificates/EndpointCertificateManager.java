package com.yahoo.vespa.hosted.controller.endpointcertificates;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.log.LogLevel;
import com.yahoo.security.SubjectAlternativeName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateMetadata;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Looks up stored endpoint certificate metadata, provisions new certificates if none is found,
 * and refreshes certificates if a newer version is available.
 *
 * @author andreer
 */
public class EndpointCertificateManager {

    private static final Logger log = Logger.getLogger(EndpointCertificateManager.class.getName());

    private final ZoneRegistry zoneRegistry;
    private final CuratorDb curator;
    private final SecretStore secretStore;
    private final EndpointCertificateProvider endpointCertificateProvider;
    private final Clock clock;
    private final BooleanFlag useRefreshedEndpointCertificate;
    private final StringFlag endpointCertificateBackfill;

    public EndpointCertificateManager(ZoneRegistry zoneRegistry,
                                      CuratorDb curator,
                                      SecretStore secretStore,
                                      EndpointCertificateProvider endpointCertificateProvider,
                                      Clock clock, FlagSource flagSource) {
        this.zoneRegistry = zoneRegistry;
        this.curator = curator;
        this.secretStore = secretStore;
        this.endpointCertificateProvider = endpointCertificateProvider;
        this.clock = clock;
        this.useRefreshedEndpointCertificate = Flags.USE_REFRESHED_ENDPOINT_CERTIFICATE.bindTo(flagSource);
        this.endpointCertificateBackfill = Flags.ENDPOINT_CERTIFICATE_BACKFILL.bindTo(flagSource);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.backfillCertificateMetadata();
            } catch (Throwable t) {
                log.log(LogLevel.INFO, "Unexpected Throwable caught while backfilling certificate metadata", t);
            }
        }, 1, 10, TimeUnit.MINUTES);
    }

    public Optional<EndpointCertificateMetadata> getEndpointCertificateMetadata(Instance instance, ZoneId zone) {

        if (!zoneRegistry.zones().directlyRouted().ids().contains(zone)) return Optional.empty();

        // Re-use existing certificate if already provisioned
        var endpointCertificateMetadata =
                curator.readEndpointCertificateMetadata(instance.id())
                        .orElseGet(() -> provisionEndpointCertificate(instance));

        // If feature flag set for application, look for and use refreshed certificate
        if (useRefreshedEndpointCertificate.with(FetchVector.Dimension.APPLICATION_ID, instance.id().serializedForm()).value()) {
            var latestAvailableVersion = latestVersionInSecretStore(endpointCertificateMetadata);

            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > endpointCertificateMetadata.version()) {
                var refreshedCertificateMetadata = new EndpointCertificateMetadata(
                        endpointCertificateMetadata.keyName(),
                        endpointCertificateMetadata.certName(),
                        latestAvailableVersion.getAsInt()
                );

                if (verifyEndpointCertificate(refreshedCertificateMetadata, instance, zone, "Did not refresh, problems with refreshed certificate: "))
                    return Optional.of(refreshedCertificateMetadata);
            }
        }

        // Only log warnings
        verifyEndpointCertificate(endpointCertificateMetadata, instance, zone, "Problems while verifying certificate: ");

        return Optional.of(endpointCertificateMetadata);
    }

    enum BackfillMode {
        DISABLE,
        DRYRUN,
        ENABLE
    }

    private void backfillCertificateMetadata() {
        BackfillMode mode = BackfillMode.valueOf(endpointCertificateBackfill.value());
        if (mode == BackfillMode.DISABLE) return;

        List<EndpointCertificateMetadata> allProviderCertificateMetadata = endpointCertificateProvider.listCertificates();
        Map<String, EndpointCertificateMetadata> sanToEndpointCertificate = new HashMap<>();

        allProviderCertificateMetadata.forEach((providerMetadata -> {
            if (providerMetadata.request_id().isEmpty())
                throw new RuntimeException("Backfill failed - provider metadata missing request_id");
            if (providerMetadata.requestedDnsSans().isEmpty())
                throw new RuntimeException("Backfill failed - provider metadata missing DNS SANs for " + providerMetadata.request_id().get());
            providerMetadata.requestedDnsSans().get().forEach(san -> sanToEndpointCertificate.put(san, providerMetadata)
            );
        }));

        Map<ApplicationId, EndpointCertificateMetadata> allEndpointCertificateMetadata = curator.readAllEndpointCertificateMetadata();

        allEndpointCertificateMetadata.forEach((applicationId, storedMetaData) -> {
            if (storedMetaData.requestedDnsSans().isPresent() && storedMetaData.request_id().isPresent())
                return;

            var hashedCn = Endpoint.createHashedCn(applicationId, zoneRegistry.system()); // use as join key
            EndpointCertificateMetadata providerMetadata = sanToEndpointCertificate.get(hashedCn);

            if(providerMetadata == null) {
                log.log(LogLevel.INFO, "No matching certificate provider metadata found for application " + applicationId.serializedForm());
                return;
            }

            EndpointCertificateMetadata backfilledMetadata =
                    new EndpointCertificateMetadata(
                            storedMetaData.keyName(),
                            storedMetaData.certName(),
                            storedMetaData.version(),
                            providerMetadata.request_id(),
                            providerMetadata.requestedDnsSans());

            if (mode == BackfillMode.DRYRUN) {
                log.log(LogLevel.INFO, "Would update stored metadata " + storedMetaData + " with data from provider: " + backfilledMetadata);
            } else if (mode == BackfillMode.ENABLE) {
                curator.writeEndpointCertificateMetadata(applicationId, backfilledMetadata);
            }
        });
    }

    private OptionalInt latestVersionInSecretStore(EndpointCertificateMetadata originalCertificateMetadata) {
        var certVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.certName()));
        var keyVersions = new HashSet<>(secretStore.listSecretVersions(originalCertificateMetadata.keyName()));

        return Sets.intersection(certVersions, keyVersions).stream().mapToInt(Integer::intValue).max();
    }

    private EndpointCertificateMetadata provisionEndpointCertificate(Instance instance) {
        List<ZoneId> directlyRoutedZones = zoneRegistry.zones().directlyRouted().zones().stream().map(ZoneApi::getId).collect(Collectors.toUnmodifiableList());
        EndpointCertificateMetadata provisionedCertificateMetadata = endpointCertificateProvider
                .requestCaSignedCertificate(instance.id(), dnsNamesOf(instance.id(), directlyRoutedZones));
        curator.writeEndpointCertificateMetadata(instance.id(), provisionedCertificateMetadata);
        return provisionedCertificateMetadata;
    }

    private boolean verifyEndpointCertificate(EndpointCertificateMetadata endpointCertificateMetadata, Instance instance, ZoneId zone, String warningPrefix) {
        try {
            var pemEncodedEndpointCertificate = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());

            if (pemEncodedEndpointCertificate == null)
                return logWarning(warningPrefix, "Secret store returned null for certificate");

            List<X509Certificate> x509CertificateList = X509CertificateUtils.certificateListFromPem(pemEncodedEndpointCertificate);

            if (x509CertificateList.isEmpty()) return logWarning(warningPrefix, "Empty certificate list");
            if (x509CertificateList.size() < 2)
                return logWarning(warningPrefix, "Only a single certificate found in chain - intermediate certificates likely missing");

            Instant now = clock.instant();
            Instant firstExpiry = Instant.MAX;
            for (X509Certificate x509Certificate : x509CertificateList) {
                Instant notBefore = x509Certificate.getNotBefore().toInstant();
                Instant notAfter = x509Certificate.getNotAfter().toInstant();
                if (now.isBefore(notBefore)) return logWarning(warningPrefix, "Certificate is not yet valid");
                if (now.isAfter(notAfter)) return logWarning(warningPrefix, "Certificate has expired");
                if (notAfter.isBefore(firstExpiry)) firstExpiry = notAfter;
            }

            X509Certificate endEntityCertificate = x509CertificateList.get(0);
            Set<String> subjectAlternativeNames = X509CertificateUtils.getSubjectAlternativeNames(endEntityCertificate).stream()
                    .filter(san -> san.getType().equals(SubjectAlternativeName.Type.DNS_NAME))
                    .map(SubjectAlternativeName::getValue).collect(Collectors.toSet());

            if (Sets.intersection(subjectAlternativeNames, Set.copyOf(dnsNamesOf(instance.id(), List.of(zone)))).isEmpty()) {
                return logWarning(warningPrefix, "No overlap between SANs in certificate and expected SANs");
            }

            return true; // All good then, hopefully
        } catch (SecretNotFoundException s) {
            return logWarning(warningPrefix, "Certificate not found in secret store");
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Exception thrown when verifying endpoint certificate", e);
            return false;
        }
    }

    private static boolean logWarning(String warningPrefix, String message) {
        log.log(LogLevel.WARNING, warningPrefix + message);
        return false;
    }

    private List<String> dnsNamesOf(ApplicationId applicationId, List<ZoneId> zones) {
        List<String> endpointDnsNames = new ArrayList<>();

        // We add first an endpoint name based on a hash of the applicationId,
        // as the certificate provider requires the first CN to be < 64 characters long.
        endpointDnsNames.add(Endpoint.createHashedCn(applicationId, zoneRegistry.system()));

        var globalDefaultEndpoint = Endpoint.of(applicationId).named(EndpointId.defaultId());
        var rotationEndpoints = Endpoint.of(applicationId).wildcard();

        var zoneLocalEndpoints = zones.stream().flatMap(zone -> Stream.of(
                Endpoint.of(applicationId).target(ClusterSpec.Id.from("default"), zone),
                Endpoint.of(applicationId).wildcard(zone)
        ));

        Stream.concat(Stream.of(globalDefaultEndpoint, rotationEndpoints), zoneLocalEndpoints)
                .map(Endpoint.EndpointBuilder::directRouting)
                .map(endpoint -> endpoint.on(Endpoint.Port.tls()))
                .map(endpointBuilder -> endpointBuilder.in(zoneRegistry.system()))
                .map(Endpoint::dnsName).forEach(endpointDnsNames::add);

        return Collections.unmodifiableList(endpointDnsNames);
    }

}
