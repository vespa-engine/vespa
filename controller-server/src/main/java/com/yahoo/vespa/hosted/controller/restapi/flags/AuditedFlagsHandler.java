// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.flags;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.configserver.flags.FlagsDb;
import com.yahoo.vespa.configserver.flags.http.FlagsHandler;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.auditlog.AuditLogger;

/**
 * An extension of {@link FlagsHandler} which logs requests to the audit log.
 *
 * @author mpolden
 */
public class AuditedFlagsHandler extends FlagsHandler {

    private final AuditLogger auditLogger;

    public AuditedFlagsHandler(Context context, Controller controller, FlagsDb flagsDb) {
        super(context, flagsDb);
        auditLogger = controller.auditLogger();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return super.handle(auditLogger.log(request));
    }

}
