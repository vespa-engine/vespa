// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;

/**
 * Creates a response for ApplicationApiHandler.
 *
 * @author hmusum
 */
public class SessionPrepareAndActivateResponse extends SlimeJsonResponse {

    public SessionPrepareAndActivateResponse(PrepareResult result, HttpRequest request, ApplicationId applicationId, Zone zone) {
        super(result.deployLogger().slime());

        TenantName tenantName = applicationId.tenant();
        String message = "Session " + result.sessionId() + " for tenant '" + tenantName.value() + "' prepared and activated.";
        Cursor root = slime.get();

        root.setString("tenant", tenantName.value());
        root.setString("session-id", Long.toString(result.sessionId()));
        root.setString("url", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName +
                "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value());
        root.setString("message", message);
        new ConfigChangeActionsSlimeConverter(result.configChangeActions()).toSlime(root);
    }

}
