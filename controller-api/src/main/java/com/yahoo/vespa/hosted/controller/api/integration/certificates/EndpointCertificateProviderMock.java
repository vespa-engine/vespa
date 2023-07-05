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
public class EndpointCertificateProviderMock implements EndpointCertificateProvider {

    private final Map<String, List<String>> dnsNames = new HashMap<>();
    private final Map<String, EndpointCertificate> certificates = new HashMap<>();

    public List<String> dnsNamesOf(String rootRequestId) {
        return Collections.unmodifiableList(dnsNames.getOrDefault(rootRequestId, List.of()));
    }

    @Override
    public EndpointCertificate requestCaSignedCertificate(String key, List<String> dnsNames, Optional<EndpointCertificate> currentCert, String algo, boolean useAlternativeProvider) {
        String endpointCertificatePrefix = "vespa.tls.%s".formatted(key);
        long epochSecond = Instant.now().getEpochSecond();
        long inAnHour = epochSecond + 3600;
        String requestId = UUID.randomUUID().toString();
        this.dnsNames.put(requestId, dnsNames);
        int version = currentCert.map(c -> currentCert.get().version() + 1).orElse(0);
        EndpointCertificate cert = new EndpointCertificate(endpointCertificatePrefix + "-key", endpointCertificatePrefix + "-cert", version, 0,
                                                           currentCert.map(EndpointCertificate::rootRequestId).orElse(requestId), Optional.of(requestId), dnsNames, "mockCa", Optional.of(inAnHour), Optional.of(epochSecond), Optional.empty());
        currentCert.ifPresent(c -> certificates.remove(c.leafRequestId().orElseThrow()));
        certificates.put(requestId, cert);
        return cert;
    }

    @Override
    public List<EndpointCertificateRequest> listCertificates() {
        return certificates.values().stream()
                           .map(p -> new EndpointCertificateRequest(
                                   p.leafRequestId().orElse(p.rootRequestId()),
                                   "requestor",
                                   "ticketId",
                                   "athenzDomain",
                                   p.requestedDnsSans().stream()
                                    .map(san -> new EndpointCertificateRequest.DnsNameStatus(san, "done"))
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
        certificates.remove(requestId);
    }

    @Override
    public EndpointCertificateDetails certificateDetails(String requestId) {
        var request = certificates.get(requestId);

        if (request == null) throw new IllegalArgumentException("Unknown certificate request");

        return new EndpointCertificateDetails(requestId,
                "requestor",
                "ok",
                "ticket_id",
                "athenz_domain",
                request.requestedDnsSans().stream().map(name -> new EndpointCertificateRequest.DnsNameStatus(name, "done")).toList(),
                "duration_sec",
                "expiry",
                request.keyName(),
                request.keyName(),
                "0",
                request.certName(),
                request.certName(),
                "0",
                "2021-09-28T00:14:31.946562037Z",
                true,
                "public_key_algo",
                "issuer",
                "serial");
    }

}
