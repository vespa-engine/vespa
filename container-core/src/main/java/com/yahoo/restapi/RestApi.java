// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import ai.vespa.http.HttpURL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.security.tls.Capability;
import com.yahoo.security.tls.CapabilitySet;
import com.yahoo.security.tls.ConnectionAuthContext;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * Rest API routing and response serialization
 *
 * @author bjorncs
 */
public interface RestApi {

    static Builder builder() { return new RestApiImpl.BuilderImpl(); }
    static RouteBuilder route(String pathPattern) { return new RestApiImpl.RouteBuilderImpl(pathPattern); }
    static HandlerConfigBuilder handlerConfig() { return new RestApiImpl.HandlerConfigBuilderImpl(); }

    HttpResponse handleRequest(HttpRequest request);
    ObjectMapper jacksonJsonMapper();

    /** @see com.yahoo.container.jdisc.HttpRequestHandler#requestHandlerSpec() */
    RequestHandlerSpec requestHandlerSpec();

    /** @see com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler */
    CapabilitySet requiredCapabilities(RequestView req);

    interface Builder {
        Builder setObjectMapper(ObjectMapper mapper);
        Builder setDefaultRoute(RouteBuilder route);
        Builder addRoute(RouteBuilder route);
        Builder addFilter(Filter filter);
        /** see {@link RestApiMappers#DEFAULT_EXCEPTION_MAPPERS} for default mappers */
        <EXCEPTION extends RuntimeException> Builder addExceptionMapper(Class<EXCEPTION> type, ExceptionMapper<EXCEPTION> mapper);
        /** see {@link RestApiMappers#DEFAULT_RESPONSE_MAPPERS} for default mappers */
        <RESPONSE_ENTITY> Builder addResponseMapper(Class<RESPONSE_ENTITY> type, ResponseMapper<RESPONSE_ENTITY> mapper);
        /** see {@link RestApiMappers#DEFAULT_REQUEST_MAPPERS} for default mappers */
        <REQUEST_ENTITY> Builder addRequestMapper(Class<REQUEST_ENTITY> type, RequestMapper<REQUEST_ENTITY> mapper);
        <RESPONSE_ENTITY> Builder registerJacksonResponseEntity(Class<RESPONSE_ENTITY> type);
        <REQUEST_ENTITY> Builder registerJacksonRequestEntity(Class<REQUEST_ENTITY> type);
        /** Disables mappers listed in {@link RestApiMappers#DEFAULT_EXCEPTION_MAPPERS} */
        Builder disableDefaultExceptionMappers();
        /** Disables mappers listed in {@link RestApiMappers#DEFAULT_RESPONSE_MAPPERS} */
        Builder disableDefaultResponseMappers();
        Builder disableDefaultAclMapping();
        Builder requiredCapabilities(Capability... capabilities);
        Builder requiredCapabilities(CapabilitySet capabilities);
        RestApi build();
    }

    interface RouteBuilder {
        RouteBuilder name(String name);
        RouteBuilder requiredCapabilities(Capability... capabilities);
        RouteBuilder requiredCapabilities(CapabilitySet capabilities);
        RouteBuilder addFilter(Filter filter);

        // GET
        RouteBuilder get(Handler<?> handler);
        RouteBuilder get(Handler<?> handler, HandlerConfigBuilder config);

        // POST
        RouteBuilder post(Handler<?> handler);
        <REQUEST_ENTITY> RouteBuilder post(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler);
        RouteBuilder post(Handler<?> handler, HandlerConfigBuilder config);
        <REQUEST_ENTITY> RouteBuilder post(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config);

        // PUT
        RouteBuilder put(Handler<?> handler);
        <REQUEST_ENTITY> RouteBuilder put(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler);
        RouteBuilder put(Handler<?> handler, HandlerConfigBuilder config);
        <REQUEST_ENTITY> RouteBuilder put(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config);

        // DELETE
        RouteBuilder delete(Handler<?> handler);
        RouteBuilder delete(Handler<?> handler, HandlerConfigBuilder config);

        // PATCH
        RouteBuilder patch(Handler<?> handler);
        <REQUEST_ENTITY> RouteBuilder patch(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler);
        RouteBuilder patch(Handler<?> handler, HandlerConfigBuilder config);
        <REQUEST_ENTITY> RouteBuilder patch(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config);

        // Default
        RouteBuilder defaultHandler(Handler<?> handler);
        RouteBuilder defaultHandler(Handler<?> handler, HandlerConfigBuilder config);
        <REQUEST_ENTITY> RouteBuilder defaultHandler(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler);
        <REQUEST_ENTITY> RouteBuilder defaultHandler(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config);
    }

    @FunctionalInterface interface Handler<RESPONSE_ENTITY> {
        RESPONSE_ENTITY handleRequest(RequestContext context) throws RestApiException;
    }

    @FunctionalInterface interface HandlerWithRequestEntity<REQUEST_ENTITY, RESPONSE_ENTITY> {
        RESPONSE_ENTITY handleRequest(RequestContext context, REQUEST_ENTITY requestEntity) throws RestApiException;
    }

    @FunctionalInterface interface ExceptionMapper<EXCEPTION extends RuntimeException> { HttpResponse toResponse(RequestContext context, EXCEPTION exception); }

    @FunctionalInterface interface ResponseMapper<RESPONSE_ENTITY> { HttpResponse toHttpResponse(RequestContext context, RESPONSE_ENTITY responseEntity) throws RestApiException; }

    @FunctionalInterface interface RequestMapper<REQUEST_ENTITY> { Optional<REQUEST_ENTITY> toRequestEntity(RequestContext context) throws RestApiException; }

    @FunctionalInterface interface Filter { HttpResponse filterRequest(FilterContext context); }

    interface HandlerConfigBuilder {
        HandlerConfigBuilder withRequiredCapabilities(Capability... capabilities);
        HandlerConfigBuilder withRequiredCapabilities(CapabilitySet capabilities);
        HandlerConfigBuilder withReadAclAction();
        HandlerConfigBuilder withWriteAclAction();
        HandlerConfigBuilder withCustomAclAction(AclMapping.Action action);
    }

    interface RequestContext {
        HttpRequest request();
        Method method();
        PathParameters pathParameters();
        QueryParameters queryParameters();
        Headers headers();
        Attributes attributes();
        Optional<RequestContent> requestContent();
        RequestContent requestContentOrThrow();
        ObjectMapper jacksonJsonMapper();
        /** Scheme, domain and port, for the original request. <em>Use this only for generating resources links, not for custom routing!</em> */
        // TODO: this needs to include path and query as well, to be useful for generating resource links that need not be rewritten.
        HttpURL baseRequestURL();
        AclMapping.Action aclAction();
        Optional<Principal> userPrincipal();
        Principal userPrincipalOrThrow();
        Optional<SSLSession> sslSession();
        Optional<ConnectionAuthContext> connectionAuthContext();

        interface Parameters {
            Optional<String> getString(String name);
            String getStringOrThrow(String name);
            default Optional<Boolean> getBoolean(String name) { return getString(name).map(Boolean::valueOf);}
            default boolean getBooleanOrThrow(String name) { return Boolean.parseBoolean(getStringOrThrow(name)); }
            default OptionalLong getLong(String name) {
                return getString(name).map(Long::parseLong).map(OptionalLong::of).orElseGet(OptionalLong::empty);
            }
            default long getLongOrThrow(String name) { return Long.parseLong(getStringOrThrow(name)); }
            default OptionalDouble getDouble(String name) {
                return getString(name).map(Double::parseDouble).map(OptionalDouble::of).orElseGet(OptionalDouble::empty);
            }
            default double getDoubleOrThrow(String name) { return Double.parseDouble(getStringOrThrow(name)); }
        }

        interface PathParameters extends Parameters {
            HttpURL.Path getFullPath();
            Optional<HttpURL.Path> getRest();
        }
        interface QueryParameters extends Parameters {
            HttpURL.Query getFullQuery();
            List<String> getStringList(String name);
        }
        interface Headers extends Parameters {}

        interface Attributes {
            Optional<Object> get(String name);
            void set(String name, Object value);
        }

        interface RequestContent {
            String contentType();
            InputStream content();
        }
    }

    interface FilterContext {
        RequestContext requestContext();
        String route();
        HttpResponse executeNext();
    }
}
