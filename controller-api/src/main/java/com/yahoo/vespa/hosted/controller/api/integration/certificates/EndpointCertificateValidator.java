// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.List;

public interface EndpointCertificateValidator {
    void validate(EndpointCertificateMetadata endpointCertificateMetadata, String serializedInstanceId, ZoneId zone, List<String> requiredNamesForZone);
}
