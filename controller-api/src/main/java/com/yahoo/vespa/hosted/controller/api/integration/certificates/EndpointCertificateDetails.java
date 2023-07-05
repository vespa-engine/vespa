// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;

/**
 * This record is used when requesting additional details about an application's endpoint certificate from the provider.
 *
 * @author andreer
 */
public record EndpointCertificateDetails(
        String requestId,
        String requestor,
        String status,
        String ticketId,
        String athenzDomain,
        List<EndpointCertificateRequest.DnsNameStatus> dnsNames,
        String durationSec,
        String expiry,
        String privateKeyKgname,
        String privateKeyKeyname,
        String privateKeyVersion,
        String certKeyKgname,
        String certKeyKeyname,
        String certKeyVersion,
        String createTime,
        boolean expiryProtection,
        String publicKeyAlgo,
        String issuer,
        String serial
) { }
