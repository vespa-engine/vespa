// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.jdisc.Metric;

/**
 * Dummy for internal use.
 *
 * @author jonmv
 */
public class RestApi extends LoggingRequestHandler {

    @Inject
    public RestApi() {
        super(ignored -> { throw new IllegalStateException("Not supposed to handle anything"); }, (Metric)null);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        throw new IllegalStateException("Not supposed to handle anything");
    }

}
