// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.vespa.model.application.validation.Validation.Context;

public class EndpointCertificateSecretsValidator implements Validator {

    /** This check is delayed until validation to allow node provisioning to complete while we are waiting for cert */
    @Override
    public void validate(Context context) {
        if (context.deployState().endpointCertificateSecrets().isPresent() && context.deployState().endpointCertificateSecrets().get().isMissing()) {
            throw new CertificateNotReadyException("TLS enabled, but could not yet retrieve certificate version %s for application %s"
                    .formatted(context.deployState().endpointCertificateSecrets().get().version(), context.deployState().getProperties().applicationId().serializedForm()));
        }
    }
}
