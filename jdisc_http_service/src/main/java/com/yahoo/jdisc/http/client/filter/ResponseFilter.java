// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client.filter;

/**
 * This interface can be implemented to define custom behavior that gets invoked before the response bytes are processed.
 * Authorization, proxy authentication and redirects processing all happen after the filters get executed.
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public interface ResponseFilter {

    public ResponseFilterContext filter(ResponseFilterContext filterContext) throws FilterException;

}
