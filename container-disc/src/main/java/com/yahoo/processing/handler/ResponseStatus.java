// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.yahoo.processing.Request;
import com.yahoo.processing.response.AbstractData;

/**
 * <p>A data item holding a response HTTP status code.
 * If this is present in a Response it will determine the HTTP status of the response (when returned over HTTP),
 * regardless of any errors present in the result which might otherwise determine the response status.</p>
 *
 * <p>If several ResponseStatus instances are present, the first one encountered by a depth-first search through
 * the data composite tree will be used.</p>
 *
 * <p>Note that this must be added to the response before any response data is writable to take effect.</p>
 *
 * @author bratseth
 */
public class ResponseStatus extends AbstractData {

    /** A http status code */
    private final int code;

    public ResponseStatus(int code, Request request) {
        super(request);
        this.code = code;
    }

    /** Returns the code of this */
    public int code() { return code; }

    @Override
    public String toString() {
        return "HTTP response " + code;
    }

}
