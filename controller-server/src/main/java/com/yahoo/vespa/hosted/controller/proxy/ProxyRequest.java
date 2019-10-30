// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.proxy;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.net.HostName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

/**
 * Keeping information about the calls that are being proxied.
 * A request is of form /zone/v2/[environment]/[region]/[config-server-path]
 *
 * @author Haakon Dybdahl
 */
public class ProxyRequest {

    private final String environment;
    private final String region;
    private final String configServerRequest;
    private final InputStream requestData;
    private final Map<String, List<String>> headers;
    private final String method;
    private final String controllerPrefix;
    private final String scheme;

    /**
     * The constructor calls exception if the request is invalid.
     *
     * @param request the request from the jdisc framework.
     * @param pathPrefix the path prefix of the proxy.
     * @throws ProxyException on errors
     */
    public ProxyRequest(HttpRequest request, String pathPrefix) throws ProxyException, IOException {
        this(request.getUri(), request.getJDiscRequest().headers(), request.getData(), request.getMethod().name(),
             pathPrefix);
    }

    ProxyRequest(URI requestUri, Map<String, List<String>> headers, InputStream body, String method,
                 String pathPrefix) throws ProxyException, IOException {
        if (requestUri == null) {
            throw new ProxyException(ErrorResponse.badRequest("Request not set."));
        }
        final String path = URLDecoder.decode(requestUri.getPath(),"UTF-8");
        if (! path.startsWith(pathPrefix)) {
            // This has to be caused by wrong mapping of path in services.xml.
            throw new ProxyException(ErrorResponse.notFoundError("Request not starting with " + pathPrefix));
        }
        final String uriNoPrefix = path.replaceFirst(pathPrefix, "")
                + (requestUri.getRawQuery() == null ? "" : "?" + requestUri.getRawQuery());

        final String[] parts = uriNoPrefix.split("/");

        this.environment = parts.length > 0 ? parts[0] : "";
        this.region = parts.length > 1 ? parts[1] : "";
        this.configServerRequest = parts.length > 2 ? uriNoPrefix.replace(environment + "/" + region, "") : "";
        this.requestData = body;
        this.headers = headers;
        this.method = method;

        String hostPort = headers.containsKey("host")
                ? headers.get("host").get(0)
                : HostName.getLocalhost() + ":" + requestUri.getPort();
        StringBuilder prefix = new StringBuilder(hostPort + pathPrefix);
        if (! environment.isEmpty()) {
            prefix.append(environment).append("/").append(region);
        }

        this.controllerPrefix = prefix.toString();
        this.scheme = requestUri.getScheme();
    }


    public String getRegion() {
        return region;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getConfigServerRequest() {
        return configServerRequest;
    }

    public InputStream getData() {
        return requestData;
    }

    @Override
    public String toString() {
        return "[ region: " + region + " env: " + environment + " request: " + configServerRequest + "]";
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getMethod() {
        return method;
    }

    public String getControllerPrefix() {
        return controllerPrefix;
    }

    public String getScheme() { return scheme; }

}
