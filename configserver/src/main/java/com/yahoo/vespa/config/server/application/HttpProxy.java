// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpFetcher;
import com.yahoo.vespa.config.server.http.NotFoundException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HttpProxy {
    private static Logger logger = Logger.getLogger(HttpProxy.class.getName());

    private final HttpFetcher fetcher;

    public HttpProxy(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public HttpResponse get(Application application, String hostName, String serviceType, String relativePath) {
        HostInfo host = application.getModel().getHosts().stream()
                .filter(hostInfo -> hostInfo.getHostname().equals(hostName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Failed to find host " + hostName));

        ServiceInfo service = host.getServices().stream()
                .filter(serviceInfo -> serviceType.equals(serviceInfo.getServiceType()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Failed to find any service of type " + serviceType
                        + " on host " + hostName));

        // "http" and "state" seems to uniquely identify an interesting HTTP port on each service
        PortInfo port = service.getPorts().stream()
                .filter(portInfo -> portInfo.getTags().stream().collect(Collectors.toSet()).containsAll(
                        Stream.of("http", "state").collect(Collectors.toSet())))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Failed to find HTTP state port"));

        return internalGet(host.getHostname(), port.getPort(), relativePath);
    }

    private HttpResponse internalGet(String hostname, int port, String relativePath) {
        String urlString = "http://" + hostname + ":" + port + "/" + relativePath;
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            logger.log(LogLevel.WARNING, "Badly formed url: " + urlString, e);
            return HttpErrorResponse.internalServerError("Failed to construct URL for backend");
        }

        HttpFetcher.Params params = new HttpFetcher.Params(2000); // 2_000 ms read timeout
        return fetcher.get(params, url);
    }
}
