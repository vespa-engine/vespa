// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig.Clients.Tokens;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * @author jonmv
 */
public class CloudTokenDataPlaneHandler extends ThreadedHttpRequestHandler {

    private final Map<String, Set<String>> tokens;

    @Inject
    public CloudTokenDataPlaneHandler(CloudTokenDataPlaneFilterConfig config, Executor executor) {
        super(executor);
        tokens = new TreeMap<>(config.clients().stream()
                                     .flatMap(client -> client.tokens().stream())
                                     .collect(groupingBy(Tokens::id,
                                                         flatMapping(token -> token.fingerprints().stream(),
                                                                     toCollection(TreeSet::new)))));
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        return new SlimeJsonResponse() {{
            Cursor tokensArray = slime.setObject().setArray("tokens");
            tokens.forEach((id, fingerprints) -> {
                Cursor tokenObject = tokensArray.addObject();
                tokenObject.setString("id", id);
                fingerprints.forEach(tokenObject.setArray("fingerprints")::addString);
            });
        }};
    }

}
