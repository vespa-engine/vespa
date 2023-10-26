// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingResponseHandler implements ResponseHandler {

    @Override
    public ContentChannel handleResponse(Response response) {
        throw new UnsupportedOperationException();
    }
}
