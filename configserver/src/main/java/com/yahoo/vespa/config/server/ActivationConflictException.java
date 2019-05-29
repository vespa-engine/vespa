// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.TenantApplications;

/**
 * Exception used when activation cannot be done because activation is for
 * an older session than the one that is active now or because current active
 * session has changed since the session to be activated was created
 *
 * Also used when a redeployment is requested, but another deployment is aready ongoing,
 * or when attempting to activate a deployment which is no longer supposed to be activated.
 * @see TenantApplications#preparing(ApplicationId)
 *
 * @author hmusum
 */
public class ActivationConflictException extends RuntimeException {
    public ActivationConflictException(String s) {
        super(s);
    }
}
