// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.ApplicationId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author tokle
 * @author andreer
 */
public class EndpointCertificateMock implements EndpointCertificateProvider {

    private final Map<ApplicationId, List<String>> dnsNames = new HashMap<>();
    private final Map<String, EndpointCertificateMetadata> providerMetadata = new HashMap<>();

    public List<String> dnsNamesOf(ApplicationId application) {
        return Collections.unmodifiableList(dnsNames.getOrDefault(application, List.of()));
    }

    @Override
    public EndpointCertificateMetadata requestCaSignedCertificate(ApplicationId applicationId, List<String> dnsNames, Optional<EndpointCertificateMetadata> currentMetadata) {
        this.dnsNames.put(applicationId, dnsNames);
        String endpointCertificatePrefix = String.format("vespa.tls.%s.%s.%s", applicationId.tenant(),
                applicationId.application(), applicationId.instance());
        long epochSecond = Instant.now().getEpochSecond();
        long inAnHour = epochSecond + 3600;
        String requestId = UUID.randomUUID().toString();
        EndpointCertificateMetadata metadata = new EndpointCertificateMetadata(endpointCertificatePrefix + "-key", endpointCertificatePrefix + "-cert", 0, 0,
                requestId, dnsNames, "mockCa", Optional.of(inAnHour), Optional.of(epochSecond));
        providerMetadata.put(requestId, metadata);
        return metadata;
    }

    @Override
    public List<EndpointCertificateRequestMetadata> listCertificates() {

        return providerMetadata.values().stream()
                .map(p -> new EndpointCertificateRequestMetadata(
                        p.requestId(),
                        "mock",
                        "mock",
                        "mock",
                        p.requestedDnsSans().stream()
                                .map(san -> new EndpointCertificateRequestMetadata.DnsNameStatus(san, "done"))
                                .collect(Collectors.toUnmodifiableList()),
                        3600,
                        "ok",
                        "2021-09-28T00:14:31.946562037Z",
                        p.expiry().orElseThrow(),
                        p.issuer(),
                        "rsa_2048"
                ))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void deleteCertificate(ApplicationId applicationId, String requestId) {
        dnsNames.remove(applicationId);
        providerMetadata.remove(requestId);
    }

}
