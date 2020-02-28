package com.yahoo.vespa.hosted.controller.certificate;

import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
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

import java.nio.charset.Charset;
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
 * re-provisions if zone is not covered, and uses refreshed certificates if a newer version is available.
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
    private final BooleanFlag validateEndpointCertificates;
    private final StringFlag endpointCertificateBackfill;
    private final BooleanFlag endpointCertInSharedRouting;

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
        this.validateEndpointCertificates = Flags.VALIDATE_ENDPOINT_CERTIFICATES.bindTo(flagSource);
        this.endpointCertificateBackfill = Flags.ENDPOINT_CERTIFICATE_BACKFILL.bindTo(flagSource);
        this.endpointCertInSharedRouting = Flags.ENDPOINT_CERT_IN_SHARED_ROUTING.bindTo(flagSource);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                this.backfillCertificateMetadata();
            } catch (Throwable t) {
                log.log(LogLevel.INFO, "Unexpected Throwable caught while backfilling certificate metadata", t);
            }
        }, 1, 10, TimeUnit.MINUTES);
    }

    public Optional<EndpointCertificateMetadata> getEndpointCertificateMetadata(Instance instance, ZoneId zone) {

        boolean endpointCertInSharedRouting = this.endpointCertInSharedRouting.with(FetchVector.Dimension.APPLICATION_ID, instance.id().serializedForm()).value();
        if (!zoneRegistry.zones().directlyRouted().ids().contains(zone) && !endpointCertInSharedRouting)
            return Optional.empty();

        final var currentCertificateMetadata = curator.readEndpointCertificateMetadata(instance.id());

        if (currentCertificateMetadata.isEmpty()) {
            var provisionedCertificateMetadata = provisionEndpointCertificate(instance, Optional.empty());
            // We do not verify the certificate if one has never existed before - because we do not want to
            // wait for it to be available before we deploy. This allows the config server to start
            // provisioning nodes ASAP, and the risk is small for a new deployment.
            curator.writeEndpointCertificateMetadata(instance.id(), provisionedCertificateMetadata);
            return Optional.of(provisionedCertificateMetadata);
        }

        // Reprovision certificate if it is missing SANs for the zone we are deploying to
        var sansInCertificate = currentCertificateMetadata.get().requestedDnsSans();
        var requiredSansForZone = dnsNamesOf(instance.id(), List.of(zone));
        if (sansInCertificate.isPresent() && !sansInCertificate.get().containsAll(requiredSansForZone)) {
            var reprovisionedCertificateMetadata = provisionEndpointCertificate(instance, currentCertificateMetadata);
            curator.writeEndpointCertificateMetadata(instance.id(), reprovisionedCertificateMetadata);
            // Verification is unlikely to succeed in this case, as certificate must be available first - controller will retry
            validateEndpointCertificate(reprovisionedCertificateMetadata, instance, zone);
            return Optional.of(reprovisionedCertificateMetadata);
        }

        // If feature flag set for application, look for and use refreshed certificate
        if (useRefreshedEndpointCertificate.with(FetchVector.Dimension.APPLICATION_ID, instance.id().serializedForm()).value()) {
            var latestAvailableVersion = latestVersionInSecretStore(currentCertificateMetadata.get());

            if (latestAvailableVersion.isPresent() && latestAvailableVersion.getAsInt() > currentCertificateMetadata.get().version()) {
                var refreshedCertificateMetadata = currentCertificateMetadata.get().withVersion(latestAvailableVersion.getAsInt());
                validateEndpointCertificate(refreshedCertificateMetadata, instance, zone);
                curator.writeEndpointCertificateMetadata(instance.id(), refreshedCertificateMetadata);
                return Optional.of(refreshedCertificateMetadata);
            }
        }

        validateEndpointCertificate(currentCertificateMetadata.get(), instance, zone);
        return currentCertificateMetadata;
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

            var hashedCn = commonNameHashOf(applicationId, zoneRegistry.system()); // use as join key
            EndpointCertificateMetadata providerMetadata = sanToEndpointCertificate.get(hashedCn);

            if (providerMetadata == null) {
                log.log(LogLevel.INFO, "No matching certificate provider metadata found for application " + applicationId.serializedForm());
                return;
            }

            EndpointCertificateMetadata backfilledMetadata =
                    new EndpointCertificateMetadata(
                            storedMetaData.keyName(),
                            storedMetaData.certName(),
                            storedMetaData.version(),
                            providerMetadata.request_id(),
                            providerMetadata.requestedDnsSans(),
                            Optional.empty());

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

    private EndpointCertificateMetadata provisionEndpointCertificate(Instance instance, Optional<EndpointCertificateMetadata> currentMetadata) {
        List<ZoneId> zones = zoneRegistry.zones().controllerUpgraded().zones().stream().map(ZoneApi::getId).collect(Collectors.toUnmodifiableList());
        return endpointCertificateProvider.requestCaSignedCertificate(instance.id(), dnsNamesOf(instance.id(), zones), currentMetadata);
    }

    private void validateEndpointCertificate(EndpointCertificateMetadata endpointCertificateMetadata, Instance instance, ZoneId zone) {
        if (validateEndpointCertificates.value())
            try {
                var pemEncodedEndpointCertificate = secretStore.getSecret(endpointCertificateMetadata.certName(), endpointCertificateMetadata.version());

                if (pemEncodedEndpointCertificate == null)
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Secret store returned null for certificate");

                List<X509Certificate> x509CertificateList = X509CertificateUtils.certificateListFromPem(pemEncodedEndpointCertificate);

                if (x509CertificateList.isEmpty())
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Empty certificate list");
                if (x509CertificateList.size() < 2)
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Only a single certificate found in chain - intermediate certificates likely missing");

                Instant now = clock.instant();
                Instant firstExpiry = Instant.MAX;
                for (X509Certificate x509Certificate : x509CertificateList) {
                    Instant notBefore = x509Certificate.getNotBefore().toInstant();
                    Instant notAfter = x509Certificate.getNotAfter().toInstant();
                    if (now.isBefore(notBefore))
                        throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is not yet valid");
                    if (now.isAfter(notAfter))
                        throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate has expired");
                    if (notAfter.isBefore(firstExpiry)) firstExpiry = notAfter;
                }

                X509Certificate endEntityCertificate = x509CertificateList.get(0);
                Set<String> subjectAlternativeNames = X509CertificateUtils.getSubjectAlternativeNames(endEntityCertificate).stream()
                        .filter(san -> san.getType().equals(SubjectAlternativeName.Type.DNS_NAME))
                        .map(SubjectAlternativeName::getValue).collect(Collectors.toSet());

                var dnsNamesOfZone = dnsNamesOf(instance.id(), List.of(zone));
                if (!subjectAlternativeNames.containsAll(dnsNamesOfZone))
                    throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate is missing required SANs for zone " + zone.value());

            } catch (SecretNotFoundException s) {
                // Normally because the cert is in the process of being provisioned - this will cause a retry in InternalStepRunner
                throw new EndpointCertificateException(EndpointCertificateException.Type.CERT_NOT_AVAILABLE, "Certificate not found in secret store");
            } catch (EndpointCertificateException e) {
                log.log(LogLevel.WARNING, "Certificate validation failure for " + instance.id().serializedForm(), e);
                throw e;
            } catch (Exception e) {
                log.log(LogLevel.WARNING, "Certificate validation failure for " + instance.id().serializedForm(), e);
                throw new EndpointCertificateException(EndpointCertificateException.Type.VERIFICATION_FAILURE, "Certificate validation failure for app " + instance.id().serializedForm(), e);
            }
    }

    private List<String> dnsNamesOf(ApplicationId applicationId, List<ZoneId> zones) {
        List<String> endpointDnsNames = new ArrayList<>();

        // We add first an endpoint name based on a hash of the applicationId,
        // as the certificate provider requires the first CN to be < 64 characters long.
        endpointDnsNames.add(commonNameHashOf(applicationId, zoneRegistry.system()));

        var globalDefaultEndpoint = Endpoint.of(applicationId).named(EndpointId.defaultId());
        var rotationEndpoints = Endpoint.of(applicationId).wildcard();

        var zoneLocalEndpoints = zones.stream().flatMap(zone -> Stream.of(
                Endpoint.of(applicationId).target(ClusterSpec.Id.from("default"), zone),
                Endpoint.of(applicationId).wildcard(zone)
        ));

        Stream.concat(Stream.of(globalDefaultEndpoint, rotationEndpoints), zoneLocalEndpoints)
                .map(endpoint -> endpoint.routingMethod(RoutingMethod.exclusive))
                .map(endpoint -> endpoint.on(Endpoint.Port.tls()))
                .map(endpointBuilder -> endpointBuilder.in(zoneRegistry.system()))
                .map(Endpoint::dnsName).forEach(endpointDnsNames::add);

        return Collections.unmodifiableList(endpointDnsNames);
    }

    /** Create a common name based on a hash of the ApplicationId. This should always be less than 64 characters long. */
    private static String commonNameHashOf(ApplicationId application, SystemName system) {
        var hashCode = Hashing.sha1().hashString(application.serializedForm(), Charset.defaultCharset());
        var base32encoded = BaseEncoding.base32().omitPadding().lowerCase().encode(hashCode.asBytes());
        return 'v' + base32encoded + Endpoint.dnsSuffix(system);
    }

}
