package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.List;

public class EndpointCertificateValidatorMock implements EndpointCertificateValidator {
    @Override
    public void validate(
            EndpointCertificateMetadata endpointCertificateMetadata,
            String serializedApplicationId,
            ZoneId zone,
            List<String> requiredNamesForZone) {
        // Mock does no validation - for unit tests only!
    }
}
