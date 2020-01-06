// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.api.TlsSecrets;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.CertificateNotReadyException;
import com.yahoo.vespa.model.VespaModel;

public class TlsSecretsValidator extends Validator {

    /** This check is delayed until validation to allow node provisioning to complete while we are waiting for cert */
    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (deployState.tlsSecrets().isPresent() && deployState.tlsSecrets().get() == TlsSecrets.MISSING) {
            throw new CertificateNotReadyException("TLS enabled, but could not retrieve certificate yet");
        }
    }
}
