// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;

/**
 * @author Stian Kristoffersen
 */
public class DummyFilter implements  SecurityRequestFilter {
    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        /* Do nothing - a bug in JDisc prevents empty request chains */
    }
}
