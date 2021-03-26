// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.InputStream;
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

    HttpResponse handleRequest(HttpRequest request);

    interface Builder {
        Builder setObjectMapper(ObjectMapper mapper);
        Builder setDefaultRoute(RouteBuilder route);
        Builder addRoute(RouteBuilder route);
        Builder addFilter(Filter filter);
        <EXCEPTION extends RuntimeException> Builder addExceptionMapper(Class<EXCEPTION> type, ExceptionMapper<EXCEPTION> mapper);
        <ENTITY> Builder addResponseMapper(Class<ENTITY> type, ResponseMapper<ENTITY> mapper);
        <ENTITY> Builder addRequestMapper(Class<ENTITY> type, RequestMapper<ENTITY> mapper);
        <ENTITY> Builder registerJacksonResponseEntity(Class<ENTITY> type);
        <ENTITY> Builder registerJacksonRequestEntity(Class<ENTITY> type);
        Builder disableDefaultExceptionMappers();
        Builder disableDefaultResponseMappers();
        RestApi build();
    }

    interface RouteBuilder {
        RouteBuilder name(String name);
        RouteBuilder get(Handler<?> handler);
        RouteBuilder post(Handler<?> handler);
        <ENTITY> RouteBuilder post(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler);
        RouteBuilder put(Handler<?> handler);
        <ENTITY> RouteBuilder put(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler);
        RouteBuilder delete(Handler<?> handler);
        RouteBuilder patch(Handler<?> handler);
        <ENTITY> RouteBuilder patch(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler);
        RouteBuilder defaultHandler(Handler<?> handler);
        <ENTITY> RouteBuilder defaultHandler(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler);
        RouteBuilder addFilter(Filter filter);
    }

    @FunctionalInterface interface Handler<ENTITY> {
        ENTITY handleRequest(RequestContext context) throws RestApiException;
    }

    @FunctionalInterface interface HandlerWithRequestEntity<REQUEST_ENTITY, RESPONSE_ENTITY> {
        RESPONSE_ENTITY handleRequest(RequestContext context, REQUEST_ENTITY requestEntity) throws RestApiException;
    }

    @FunctionalInterface interface ExceptionMapper<EXCEPTION extends RuntimeException> { HttpResponse toResponse(RequestContext context, EXCEPTION exception); }

    @FunctionalInterface interface ResponseMapper<ENTITY> { HttpResponse toHttpResponse(RequestContext context, ENTITY responseEntity) throws RestApiException; }

    @FunctionalInterface interface RequestMapper<ENTITY> { Optional<ENTITY> toRequestEntity(RequestContext context) throws RestApiException; }

    @FunctionalInterface interface Filter { HttpResponse filterRequest(FilterContext context); }

    interface RequestContext {
        HttpRequest request();
        PathParameters pathParameters();
        QueryParameters queryParameters();
        Headers headers();
        Attributes attributes();
        Optional<RequestContent> requestContent();
        RequestContent requestContentOrThrow();
        ObjectMapper jacksonJsonMapper();

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

        interface PathParameters extends Parameters {}
        interface QueryParameters extends Parameters {}
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
