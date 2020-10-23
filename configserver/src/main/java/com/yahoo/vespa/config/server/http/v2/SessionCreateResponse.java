// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

/**
 * Creates a response for SessionCreateHandler.
 *
 * @author hmusum
 */
public class SessionCreateResponse extends SlimeJsonResponse {

    public SessionCreateResponse(Slime deployLog, TenantName tenantName, String hostName, int port, long sessionId) {
        super(deployLog);
        String path = "http://" + hostName + ":" + port + "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId;
        Cursor root = deployLog.get();

        root.setString("tenant", tenantName.value());
        root.setString("session-id", Long.toString(sessionId));
        root.setString("prepared", path + "/prepared");
        root.setString("content", path + "/content/");
        root.setString("message", "Session " + sessionId + " for tenant '" + tenantName.value() + "' created.");
    }
}
