// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Creates a response for SessionCreateHandler.
 *
 * @author hmusum
 * @since 5.1.27
 */
public class SessionCreateResponse extends SessionResponse {
    private final TenantName tenantName;

    public SessionCreateResponse(TenantName tenantName, Slime deployLog, Cursor root) {
        super(deployLog, root);
        this.tenantName = tenantName;
    }

    public HttpResponse createResponse(String hostName, int port, long sessionId) {
        String path = "http://" + hostName + ":" + port + "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId;

        this.root.setString("tenant", tenantName.value());
        this.root.setString("session-id", Long.toString(sessionId));
        this.root.setString("prepared", path + "/prepared");
        this.root.setString("content", path + "/content/");
        this.root.setString("message", "Session " + sessionId + " for tenant '" + tenantName.value() + "' created.");
        return this;
    }
}
