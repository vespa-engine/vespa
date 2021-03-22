// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bjorncs
 */
class RestApiImpl implements RestApi {

    private static final Logger log = Logger.getLogger(RestApiImpl.class.getName());

    private final Route defaultRoute;
    private final List<Route> routes;
    private final List<ExceptionMapperHolder<?>> exceptionMappers;
    private final List<ResponseMapperHolder<?>> responseMappers;
    private final List<Filter> filters;
    private final ObjectMapper jacksonJsonMapper;

    private RestApiImpl(RestApi.Builder builder) {
        BuilderImpl builderImpl = (BuilderImpl) builder;
        ObjectMapper jacksonJsonMapper = builderImpl.jacksonJsonMapper != null ? builderImpl.jacksonJsonMapper : JacksonJsonMapper.instance.copy();
        this.defaultRoute = builderImpl.defaultRoute != null ? builderImpl.defaultRoute : createDefaultRoute();
        this.routes = List.copyOf(builderImpl.routes);
        this.exceptionMappers = combineWithDefaultExceptionMappers(
                builderImpl.exceptionMappers, Boolean.TRUE.equals(builderImpl.disableDefaultExceptionMappers));
        this.responseMappers = combineWithDefaultResponseMappers(
                builderImpl.responseMappers, jacksonJsonMapper, Boolean.TRUE.equals(builderImpl.disableDefaultResponseMappers));
        this.filters = List.copyOf(builderImpl.filters);
        this.jacksonJsonMapper = jacksonJsonMapper;
    }

    @Override
    public HttpResponse handleRequest(HttpRequest request) {
        Path pathMatcher = new Path(request.getUri());
        Route resolvedRoute = resolveRoute(pathMatcher);
        RequestContextImpl requestContext = new RequestContextImpl(request, pathMatcher, jacksonJsonMapper);
        FilterContextImpl filterContext =
                createFilterContextRecursive(
                        resolvedRoute, requestContext, filters,
                        createFilterContextRecursive(resolvedRoute, requestContext, resolvedRoute.filters, null));
        if (filterContext != null) {
            return filterContext.executeFirst();
        } else {
            return dispatchToRoute(resolvedRoute, requestContext);
        }
    }

    @Override public ObjectMapper jacksonJsonMapper() { return jacksonJsonMapper; }

    private HttpResponse dispatchToRoute(Route route, RequestContextImpl context) {
        RestApi.MethodHandler<?> resolvedHandler = route.handlerPerMethod.get(context.request().getMethod());
        if (resolvedHandler == null) {
            resolvedHandler = route.defaultHandler;
        }
        Object entity;
        try {
            entity = resolvedHandler.handleRequest(context);
        } catch (RuntimeException e) {
            ExceptionMapperHolder<?> mapper = exceptionMappers.stream()
                    .filter(holder -> holder.matches(e))
                    .findFirst().orElseThrow(() -> e);
            return mapper.toResponse(e, context);
        }
        if (entity == null) throw new NullPointerException("Handler must return non-null value");
        ResponseMapperHolder<?> mapper = responseMappers.stream()
                .filter(holder -> holder.matches(entity))
                .findFirst().orElseThrow(() -> new IllegalStateException("No mapper configured for " + entity.getClass()));
        return mapper.toHttpResponse(entity, context);
    }

    private Route resolveRoute(Path pathMatcher) {
        Route matchingRoute = routes.stream()
                .filter(route -> pathMatcher.matches(route.pathPattern))
                .findFirst()
                .orElse(null);
        if (matchingRoute != null) return matchingRoute;
        pathMatcher.matches(defaultRoute.pathPattern); // to populate any path parameters
        return defaultRoute;
    }

    private FilterContextImpl createFilterContextRecursive(
            Route route, RequestContextImpl requestContext, List<Filter> filters, FilterContextImpl previousContext) {
        FilterContextImpl filterContext = previousContext;
        ListIterator<Filter> iterator = filters.listIterator(filters.size());
        while (iterator.hasPrevious()) {
            filterContext = new FilterContextImpl(route, iterator.previous(), requestContext, filterContext);
        }
        return filterContext;
    }

    private static Route createDefaultRoute() {
        RouteBuilder routeBuilder = new RouteBuilderImpl("{*}")
                .defaultHandler(context -> {
                    throw new RestApiException.NotFoundException();
                });
        return ((RouteBuilderImpl)routeBuilder).build();
    }

    private static List<ExceptionMapperHolder<?>> combineWithDefaultExceptionMappers(
            List<ExceptionMapperHolder<?>> configuredExceptionMappers, boolean disableDefaultMappers) {
        List<ExceptionMapperHolder<?>> exceptionMappers = new ArrayList<>(configuredExceptionMappers);
        if (!disableDefaultMappers){
            exceptionMappers.add(new ExceptionMapperHolder<>(RestApiException.class, (exception, context) -> exception.response()));
        }
        return exceptionMappers;
    }

    private static List<ResponseMapperHolder<?>> combineWithDefaultResponseMappers(
            List<ResponseMapperHolder<?>> configuredResponseMappers, ObjectMapper jacksonJsonMapper, boolean disableDefaultMappers) {
        List<ResponseMapperHolder<?>> responseMappers = new ArrayList<>(configuredResponseMappers);
        if (!disableDefaultMappers) {
            responseMappers.add(new ResponseMapperHolder<>(HttpResponse.class, (entity, context) -> entity));
            responseMappers.add(new ResponseMapperHolder<>(String.class, (entity, context) -> new MessageResponse(entity)));
            responseMappers.add(new ResponseMapperHolder<>(Slime.class, (entity, context) -> new SlimeJsonResponse(entity)));
            responseMappers.add(new ResponseMapperHolder<>(JsonNode.class, (entity, context) -> new JacksonJsonResponse<>(200, entity, jacksonJsonMapper, true)));
            responseMappers.add(new ResponseMapperHolder<>(RestApi.JacksonResponseEntity.class, (entity, context) -> new JacksonJsonResponse<>(200, entity, jacksonJsonMapper, true)));
        }
        return responseMappers;
    }

    static class BuilderImpl implements RestApi.Builder {
        private final List<Route> routes = new ArrayList<>();
        private final List<ExceptionMapperHolder<?>> exceptionMappers = new ArrayList<>();
        private final List<ResponseMapperHolder<?>> responseMappers = new ArrayList<>();
        private final List<RestApi.Filter> filters = new ArrayList<>();
        private Route defaultRoute;
        private ObjectMapper jacksonJsonMapper;
        private Boolean disableDefaultExceptionMappers;
        private Boolean disableDefaultResponseMappers;

        @Override public RestApi.Builder setObjectMapper(ObjectMapper mapper) { this.jacksonJsonMapper = mapper; return this; }
        @Override public RestApi.Builder setDefaultRoute(RestApi.RouteBuilder route) { this.defaultRoute = ((RouteBuilderImpl)route).build(); return this; }
        @Override public RestApi.Builder addRoute(RestApi.RouteBuilder route) { routes.add(((RouteBuilderImpl)route).build()); return this; }
        @Override public RestApi.Builder addFilter(RestApi.Filter filter) { filters.add(filter); return this; }

        @Override public <EXCEPTION extends RuntimeException> RestApi.Builder addExceptionMapper(Class<EXCEPTION> type, RestApi.ExceptionMapper<EXCEPTION> mapper) {
            exceptionMappers.add(new ExceptionMapperHolder<>(type, mapper)); return this;
        }

        @Override public <ENTITY> RestApi.Builder addResponseMapper(Class<ENTITY> type, RestApi.ResponseMapper<ENTITY> mapper) {
            responseMappers.add(new ResponseMapperHolder<>(type, mapper)); return this;
        }

        @Override public Builder disableDefaultExceptionMappers() { this.disableDefaultExceptionMappers = true; return this; }
        @Override public Builder disableDefaultResponseMappers() { this.disableDefaultResponseMappers = true; return this; }
        @Override public RestApi build() { return new RestApiImpl(this); }
    }

    public static class RouteBuilderImpl implements RestApi.RouteBuilder {
        private final String pathPattern;
        private String name;
        private final Map<com.yahoo.jdisc.http.HttpRequest.Method, RestApi.MethodHandler<?>> handlerPerMethod = new HashMap<>();
        private final List<RestApi.Filter> filters = new ArrayList<>();
        private RestApi.MethodHandler<?> defaultHandler;

        RouteBuilderImpl(String pathPattern) { this.pathPattern = pathPattern; }

        @Override public RestApi.RouteBuilder name(String name) { this.name = name; return this; }
        @Override public RestApi.RouteBuilder get(RestApi.MethodHandler<?> handler) { return addHandler(com.yahoo.jdisc.http.HttpRequest.Method.GET, handler); }
        @Override public RestApi.RouteBuilder post(RestApi.MethodHandler<?> handler) { return addHandler(com.yahoo.jdisc.http.HttpRequest.Method.POST, handler); }
        @Override public RestApi.RouteBuilder put(RestApi.MethodHandler<?> handler) { return addHandler(com.yahoo.jdisc.http.HttpRequest.Method.PUT, handler); }
        @Override public RestApi.RouteBuilder delete(RestApi.MethodHandler<?> handler) { return addHandler(com.yahoo.jdisc.http.HttpRequest.Method.DELETE, handler); }
        @Override public RestApi.RouteBuilder patch(RestApi.MethodHandler<?> handler) { return addHandler(com.yahoo.jdisc.http.HttpRequest.Method.PATCH, handler); }
        @Override public RestApi.RouteBuilder defaultHandler(RestApi.MethodHandler<?> handler) { defaultHandler = handler; return this; }
        @Override public RestApi.RouteBuilder addFilter(RestApi.Filter filter) { filters.add(filter); return this; }

        private RestApi.RouteBuilder addHandler(com.yahoo.jdisc.http.HttpRequest.Method method, RestApi.MethodHandler<?> handler) {
            handlerPerMethod.put(method, handler); return this;
        }

        private Route build() { return new Route(this); }
    }

    private static class RequestContextImpl implements RestApi.RequestContext {
        final HttpRequest request;
        final Path pathMatcher;
        final ObjectMapper jacksonJsonMapper;
        final PathParameters pathParameters = new PathParametersImpl();
        final QueryParameters queryParameters = new QueryParametersImpl();
        final Headers headers = new HeadersImpl();
        final Attributes attributes = new AttributesImpl();
        final RequestContent requestContent;

        RequestContextImpl(HttpRequest request, Path pathMatcher, ObjectMapper jacksonJsonMapper) {
            this.request = request;
            this.pathMatcher = pathMatcher;
            this.jacksonJsonMapper = jacksonJsonMapper;
            this.requestContent = request.getData() != null ? new RequestContentImpl() : null;
        }

        @Override public HttpRequest request() { return request; }
        @Override public PathParameters pathParameters() { return pathParameters; }
        @Override public QueryParameters queryParameters() { return queryParameters; }
        @Override public Headers headers() { return headers; }
        @Override public Attributes attributes() { return attributes; }
        @Override public Optional<RequestContent> requestContent() { return Optional.ofNullable(requestContent); }
        @Override public RequestContent requestContentOrThrow() {
            return requestContent().orElseThrow(() -> new RestApiException.BadRequest("Request content missing"));
        }

        private class PathParametersImpl implements RestApi.RequestContext.PathParameters {
            @Override
            public Optional<String> getString(String name) {
                if (name.equals("*")) {
                    String rest = pathMatcher.getRest();
                    return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
                }
                return Optional.ofNullable(pathMatcher.get(name));
            }
            @Override public String getStringOrThrow(String name) {
                return getString(name)
                        .orElseThrow(() -> new RestApiException.BadRequest("Path parameter '" + name + "' is missing"));
            }
        }

        private class QueryParametersImpl implements RestApi.RequestContext.QueryParameters {
            @Override public Optional<String> getString(String name) { return Optional.ofNullable(request.getProperty(name)); }
            @Override public String getStringOrThrow(String name) {
                return getString(name)
                        .orElseThrow(() -> new RestApiException.BadRequest("Query parameter '" + name + "' is missing"));
            }
        }

        private class HeadersImpl implements RestApi.RequestContext.Headers {
            @Override public Optional<String> getString(String name) { return Optional.ofNullable(request.getHeader(name)); }
            @Override public String getStringOrThrow(String name) {
                return getString(name)
                        .orElseThrow(() -> new RestApiException.BadRequest("Header '" + name + "' missing"));
            }
        }

        private class RequestContentImpl implements RestApi.RequestContext.RequestContent {
            @Override public String contentType() { return request.getHeader("Content-Type"); }
            @Override public InputStream inputStream() { return request.getData(); }
            @Override public ObjectMapper jacksonJsonMapper() { return jacksonJsonMapper; }
            @Override public byte[] consumeByteArray() { return convertIoException(() -> inputStream().readAllBytes()); }
            @Override public String consumeString() { return new String(consumeByteArray(), StandardCharsets.UTF_8); }

            @Override
            public JsonNode consumeJsonNode() {
                return convertIoException(() -> {
                    try {
                        if (log.isLoggable(Level.FINE)) {
                            String content = consumeString();
                            log.fine(() -> "Request content: " + content);
                            return jacksonJsonMapper.readTree(content);
                        } else {
                            return jacksonJsonMapper.readTree(request.getData());
                        }
                    } catch (com.fasterxml.jackson.core.JsonParseException e) {
                        log.log(Level.FINE, e.getMessage(), e);
                        throw new RestApiException.BadRequest("Invalid json request content: " + Exceptions.toMessageString(e), e);
                    }
                });
            }

            @Override
            public Slime consumeSlime() {
                try {
                    String content = consumeString();
                    log.fine(() -> "Request content: " + content);
                    return SlimeUtils.jsonToSlimeOrThrow(content);
                } catch (com.yahoo.slime.JsonParseException e) {
                    log.log(Level.FINE, e.getMessage(), e);
                    throw new RestApiException.BadRequest("Invalid json request content: " + Exceptions.toMessageString(e), e);
                }
            }

            @Override
            public <T extends JacksonRequestEntity> T consumeJacksonEntity(Class<T> type) {
                return convertIoException(() -> {
                    try {
                        if (log.isLoggable(Level.FINE)) {
                            String content = consumeString();
                            log.fine(() -> "Request content: " + content);
                            return jacksonJsonMapper.readValue(content, type);
                        } else {
                            return jacksonJsonMapper.readValue(request.getData(), type);
                        }
                    } catch (com.fasterxml.jackson.core.JsonParseException | JsonMappingException e) {
                        log.log(Level.FINE, e.getMessage(), e);
                        throw new RestApiException.BadRequest("Invalid json request content: " + Exceptions.toMessageString(e), e);
                    }
                });
            }
        }

        private class AttributesImpl implements RestApi.RequestContext.Attributes {
            @Override public Optional<Object> get(String name) { return Optional.ofNullable(request.getJDiscRequest().context().get(name)); }
            @Override public void set(String name, Object value) { request.getJDiscRequest().context().put(name, value); }
        }

        @FunctionalInterface private interface SupplierThrowingIoException<T> { T get() throws IOException; }
        private static <T> T convertIoException(SupplierThrowingIoException<T> supplier) {
            try {
                return supplier.get();
            } catch (IOException e) {
                throw new RestApiException.InternalServerError("Failed to read request content: " + Exceptions.toMessageString(e), e);
            }
        }
    }

    private class FilterContextImpl implements RestApi.FilterContext {
        final Route route;
        final RestApi.Filter filter;
        final RequestContextImpl requestContext;
        final FilterContextImpl next;

        FilterContextImpl(Route route, RestApi.Filter filter, RequestContextImpl requestContext, FilterContextImpl next) {
            this.route = route;
            this.filter = filter;
            this.requestContext = requestContext;
            this.next = next;
        }

        @Override public RestApi.RequestContext requestContext() { return requestContext; }
        @Override public String route() { return route.name != null ? route.name : route.pathPattern; }

        HttpResponse executeFirst() { return filter.filterRequest(this); }

        @Override
        public HttpResponse executeNext() {
            if (next != null) {
                return next.filter.filterRequest(next);
            } else {
                return dispatchToRoute(route, requestContext);
            }
        }
    }

    private static class ExceptionMapperHolder<EXCEPTION extends RuntimeException> {
        final Class<EXCEPTION> type;
        final RestApi.ExceptionMapper<EXCEPTION> mapper;

        ExceptionMapperHolder(Class<EXCEPTION> type, RestApi.ExceptionMapper<EXCEPTION> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        boolean matches(RuntimeException e) { return type.isAssignableFrom(e.getClass()); }
        HttpResponse toResponse(RuntimeException e, RestApi.RequestContext context) { return mapper.toResponse(type.cast(e), context); }
    }

    private static class ResponseMapperHolder<ENTITY> {
        final Class<ENTITY> type;
        final RestApi.ResponseMapper<ENTITY> mapper;

        ResponseMapperHolder(Class<ENTITY> type, RestApi.ResponseMapper<ENTITY> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        boolean matches(Object entity) { return type.isAssignableFrom(entity.getClass()); }
        HttpResponse toHttpResponse(Object entity, RestApi.RequestContext context) { return mapper.toHttpResponse(type.cast(entity), context); }
    }


    static class Route {
        private final String pathPattern;
        private final String name;
        private final Map<com.yahoo.jdisc.http.HttpRequest.Method, RestApi.MethodHandler<?>> handlerPerMethod;
        private final RestApi.MethodHandler<?> defaultHandler;
        private final List<Filter> filters;

        private Route(RestApi.RouteBuilder builder) {
            RouteBuilderImpl builderImpl = (RouteBuilderImpl)builder;
            this.pathPattern = builderImpl.pathPattern;
            this.name = builderImpl.name;
            this.handlerPerMethod = Map.copyOf(builderImpl.handlerPerMethod);
            this.defaultHandler = builderImpl.defaultHandler != null ? builderImpl.defaultHandler : createDefaultMethodHandler();
            this.filters = List.copyOf(builderImpl.filters);
        }

        private RestApi.MethodHandler<?> createDefaultMethodHandler() {
            return context -> { throw new RestApiException.MethodNotAllowed(context.request()); };
        }
    }

}
