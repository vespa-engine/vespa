// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.LocalSession;

/**
 * Represents a content request for an application.
 *
 * @author lulf
 * @since 5.3
 */
class ApplicationContentRequest extends ContentRequest {

    private static final String uriPattern = "http://*/application/v2/tenant/*/application/*/environment/*/region/*/instance/*/content/*";
    private final ApplicationId applicationId;
    private final Zone zone;

    private ApplicationContentRequest(HttpRequest request, LocalSession session, ApplicationId applicationId, Zone zone) {
        super(request, session);
        this.applicationId = applicationId;
        this.zone = zone;
    }

    static ContentRequest create(HttpRequest request, LocalSession session, ApplicationId applicationId, Zone zone) {
        return new ApplicationContentRequest(request, session, applicationId, zone);
    }

    @Override
    protected String getContentPath(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, uriPattern);
        return bm.group(7);
    }

    @Override
    public String getPathPrefix() {
        StringBuilder sb = new StringBuilder();
        sb.append("/application/v2/tenant/").append(applicationId.tenant().value());
        sb.append("/application/").append(applicationId.application().value());
        sb.append("/environment/").append(zone.environment().value());
        sb.append("/region/").append(zone.region().value());
        sb.append("/instance/").append(applicationId.instance().value());
        return sb.toString();
    }

}
