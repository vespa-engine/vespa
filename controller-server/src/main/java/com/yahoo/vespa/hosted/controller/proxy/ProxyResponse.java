// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Response class that also rewrites URL from config server.
 *
 * @author Haakon Dybdahl
 */
public class ProxyResponse extends HttpResponse {

    private final String bodyResponseRewritten;
    private final String contentType;

    public ProxyResponse(
            ProxyRequest controllerRequest,
            String bodyResponse,
            int statusResponse,
            URI configServer,
            String contentType) {
        super(statusResponse);
        this.contentType = contentType;

        String configServerPrefix = HttpURL.from(configServer).withPath(Path.empty()).asURI().toString();
        String controllerRequestPrefix = controllerRequest.getControllerPrefixUri().toString();
        bodyResponseRewritten = bodyResponse.replace(configServerPrefix, controllerRequestPrefix);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        stream.write(bodyResponseRewritten.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() { return contentType; }
}
