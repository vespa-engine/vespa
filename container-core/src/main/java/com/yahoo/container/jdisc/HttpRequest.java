// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.HttpRequest.Version;
import com.yahoo.jdisc.http.server.jetty.AccessLoggingRequestHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.processing.request.Properties;

import java.io.InputStream;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.yahoo.jdisc.http.HttpRequest.Method;

/**
 * Wraps a JDisc HTTP request for a synchronous API.
 * <p>
 * The properties of this request represents what was received in the request
 * and are thus immutable. If you need mutable abstractions, use a higher level
 * framework, e.g. Processing.
 *
 * @author hmusum
 * @author Steinar Knutsen
 */
public class HttpRequest {

    private final com.yahoo.jdisc.http.HttpRequest parentRequest;
    private final Map<String, String> properties;
    private final InputStream requestData;

    /**
     * Builder of HTTP requests
     */
    public static class Builder {

        private final HttpRequest parent;
        private com.yahoo.jdisc.http.HttpRequest jdiscRequest;
        Method method = null;
        Version version = null;
        Map<String, String> properties = new HashMap<>();
        InputStream requestData = null;
        URI uri = null;
        CurrentContainer container = null;
        private static final String nag = " must be set before the attempted operation.";
        SocketAddress remoteAddress;

        private void boom(Object ref, String what) {
            if (ref == null) {
                throw new IllegalStateException(what + nag);
            }
        }

        private void requireUri() {
            boom(uri, "An URI");
        }

        private void requireContainer() {
            boom(container, "A CurrentContainer instance");
        }

        private void ensureJdiscParent() {
            if (jdiscRequest == null) {
                if (parent == null) {
                    throw new IllegalStateException("Neither another HttpRequest nor JDisc request available.");
                } else {
                    jdiscRequest = parent.getJDiscRequest();
                }
            }
        }

        private void ensureRequestData() {
            if (requestData == null) {
                if (parent == null) {
                    throw new IllegalStateException(
                            "Neither another HttpRequest nor request data input stream available.");
                } else {
                    requestData = parent.getData();
                }
            }
        }

        /**
         * Instantiate a request builder with defaults from an existing request.
         * If the request is null, a JDisc request must be set explitly using
         * {@link #jdiscRequest(com.yahoo.jdisc.http.HttpRequest)} before
         * instantiating any HTTP request.
         *
         * @param request source for defaults and parent JDisc request, may be null
         * @see HttpRequest#createTestRequest(String, com.yahoo.jdisc.http.HttpRequest.Method)
         */
        public Builder(HttpRequest request) {
            this(request, request.getJDiscRequest());
        }

        /**
         * Instantiate a request builder with defaults from an existing request.
         *
         * @param request parent JDisc request
         * @see HttpRequest#createTestRequest(String, com.yahoo.jdisc.http.HttpRequest.Method)
         */
        public Builder(com.yahoo.jdisc.http.HttpRequest request) {
            this(null, request);
        }

        private Builder(HttpRequest parent, com.yahoo.jdisc.http.HttpRequest jdiscRequest) {
            this.parent = parent;
            this.jdiscRequest = jdiscRequest;
            populateProperties();

        }

        private void populateProperties() {
            if (parent == null) return;

            properties.putAll(parent.propertyMap());
        }

        /**
         * Add a parameter to the request. Multi-value parameters are not supported.
         *
         * @param key parameter name
         * @param value parameter value
         * @return this Builder instance
         */
        public Builder put(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         *  Removes the parameter from the request properties.
         *  If there is no such parameter, nothing will be done.
         */
        public Builder removeProperty(String parameterName) {
            properties.remove(parameterName);
            return this;
        }

        /**
         * Set the HTTP method for the new request.
         *
         * @param method the HTTP method to use for the new request
         * @return this Builder instance
         */
        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        /**
         * Define the JDisc parent request.
         *
         * @param request a valid JDisc request for the current container
         * @return this Builder instance
         */
        public Builder jdiscRequest(com.yahoo.jdisc.http.HttpRequest request) {
            this.jdiscRequest = request;
            return this;
        }

        /**
         * Set an inputstream to use for the request. If not set, the data from
         * the original HttpRequest is used.
         *
         * @param requestData data to be consumed, e.g. POST data
         * @return this Builder instance
         */
        public Builder requestData(InputStream requestData) {
            this.requestData = requestData;
            return this;
        }

        /**
         * Set the URI of the server request created.
         *
         * @param uri a valid URI for a server request
         * @return this Builder instance
         */
        public Builder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Create a new HTTP request without creating a new JDisc request. This
         * is for scenarios where another HTTP request handler is invoked
         * directly without dispatching through JDisc. The parent JDisc request
         * for the original HttpRequest will be passed on the new HttpRequest
         * instance's JDisc request, but no properties will be propagated into
         * the original JDisc request.
         *
         * @return a new HttpRequest instance reflecting the given request data and parameters
         */
        public HttpRequest createDirectRequest() {
            ensureRequestData();
            ensureJdiscParent();
            return new HttpRequest(jdiscRequest, requestData, properties);
        }

        /**
         * Start of API for synchronous HTTP request dispatch. Not yet ready for use.
         *
         * @return a new client request
         */
        public HttpRequest createClientRequest() {
            ensureJdiscParent();
            requireUri();
            com.yahoo.jdisc.http.HttpRequest clientRequest;
            if (method == null) {
                clientRequest = com.yahoo.jdisc.http.HttpRequest
                        .newClientRequest(jdiscRequest, uri);
            } else {
                if (version == null) {
                    clientRequest = com.yahoo.jdisc.http.HttpRequest
                            .newClientRequest(jdiscRequest, uri, method);
                } else {
                    clientRequest = com.yahoo.jdisc.http.HttpRequest
                            .newClientRequest(jdiscRequest, uri, method,
                                    version);
                }
            }
            setParameters(clientRequest);
            // TODO set requestData sanely
            return new HttpRequest(clientRequest, requestData, properties);
        }

        /**
         * Start of API for synchronous HTTP request dispatch. Not yet ready for use.
         *
         * @return a new server request
         */
        public HttpRequest createServerRequest() {
            requireUri();
            requireContainer();
            com.yahoo.jdisc.http.HttpRequest serverRequest;
            if (method == null) {
                serverRequest = com.yahoo.jdisc.http.HttpRequest
                        .newServerRequest(container, uri);
            } else {
                if (version == null) {
                    serverRequest = com.yahoo.jdisc.http.HttpRequest
                            .newServerRequest(container, uri, method);
                } else {
                    if (remoteAddress == null) {
                        serverRequest = com.yahoo.jdisc.http.HttpRequest
                                .newServerRequest(container, uri, method,
                                        version);
                    } else {
                        serverRequest = com.yahoo.jdisc.http.HttpRequest
                                .newServerRequest(container, uri, method,
                                        version, remoteAddress);
                    }
                }
            }
            setParameters(serverRequest);
            // TODO IO wiring
            return new HttpRequest(serverRequest, requestData, properties);
        }

        private void setParameters(com.yahoo.jdisc.http.HttpRequest request) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                request.parameters().put(entry.getKey(), wrap(entry.getValue()));
            }
        }

    }

    /**
     * Wrap a JDisc HTTP request in a synchronous API. The properties from the
     * JDisc request will be copied into the HTTP request.
     *
     * @param jdiscHttpRequest the JDisc request
     * @param requestData the associated input stream, e.g. with POST request
     */
    public HttpRequest(com.yahoo.jdisc.http.HttpRequest jdiscHttpRequest, InputStream requestData) {
        this(jdiscHttpRequest, requestData, null);
    }

    /**
     * Wrap a JDisc HTTP request in a synchronous API. The properties from the
     * JDisc request will be copied into the HTTP request. The mappings in
     * propertyOverrides will mask the settings in the JDisc request. The
     * content of propertyOverrides will be copied, so it is safe to re-use and
     * changes in propertyOverrides after constructing the HttpRequest instance
     * will obviously not be reflected by the request. The same applies for
     * JDisc parameters.
     *
     * @param jdiscHttpRequest the JDisc request
     * @param requestData the associated input stream, e.g. with POST request
     * @param propertyOverrides properties which should not have the same settings as in the
     *                          parent JDisc request, may be null
     */
    public HttpRequest(com.yahoo.jdisc.http.HttpRequest jdiscHttpRequest,
                       InputStream requestData, Map<String, String> propertyOverrides) {
        parentRequest = jdiscHttpRequest;
        this.requestData = requestData;
        properties = copyProperties(jdiscHttpRequest.parameters(), propertyOverrides);
    }

    /**
     * Create a new HTTP request from an URI.
     *
     * @param container the current container instance
     * @param uri the request parameters
     * @param method GET, POST, etc
     * @param requestData the associated data stream, may be null
     * @return a new HTTP request
     */
    public static HttpRequest createRequest(CurrentContainer container, URI uri,
                                            Method method, InputStream requestData) {
        return createRequest(container, uri, method, requestData, null);
    }

    /**
     * Create a new HTTP request from an URI.
     *
     * @param container the current container instance
     * @param uri the request parameters
     * @param method GET, POST, etc
     * @param requestData the associated data stream, may be null
     * @param properties a set of properties to set in the request in addition to the implicit ones from the URI
     * @return a new HTTP request
     */
    public static HttpRequest createRequest(CurrentContainer container,
                                            URI uri, Method method, InputStream requestData,
                                            Map<String, String> properties) {
        com.yahoo.jdisc.http.HttpRequest clientRequest = 
                com.yahoo.jdisc.http.HttpRequest.newClientRequest(new Request(container, uri), uri, method);
        setProperties(clientRequest, properties);
        return new HttpRequest(clientRequest, requestData);
    }

    private static void setProperties(com.yahoo.jdisc.http.HttpRequest clientRequest, Map<String, String> properties) {
        if (properties == null) return;

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            clientRequest.parameters().put(entry.getKey(), wrap(entry.getValue()));
        }
    }

    // conservative code in case anything else depends on modifying these lists
    private static List<String> wrap(String value) {
        List<String> l = new ArrayList<>(4);
        l.add(value);
        return l;
    }

    public static Optional<HttpRequest> getHttpRequest(com.yahoo.processing.Request processingRequest) {
        final Properties requestProperties = processingRequest.properties();
        return Optional.ofNullable(
                (HttpRequest) requestProperties.get(com.yahoo.processing.Request.JDISC_REQUEST));
    }

    public Optional<AccessLogEntry> getAccessLogEntry() {
        return Optional.of(getJDiscRequest())
                .flatMap(AccessLoggingRequestHandler::getAccessLogEntry);
    }

    private static URI createUri(String request) {
        final URI uri;
        try {
            uri = new URI(request);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        return uri;
    }

    /**
     * Only for simpler unit testing.
     *
     * @param uri the complete URI string
     * @param method POST, GET, etc
     * @return a valid HTTP request
     */
    public static HttpRequest createTestRequest(String uri, Method method) {
        return createTestRequest(uri, method, null);
    }

    /**
     * Only for simpler unit testing.
     *
     * @param uri the complete URI string
     * @param method POST, GET, etc
     * @param requestData for simulating POST
     * @return a valid HTTP request
     */
    public static HttpRequest createTestRequest(String uri, Method method, InputStream requestData) {
        return createTestRequest(uri, method, requestData, null);
    }

    public static HttpRequest createTestRequest(String uri, Method method, InputStream requestData, Map<String, String> properties) {
        return createRequest(new MockCurrentContainer(), createUri(uri), method, requestData, properties);
    }

    private static Map<String, String> copyProperties(Map<String, List<String>> parameters, Map<String, String> parameterMask) {
        Map<String, String> mask;
        Map<String, String> view;

        mask = Objects.requireNonNullElse(parameterMask, Collections.emptyMap());
        view = new HashMap<>(parameters.size() + mask.size());
        for (Map.Entry<String, List<String>> parameter : parameters.entrySet()) {
            if (existsAsOriginalParameter(parameter.getValue())) {
                List<String> values = parameter.getValue();
                view.put(parameter.getKey(), values.get(values.size() - 1)); // prefer the last value
            }
        }
        view.putAll(mask);
        return Collections.unmodifiableMap(view);
    }

    private static boolean existsAsOriginalParameter(List<String> value) {
        return value != null && value.size() > 0 && value.get(0) != null;
    }

    /**
     * Return the HTTP method (GET, POST...) of the incoming request.
     *
     * @return a Method instance matching the HTTP method of the request
     */
    public Method getMethod() {
        return parentRequest.getMethod();
    }

    /**
     * Get the full URI corresponding to this request.
     *
     * @return the URI of this request
     */
    public URI getUri() {
        return parentRequest.getUri();
    }

    /**
     * Access the underlying JDisc for this HTTP request.
     *
     * @return the corresponding JDisc request instance
     */
    public com.yahoo.jdisc.http.HttpRequest getJDiscRequest() {
        return parentRequest;
    }

    /**
     * Returns the value of a request property/parameter.
     * Multi-value properties are not supported.
     *
     * @param name the name of the URI property to return
     * @return the value of the property in question, or null if not present
     */
    public String getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Return a read-only view of the request parameters. Multi-value parameters
     * are not supported.
     *
     * @return a map containing all the parameters in the request
     */
    public Map<String, String> propertyMap() {
        return properties;
    }

    /**
     * Helper method to parse boolean request flags, using
     * Boolean.parseBoolean(String). Unset values are regarded as false.
     *
     * @param name the name of a request property
     * @return whether the property has been explicitly set to true
     */
    public boolean getBooleanProperty(String name) {
        if (getProperty(name) == null) {
            return false;
        }
        return Boolean.parseBoolean(getProperty(name));
    }

    /**
     * Check whether a property exists.
     *
     * @param name the name of a request property
     * @return true if the property has a value
     */
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    /**
     * Access an HTTP header in the request. Multi-value headers are not supported.
     *
     * @param name the name of an HTTP header
     * @return the first pertinent value
     */
    public String getHeader(String name) {
        if (parentRequest.headers().get(name) == null)
            return null;
        return parentRequest.headers().get(name).get(0);
    }

    /** Get the host segment of the URI of this request. */
    public String getHost() {
        return getUri().getHost();
    }

    /** The port of the URI of this request. */
    public int getPort() {
        return getUri().getPort();
    }

    /**
     * The input stream for this request, i.e. data POSTed from the client. A
     * client may read as much or as little data as needed from this stream,
     * draining and closing will be done by the RequestHandler base classes
     * using this HttpRequest (sub-)class. In other words, this stream should
     * not be closed after use.
     *
     * @return the stream with the client data for this request
     */
    public InputStream getData() {
        return requestData;
    }

    /**
     * Helper class for testing only.
     */
    private static class MockCurrentContainer implements CurrentContainer {
        @Override
        public Container newReference(URI uri) {
            return new Container() {

                @Override
                public RequestHandler resolveHandler(com.yahoo.jdisc.Request request) {
                    return null;
                }

                @Override
                public <T> T getInstance(Class<T> tClass) {
                    return null;
                }

                @Override
                public ResourceReference refer() {
                    return References.NOOP_REFERENCE;
                }

                @Override
                public void release() {
                    // NOP
                }

                @Override
                public long currentTimeMillis() {
                    return System.currentTimeMillis();
                }
            };
        }
    }

}
