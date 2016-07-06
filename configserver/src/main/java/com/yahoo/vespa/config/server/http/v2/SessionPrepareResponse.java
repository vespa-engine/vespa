// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.SessionResponse;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.Tenant;

/**
 * Creates a response for SessionPrepareHandler.
 *
 * @author hmusum
 * @since 5.1.28
 */
class SessionPrepareResponse extends SessionResponse {

    public SessionPrepareResponse(Slime deployLog, Tenant tenant, HttpRequest request, Session session, ConfigChangeActions actions) {
        super(deployLog, deployLog.get());
        String message = "Session " + session.getSessionId() + " for tenant '" + tenant.getName() + "' prepared.";
        this.root.setString("tenant", tenant.getName().value());
        this.root.setString("activate", "http://" + request.getHost() + ":" + request.getPort() + "/application/v2/tenant/" + tenant.getName() + "/session/" + session.getSessionId() + "/active");
        root.setString("message", message);
        new ConfigChangeActionsSlimeConverter(actions).toSlime(root);
    }

}
