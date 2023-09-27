package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.restapi.SlimeJsonResponse;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.Executor;

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
