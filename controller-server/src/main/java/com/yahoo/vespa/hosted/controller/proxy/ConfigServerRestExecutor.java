// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.container.jdisc.HttpResponse;

/**
 * Executes call against config servers and handles discovery requests. Rest URIs in the response are
 * rewritten.
 *
 * @author Haakon Dybdahl
 */
public interface ConfigServerRestExecutor {

    HttpResponse handle(ProxyRequest request);

}
