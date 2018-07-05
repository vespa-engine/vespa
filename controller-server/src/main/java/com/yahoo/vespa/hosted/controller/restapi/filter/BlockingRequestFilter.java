// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;

/**
 * @author bjorncs
 */
@SuppressWarnings("unused") // Injected
public class BlockingRequestFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        Response response = new Response(Response.Status.FORBIDDEN);
        try (FastContentWriter writer = ResponseDispatch.newInstance(response).connectFastWriter(handler)) {
            writer.write("Forbidden to access this API");
        }
    }
}
