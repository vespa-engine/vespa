// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.config.server.http.ContentRequest;

/**
 * Represents a content request for an application.
 *
 * @author Ulf Lilleengen
 * @since 5.3
 */
public class ApplicationContentRequest extends ContentRequest {

    private final ApplicationId applicationId;
    private final Zone zone;

    ApplicationContentRequest(HttpRequest request,
                              long sessionId,
                              ApplicationId applicationId,
                              Zone zone,
                              String contentPath,
                              ApplicationFile applicationFile) {
        super(request, sessionId, contentPath, applicationFile);
        this.applicationId = applicationId;
        this.zone = zone;
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
