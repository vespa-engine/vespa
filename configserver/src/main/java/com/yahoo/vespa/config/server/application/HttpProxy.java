// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Scheme;
import com.google.inject.Inject;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.HttpFetcher;
import com.yahoo.vespa.config.server.http.HttpFetcher.Params;
import com.yahoo.vespa.config.server.http.NotFoundException;
import com.yahoo.vespa.config.server.http.SimpleHttpFetcher;

import java.net.MalformedURLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpProxy {

    private static final Logger logger = Logger.getLogger(HttpProxy.class.getName());

    private final HttpFetcher fetcher;

    @Inject
    public HttpProxy() { this(new SimpleHttpFetcher()); }
    public HttpProxy(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public HttpResponse get(Application application, String hostName, String serviceType, Path relativePath) {
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
                               .filter(portInfo -> portInfo.getTags().containsAll(List.of("http", "state")))
                               .findFirst()
                               .orElseThrow(() -> new NotFoundException("Failed to find HTTP state port"));

        return internalGet(host.getHostname(), port.getPort(), relativePath);
    }

    private HttpResponse internalGet(String hostname, int port, Path relativePath) {
        HttpURL url = HttpURL.create(Scheme.http, DomainName.of(hostname), port, relativePath);
        try {
            return fetcher.get(new Params(2000), // 2_000 ms read timeout
                               url.asURI().toURL());
        } catch (MalformedURLException e) {
            logger.log(Level.WARNING, "Badly formed url: " + url, e);
            return HttpErrorResponse.internalServerError("Failed to construct URL for backend");
        }
    }

}
