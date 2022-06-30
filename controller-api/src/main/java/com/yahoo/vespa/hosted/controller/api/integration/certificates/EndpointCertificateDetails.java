// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.util.List;

/**
 * This record is used when requesting additional metadata about an application's endpoint certificate from the provider.
 *
 * @author andreer
 */
public record EndpointCertificateDetails(
        String request_id,
        String requestor,
        String status,
        String ticket_id,
        String athenz_domain,
        List<EndpointCertificateRequestMetadata.DnsNameStatus> dnsnames,
        String duration_sec,
        String expiry,
        String private_key_kgname,
        String private_key_keyname,
        String private_key_version,
        String cert_key_kgname,
        String cert_key_keyname,
        String cert_key_version,
        String create_time,
        boolean expiry_protection,
        String public_key_algo,
        String issuer,
        String serial
) { }