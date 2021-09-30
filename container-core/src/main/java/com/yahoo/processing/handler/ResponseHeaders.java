// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.google.common.collect.ImmutableMap;
import com.yahoo.processing.Request;
import com.yahoo.processing.response.AbstractData;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds a set of headers which will be added to the Processing response.
 * A Response may contain multiple such data objects, and all of them will be added to the response.
 *
 * @author bratseth
 */
public class ResponseHeaders extends AbstractData {

    private final Map<String,List<String>> headers;

    /**
     * Creates a response headers object with a set of headers.
     *
     * @param headers the headers to copy into this object
     */
    public ResponseHeaders(Map<String,List<String>> headers, Request request) {
        super(request);
        this.headers = ImmutableMap.copyOf(headers);
    }

    /** Returns an unmodifiable map of the response headers of this */
    public Map<String,List<String>> headers() { return headers; }

}
