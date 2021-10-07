// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.OutputStream;

/**
 * @author bratseth
 */
public class EmptyResponse extends HttpResponse {

    public EmptyResponse() {
        super(200);
    }

    @Override
    public void render(OutputStream stream) {}

}
