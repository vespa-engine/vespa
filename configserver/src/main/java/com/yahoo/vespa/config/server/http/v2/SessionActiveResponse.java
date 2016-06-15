// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.SessionResponse;
import com.yahoo.vespa.config.server.session.LocalSession;

public class SessionActiveResponse extends SessionResponse {

    public SessionActiveResponse(Slime metaData, TenantName tenantName, HttpRequest request, LocalSession session, Zone zone) {
        super(metaData, metaData.get());
        String message = "Session " + session.getSessionId() + " for tenant '" + tenantName + "' activated.";
        root.setString("tenant", tenantName.value());
        root.setString("message", message);
        final ApplicationId applicationId = session.getApplicationId();
        root.setString("url", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName +
                "/application/" + applicationId.application().value() +
                "/environment/" + zone.environment().value() +
                "/region/" + zone.region().value() +
                "/instance/" + applicationId.instance().value());
    }
}
