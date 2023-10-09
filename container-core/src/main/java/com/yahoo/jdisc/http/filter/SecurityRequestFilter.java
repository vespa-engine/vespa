// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter;

import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author Simon Thoresen Hult
 */
public interface SecurityRequestFilter extends RequestFilterBase {

    void filter(DiscFilterRequest request, ResponseHandler handler);

}
