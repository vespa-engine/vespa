// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author mpolden
 */
public class FilterUtils {

    private FilterUtils() {}

    /** Write HTTP response using given handler */
    public static void write(HttpResponse response, ResponseHandler handler) {
        response.headers().put("Content-Type", response.getContentType());
        try (FastContentWriter writer = ResponseDispatch.newInstance(response.getJdiscResponse())
                                                        .connectFastWriter(handler)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                response.render(out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            writer.write(out.toByteArray());
        }
    }

}
