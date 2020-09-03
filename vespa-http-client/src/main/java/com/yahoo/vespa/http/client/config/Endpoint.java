// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.config;

import java.io.Serializable;
import java.net.URL;

/**
 * Represents an endpoint, in most cases a JDisc container
 * in a Vespa cluster configured with <code>document-api</code>.
 *
 * @author Einar M R Rosenvinge
 */
public final class Endpoint implements Serializable {

    /**
     * Creates an Endpoint with the default port and without using SSL.
     *
     * @param hostname the hostname
     * @return an Endpoint instance
     */
    public static Endpoint create(String hostname) {
        return new Endpoint(hostname, DEFAULT_PORT, false);
    }

    /**
     * Creates an Endpoint with the given hostname, port and SSL setting.
     *
     * @param hostname the hostname
     * @param port the port
     * @param useSsl true if SSL is to be used
     * @return an Endpoint instance
     */
    public static Endpoint create(String hostname, int port, boolean useSsl) {
        return new Endpoint(hostname, port, useSsl);
    }

    public static Endpoint create(URL url) {
        return new Endpoint(url.getHost(), url.getPort(), "https".equals(url.getProtocol()));
    }

    private static final long serialVersionUID = 4545345L;
    private final String hostname;
    private final int port;
    private final boolean useSsl;
    private static final int DEFAULT_PORT = 4080;
    private Endpoint(String hostname, int port, boolean useSsl) {
        if (hostname.startsWith("https://")) {
            throw new RuntimeException("Hostname should be name of machine, not prefixed with protocol (https://)");
        }
        // A lot of people put http:// before the servername, let us allow that.
        if (hostname.startsWith("http://")) {
            this.hostname = hostname.replaceFirst("http://", "");
        } else {
            this.hostname = hostname;
        }
        this.port = port;
        this.useSsl = useSsl;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    @Override
    public String toString() {
        return hostname + ":" + port + " ssl=" + useSsl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Endpoint)) return false;
        Endpoint endpoint = (Endpoint) o;
        return port == endpoint.port && useSsl == endpoint.useSsl && hostname.equals(endpoint.hostname);
    }

    @Override
    public int hashCode() {
        int result = hostname.hashCode();
        result = 31 * result + port;
        result = 31 * result + (useSsl ? 1 : 0);
        return result;
    }

}
