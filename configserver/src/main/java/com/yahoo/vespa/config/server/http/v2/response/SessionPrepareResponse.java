// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActionsSlimeConverter;
import com.yahoo.vespa.config.server.http.v2.PrepareResult;

/**
 * Creates a response for SessionPrepareHandler.
 *
 * @author hmusum
 */
public class SessionPrepareResponse extends SlimeJsonResponse {

    public SessionPrepareResponse(TenantName tenantName, HttpRequest request, long sessionId) {
        this(new Slime(), tenantName, request, sessionId, new ConfigChangeActions());
    }

    public SessionPrepareResponse(PrepareResult result, TenantName tenantName, HttpRequest request) {
        this(result.deployLogger().slime(), tenantName, request, result.sessionId(), result.configChangeActions());
    }

    private SessionPrepareResponse(Slime deployLog, TenantName tenantName, HttpRequest request, long sessionId, ConfigChangeActions actions) {
        super(deployLog);

        Cursor root = deployLog.get().type() != Type.NIX ? deployLog.get() : deployLog.setObject();
        root.setString("tenant", tenantName.value());
        root.setString("session-id", Long.toString(sessionId));
        root.setString("activate", "http://" + request.getHost() + ":" + request.getPort() +
                "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId + "/active");
        root.setString("message", "Session " + sessionId + " for tenant '" + tenantName.value() + "' prepared.");
        new ConfigChangeActionsSlimeConverter(actions).toSlime(root);
    }

}
