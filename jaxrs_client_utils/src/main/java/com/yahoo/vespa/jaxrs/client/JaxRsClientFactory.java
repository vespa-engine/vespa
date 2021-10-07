// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;

import java.net.URI;
import java.time.Duration;

/**
 * Interface for creating a JAX-RS client API instance for a single server endpoint.
 *
 * @author bakksjo
 */
public interface JaxRsClientFactory {
    class Params<T> {
        private final Class<T> apiClass;
        private final URI uri;

        private Duration connectTimeout = Duration.ofSeconds(30);
        private Duration readTimeout = Duration.ofSeconds(30);

        public Params(Class<T> apiClass, URI uri) {
            this.apiClass = apiClass;
            this.uri = uri;
        }

        public Class<T> apiClass() {
            return apiClass;
        }

        public URI uri() {
            return uri;
        }

        public void setConnectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
        }

        public Duration connectTimeout() {
            return connectTimeout;
        }

        public void setReadTimeout(Duration timeout) {
            readTimeout = timeout;
        }

        public Duration readTimeout() {
            return readTimeout;
        }
    }

    default <T> T createClient(Params<T> params) {
        return createClient(params.apiClass, new HostName(params.uri.getHost()), params.uri.getPort(), params.uri.getPath(), params.uri.getScheme());
    }

    <T> T createClient(Class<T> apiClass, HostName hostName, int port, String pathPrefix, String scheme);
}
