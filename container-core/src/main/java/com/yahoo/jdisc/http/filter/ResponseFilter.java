// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.SharedResource;

/**
 * @author Einar M R Rosenvinge
 */
public interface ResponseFilter extends SharedResource, ResponseFilterBase {

    public void filter(Response response, Request request);
}
