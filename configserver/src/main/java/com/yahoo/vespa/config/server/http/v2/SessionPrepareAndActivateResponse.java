// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Creates a response for SessionPrepareHandler.
 *
 * @author hmusum
 */
class SessionPrepareAndActivateResponse extends SessionResponse {

    SessionPrepareAndActivateResponse(PrepareResult result, TenantName tenantName, HttpRequest request,
                                      ApplicationId applicationId, Zone zone) {
        this(result.deployLog(), tenantName, request, result.sessionId(), result.configChangeActions(),
             zone, applicationId);
    }

    private SessionPrepareAndActivateResponse(Slime deployLog, TenantName tenantName, HttpRequest request,
                                              long sessionId, ConfigChangeActions actions, Zone zone,
                                              ApplicationId applicationId) {
        super(deployLog, deployLog.get());
        String message = "Session " + sessionId + " for tenant '" + tenantName.value() + "' prepared and activated.";
        this.root.setString("tenant", tenantName.value());
        root.setString("url", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName +
                "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value());
        root.setString("message", message);
        new ConfigChangeActionsSlimeConverter(actions).toSlime(root);
    }

}
