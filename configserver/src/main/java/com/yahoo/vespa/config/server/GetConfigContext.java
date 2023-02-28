// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

/**
 * Contains the context for serving getconfig requests so that this information does not have to be looked up multiple times.
 *
 * @author Ulf Lilleengen
 */
public class GetConfigContext {

    private final ApplicationId app;
    private final RequestHandler requestHandler;
    private final Trace trace;

    private GetConfigContext(ApplicationId app, RequestHandler handler, Trace trace) {
        this.app = app;
        this.requestHandler = handler;
        this.trace = trace;
    }

    public ApplicationId applicationId() {
        return app;
    }

    public Trace trace() {
        return trace;
    }

    public RequestHandler requestHandler() {
        return requestHandler;
    }

    public static GetConfigContext create(ApplicationId app, RequestHandler handler, Trace trace) {
        return new GetConfigContext(app, handler, trace);
    }

    public static GetConfigContext empty() {
        return new GetConfigContext(null, null, null);
    }

    public static GetConfigContext testContext(ApplicationId app) {
        return new GetConfigContext(app, null, null);
    }

    public boolean isEmpty() { return app == null && requestHandler == null && trace == null; }
    
    /**
     * Helper to produce a log preamble with the tenant and app id
     * @return log msg preamble
     */
    public String logPre() {
        return TenantRepository.logPre(app);
    }

    @Override
    public String toString() {
        return "get config context for application " + app + ", having handler " + requestHandler;
    }

}
