// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.auditlog;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;

/**
 * A handler that logs requests to the audit log. Handlers that need audit logging should extend this and implement
 * {@link AuditLoggingRequestHandler#auditAndHandle(HttpRequest)}.
 *
 * @author mpolden
 */
public abstract class AuditLoggingRequestHandler extends ThreadedHttpRequestHandler {

    private final AuditLogger auditLogger;

    public AuditLoggingRequestHandler(Context ctx, AuditLogger auditLogger) {
        super(ctx);
        this.auditLogger = auditLogger;
    }

    @Override
    public final HttpResponse handle(HttpRequest request) {
        return auditAndHandle(auditLogger.log(request));
    }

    @Override
    public final HttpResponse handle(HttpRequest request, ContentChannel channel) {
        return super.handle(request, channel);
    }

    public abstract HttpResponse auditAndHandle(HttpRequest request);

}
