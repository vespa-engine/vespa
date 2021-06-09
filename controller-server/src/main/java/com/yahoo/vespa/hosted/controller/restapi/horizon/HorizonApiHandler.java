package com.yahoo.vespa.hosted.controller.restapi.horizon;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.MessageResponse;

/**
 * Proxies metrics requests from Horizon UI
 *
 * @author valerijf
 */
public class HorizonApiHandler extends LoggingRequestHandler {

    @Inject
    public HorizonApiHandler(LoggingRequestHandler.Context parentCtx) {
        super(parentCtx);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new MessageResponse("OK");
    }
}
