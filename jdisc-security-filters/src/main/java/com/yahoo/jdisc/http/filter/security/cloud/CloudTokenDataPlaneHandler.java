// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.restapi.SlimeJsonResponse;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author jonmv
 */
public class CloudTokenDataPlaneHandler extends ThreadedHttpRequestHandler {

    private final List<String> fingerprints;

    @Inject
    public CloudTokenDataPlaneHandler(CloudTokenDataPlaneFilterConfig config, Executor executor) {
        super(executor);
        fingerprints = config.clients().stream()
                             .flatMap(client -> client.tokens().stream())
                             .flatMap(token -> token.fingerprints().stream())
                             .distinct().sorted().toList();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new SlimeJsonResponse() {{ fingerprints.forEach(slime.setObject().setArray("fingerprints")::addString); }};
    }

}
