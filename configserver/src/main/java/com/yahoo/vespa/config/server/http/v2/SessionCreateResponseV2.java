// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.Tenant;
import com.yahoo.vespa.config.server.http.SessionCreateResponse;
import com.yahoo.vespa.config.server.http.SessionResponse;

/**
 * Creates a response for SessionCreateHandler (v2).
 *
 * @author hmusum
 * @since 5.1.27
 */
public class SessionCreateResponseV2 extends SessionResponse implements SessionCreateResponse {
    private final Tenant tenant;

    public SessionCreateResponseV2(Tenant tenant, Slime deployLog, Cursor root) {
        super(deployLog, root);
        this.tenant = tenant;
    }

    @Override
    public HttpResponse createResponse(String hostName, int port, long sessionId) {
        String tenantName = tenant.getName().value();
        String path = "http://" + hostName + ":" + port + "/application/v2/tenant/" + tenantName + "/session/" + sessionId;

        this.root.setString("tenant", tenantName);
        this.root.setString("session-id", Long.toString(sessionId));
        this.root.setString("prepared", path + "/prepared");
        this.root.setString("content", path + "/content/");
        this.root.setString("message", "Session " + sessionId + " for tenant '" + tenantName + "' created.");
        return this;
    }
}
