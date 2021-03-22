// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Slime;

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
    ObjectMapper jacksonJsonMapper();

    interface Builder {
        Builder setObjectMapper(ObjectMapper mapper);
        Builder setDefaultRoute(RouteBuilder route);
        Builder addRoute(RouteBuilder route);
        Builder addFilter(Filter filter);
        <EXCEPTION extends RuntimeException> Builder addExceptionMapper(Class<EXCEPTION> type, ExceptionMapper<EXCEPTION> mapper);
        <ENTITY> Builder addResponseMapper(Class<ENTITY> type, ResponseMapper<ENTITY> mapper);
        Builder disableDefaultExceptionMappers();
        Builder disableDefaultResponseMappers();
        RestApi build();
    }

    interface RouteBuilder {
        RouteBuilder name(String name);
        RouteBuilder get(MethodHandler<?> handler);
        RouteBuilder post(MethodHandler<?> handler);
        RouteBuilder put(MethodHandler<?> handler);
        RouteBuilder delete(MethodHandler<?> handler);
        RouteBuilder patch(MethodHandler<?> handler);
        RouteBuilder defaultHandler(MethodHandler<?> handler);
        RouteBuilder addFilter(Filter filter);
    }

    @FunctionalInterface interface ExceptionMapper<EXCEPTION extends RuntimeException> { HttpResponse toResponse(EXCEPTION exception, RequestContext context); }

    @FunctionalInterface interface MethodHandler<ENTITY> { ENTITY handleRequest(RequestContext context) throws RestApiException; }

    @FunctionalInterface interface ResponseMapper<ENTITY> { HttpResponse toHttpResponse(ENTITY responseEntity, RequestContext context); }

    @FunctionalInterface interface Filter { HttpResponse filterRequest(FilterContext context); }

    /** Marker interface required for automatic serialization of Jackson response entities */
    interface JacksonResponseEntity {}

    /** Marker interface required for automatic serialization of Jackson request entities */
    interface JacksonRequestEntity {}

    interface RequestContext {
        HttpRequest request();
        PathParameters pathParameters();
        QueryParameters queryParameters();
        Headers headers();
        Attributes attributes();
        Optional<RequestContent> requestContent();
        RequestContent requestContentOrThrow();

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
            InputStream inputStream();
            ObjectMapper jacksonJsonMapper();
            byte[] consumeByteArray();
            String consumeString();
            JsonNode consumeJsonNode();
            Slime consumeSlime();
            <T extends JacksonRequestEntity> T consumeJacksonEntity(Class<T> type);
        }
    }

    interface FilterContext {
        RequestContext requestContext();
        String route();
        HttpResponse executeNext();
    }
}
