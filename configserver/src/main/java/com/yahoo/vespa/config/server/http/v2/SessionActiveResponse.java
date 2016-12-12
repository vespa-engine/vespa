// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.SessionResponse;

public class SessionActiveResponse extends SessionResponse {

    public SessionActiveResponse(Slime metaData, HttpRequest request, ApplicationId applicationId, long sessionId, Zone zone) {
        super(metaData, metaData.get());
        TenantName tenantName = applicationId.tenant();
        String message = "Session " + sessionId + " for tenant '" + tenantName.value() + "' activated.";
        root.setString("tenant", tenantName.value());
        root.setString("message", message);
        root.setString("url", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName +
                "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value());
    }
}
