// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.v2.PrepareAndActivateResult;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;

/**
 * Creates a response for ApplicationApiHandler.
 *
 * @author hmusum
 */
public class SessionPrepareAndActivateResponse extends SlimeJsonResponse {

    public SessionPrepareAndActivateResponse(PrepareAndActivateResult result, ApplicationId applicationId) {
        super(result.prepareResult().deployLogger().slime());

        TenantName tenantName = applicationId.tenant();
        String message = "Session " + result.prepareResult().sessionId() + " for tenant '" + tenantName.value() + "' prepared" +
                         (result.activationFailure() == null ? " and activated." : ", but activation failed: " + result.activationFailure().getMessage());
        Cursor root = slime.get();

        root.setString("message", message);
        root.setString("sessionId", Long.toString(result.prepareResult().sessionId()));
        root.setBool("activated", result.activationFailure() == null);

        new ConfigChangeActionsSlimeConverter(result.prepareResult().configChangeActions()).toSlime(root);
    }

}
