// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Creates a response for SessionPrepareHandler.
 *
 * @author hmusum
 * @since 5.1.28
 */
class SessionPrepareResponse extends SessionResponse {

    public SessionPrepareResponse(Slime deployLog, TenantName tenantName, HttpRequest request, long sessionId, ConfigChangeActions actions) {
        super(deployLog, deployLog.get());
        String message = "Session " + sessionId + " for tenant '" + tenantName.value() + "' prepared.";
        this.root.setString("tenant", tenantName.value());
        this.root.setString("activate", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId + "/active");
        root.setString("message", message);
        new ConfigChangeActionsSlimeConverter(actions).toSlime(root);
    }

}
