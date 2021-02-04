package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.List;

public interface EndpointCertificateValidator {
    void validate(EndpointCertificateMetadata endpointCertificateMetadata, String serializedInstanceId, ZoneId zone, List<String> requiredNamesForZone);
}
