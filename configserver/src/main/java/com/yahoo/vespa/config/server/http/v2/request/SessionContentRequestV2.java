// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.request;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import ai.vespa.http.HttpURL;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.config.server.http.ContentRequest;

/**
 * Requests for content and content status (v2)
 * are handled by this class.
 *
 * @author hmusum
 * @since 5.3
 */
public class SessionContentRequestV2 extends ContentRequest {

    private final TenantName tenantName;
    private final long sessionId;

    public SessionContentRequestV2(HttpRequest request,
                            long sessionId,
                            TenantName tenantName,
                            HttpURL.Path path,
                            ApplicationFile applicationFile) {
        super(request, sessionId, path, applicationFile);
        this.tenantName = tenantName;
        this.sessionId = sessionId;
    }

    @Override
    public String getPathPrefix() {
        return "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId;
    }

    public static HttpURL.Path getContentPath(HttpRequest request) {
        Path path = new Path(request.getUri());
        if ( ! path.matches("/application/v2/tenant/{tenant}/session/{session}/content/{*}"))
            throw new IllegalStateException("error in request routing");
        return path.getRest();
    }

}
