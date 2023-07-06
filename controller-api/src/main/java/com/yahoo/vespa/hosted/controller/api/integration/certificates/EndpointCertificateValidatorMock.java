// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import com.yahoo.config.provision.zone.ZoneId;

import java.util.List;

/**
 * @author andreer
 */
public class EndpointCertificateValidatorMock implements EndpointCertificateValidator {

    @Override
    public void validate(
            EndpointCertificate endpointCertificate,
            String serializedApplicationId,
            ZoneId zone,
            List<String> requiredNamesForZone) {
        // Mock does no validation - for unit tests only!
    }

}
