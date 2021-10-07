// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.request;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.server.http.ContentRequest;
import com.yahoo.vespa.config.server.http.Utils;

/**
 * Requests for content and content status (v2)
 * are handled by this class.
 *
 * @author hmusum
 * @since 5.3
 */
public class SessionContentRequestV2 extends ContentRequest {
    private static final String uriPattern = "http://*/application/v2/tenant/*/session/*/content/*";
    private final TenantName tenantName;
    private final long sessionId;

    public SessionContentRequestV2(HttpRequest request,
                            long sessionId,
                            TenantName tenantName,
                            String path,
                            ApplicationFile applicationFile) {
        super(request, sessionId, path, applicationFile);
        this.tenantName = tenantName;
        this.sessionId = sessionId;
    }

    @Override
    public String getPathPrefix() {
        return "/application/v2/tenant/" + tenantName.value() + "/session/" + sessionId;
    }

    public static String getContentPath(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, uriPattern);
        return bm.group(4);
    }
}
