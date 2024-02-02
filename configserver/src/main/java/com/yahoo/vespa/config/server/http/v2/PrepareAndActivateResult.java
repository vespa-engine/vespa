package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ParentHostUnavailableException;

/**
 * Allows a partial deployment success, where the application is prepared, but not activated.
 * This currently only allows the parent-host-not-ready and application-lock cases, as other transient errors are
 * thrown too early (LB during prepare, cert during validation), but could be expanded to allow
 * reuse of a prepared session in the future. In that case, users of this result (handler and its client)
 * must also be updated.
 *
 * @author jonmv
 */
public record PrepareAndActivateResult(PrepareResult prepareResult, RuntimeException activationFailure) {

    public PrepareResult deployResult() {
        if (activationFailure != null) throw activationFailure;
        return prepareResult;
    }

}
