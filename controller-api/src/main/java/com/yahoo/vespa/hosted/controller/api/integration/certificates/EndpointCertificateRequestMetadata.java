// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;

/**
 * This record is used for metadata about an application's endpoint certificate received from the certificate provider.
 *
 * @param createTime ISO 8601
 * @author andreer
 */
public record EndpointCertificateRequestMetadata(String requestId, String requestor, String ticketId,
                                                 String athenzDomain, List<DnsNameStatus> dnsNames, long durationSec,
                                                 String status, String createTime, long expiry, String issuer,
                                                 String publicKeyAlgo) {


}
