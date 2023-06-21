// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @author tokle
 * @author andreer
 */
public class EndpointCertificateMock implements EndpointCertificateProvider {

    private final Map<String, List<String>> dnsNames = new HashMap<>();
    private final Map<String, EndpointCertificateMetadata> providerMetadata = new HashMap<>();

    public List<String> dnsNamesOf(String rootRequestId) {
        return Collections.unmodifiableList(dnsNames.getOrDefault(rootRequestId, List.of()));
    }

    @Override
    public EndpointCertificateMetadata requestCaSignedCertificate(String key, List<String> dnsNames, Optional<EndpointCertificateMetadata> currentMetadata, String algo, boolean useAlternativeProvider) {
        String endpointCertificatePrefix = "vespa.tls.%s".formatted(key);
        long epochSecond = Instant.now().getEpochSecond();
        long inAnHour = epochSecond + 3600;
        String requestId = UUID.randomUUID().toString();
        this.dnsNames.put(requestId, dnsNames);
        int version = currentMetadata.map(c -> currentMetadata.get().version()+1).orElse(0);
        EndpointCertificateMetadata metadata = new EndpointCertificateMetadata(endpointCertificatePrefix + "-key", endpointCertificatePrefix + "-cert", version, 0,
                currentMetadata.map(EndpointCertificateMetadata::rootRequestId).orElse(requestId), Optional.of(requestId), dnsNames, "mockCa", Optional.of(inAnHour), Optional.of(epochSecond), Optional.empty());
        currentMetadata.ifPresent(c -> providerMetadata.remove(c.leafRequestId().orElseThrow()));
        providerMetadata.put(requestId, metadata);
        return metadata;
    }

    @Override
    public List<EndpointCertificateRequestMetadata> listCertificates() {

        return providerMetadata.values().stream()
                .map(p -> new EndpointCertificateRequestMetadata(
                        p.leafRequestId().orElse(p.rootRequestId()),
                        "requestor",
                        "ticketId",
                        "athenzDomain",
                        p.requestedDnsSans().stream()
                                .map(san -> new EndpointCertificateRequestMetadata.DnsNameStatus(san, "done"))
                                .toList(),
                        3600,
                        "ok",
                        "2021-09-28T00:14:31.946562037Z",
                        p.expiry().orElseThrow(),
                        p.issuer(),
                        "rsa_2048"
                ))
                .toList();
    }

    @Override
    public void deleteCertificate(String requestId) {
        dnsNames.remove(requestId);
        providerMetadata.remove(requestId);
    }

    @Override
    public EndpointCertificateDetails certificateDetails(String requestId) {
        var metadata = providerMetadata.get(requestId);

        if(metadata==null) throw new RuntimeException("Unknown certificate request");

        return new EndpointCertificateDetails(requestId,
                "requestor",
                "ok",
                "ticket_id",
                "athenz_domain",
                metadata.requestedDnsSans().stream().map(name -> new EndpointCertificateRequestMetadata.DnsNameStatus(name, "done")).toList(),
                "duration_sec",
                "expiry",
                metadata.keyName(),
                metadata.keyName(),
                "0",
                metadata.certName(),
                metadata.certName(),
                "0",
                "2021-09-28T00:14:31.946562037Z",
                true,
                "public_key_algo",
                "issuer",
                "serial");
    }
}
