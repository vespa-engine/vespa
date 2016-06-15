// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.LocalSession;

/**
 * Requests for content and content status (v2)
 * are handled by this class.
 *
 * @author musum
 * @since 5.3
 */
class SessionContentRequestV2 extends ContentRequest {
    private static final String uriPattern = "http://*/application/v2/tenant/*/session/*/content/*";
    private final TenantName tenantName;
    private final long sessionId;

    private SessionContentRequestV2(HttpRequest request, LocalSession session, TenantName tenantName) {
        super(request, session);
        this.tenantName = tenantName;
        this.sessionId = session.getSessionId();
    }

    static ContentRequest create(HttpRequest request, LocalSession session) {
        return new SessionContentRequestV2(request, session, Utils.getTenantFromSessionRequest(request));
    }

    @Override
    public String getPathPrefix() {
        return "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId;
    }

    @Override
    protected String getContentPath(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, uriPattern);
        return bm.group(4);
    }
}
