// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.config.provision.ApplicationId;

public class SessionActiveResponse extends SlimeJsonResponse {

    public SessionActiveResponse(Slime metaData, HttpRequest request, ApplicationId applicationId, long sessionId, Zone zone) {
        super(metaData);
        TenantName tenantName = applicationId.tenant();
        String message = "Session " + sessionId + " for tenant '" + tenantName.value() + "' activated.";
        Cursor root = metaData.get();

        root.setString("tenant", tenantName.value());
        root.setString("session-id", Long.toString(sessionId));
        root.setString("message", message);
        root.setString("url", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName +
                "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value());
    }
}
