// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.component.AbstractComponent;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.hosted.controller.proxy.ConfigServerRestExecutor;
import com.yahoo.vespa.hosted.controller.proxy.ProxyRequest;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;

import java.io.InputStream;
import java.util.Optional;
import java.util.Scanner;

/**
 * @author mpolden
 */
public class ConfigServerProxyMock extends AbstractComponent implements ConfigServerRestExecutor {

    private volatile ProxyRequest lastReceived = null;
    private volatile String requestBody = null;

    @Override
    public HttpResponse handle(ProxyRequest proxyRequest) {
        lastReceived = proxyRequest;
        // Copy request body as the input stream is drained once the request completes
        requestBody = asString(proxyRequest.getData());
        return new StringResponse("ok");
    }

    public Optional<ProxyRequest> lastReceived() {
        return Optional.ofNullable(lastReceived);
    }

    public Optional<String> lastRequestBody() {
        return Optional.ofNullable(requestBody);
    }

    private static String asString(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
