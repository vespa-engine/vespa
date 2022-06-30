// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.vespa.model.VespaModel;

public class EndpointCertificateSecretsValidator extends Validator {

    /** This check is delayed until validation to allow node provisioning to complete while we are waiting for cert */
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (deployState.endpointCertificateSecrets().isPresent() && deployState.endpointCertificateSecrets().get().isMissing()) {
            throw new CertificateNotReadyException("TLS enabled, but could not yet retrieve certificate version %s for application %s"
                    .formatted(deployState.endpointCertificateSecrets().get().version(), deployState.getProperties().applicationId().serializedForm()));
        }
    }
}
