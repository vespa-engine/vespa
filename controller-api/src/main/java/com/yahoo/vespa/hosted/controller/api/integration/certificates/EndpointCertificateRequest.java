// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;

/**
 * This class is used for details about an application's endpoint certificate received from the certificate provider.
 *
 * @param createTime ISO 8601
 * @author andreer
 */
public record EndpointCertificateRequest(String requestId, String requestor, String ticketId, String athenzDomain,
                                         List<DnsNameStatus> dnsNames, long durationSec, String status,
                                         String createTime, long expiry, String issuer, String publicKeyAlgo) {

    public record DnsNameStatus(String dnsName, String status) {}

}
