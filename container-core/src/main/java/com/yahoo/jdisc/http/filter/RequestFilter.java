// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.component.chain.model.Chainable;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;

/**
 * @author Einar M R Rosenvinge
 */
public interface RequestFilter extends com.yahoo.jdisc.SharedResource, RequestFilterBase, Chainable {

    void filter(HttpRequest request, ResponseHandler handler);

}
