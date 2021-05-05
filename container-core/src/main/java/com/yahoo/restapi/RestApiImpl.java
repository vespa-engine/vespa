// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author bjorncs
 */
class RestApiImpl implements RestApi {

    private static final Logger log = Logger.getLogger(RestApiImpl.class.getName());

    private final Route defaultRoute;
    private final List<Route> routes;
    private final List<ExceptionMapperHolder<?>> exceptionMappers;
    private final List<ResponseMapperHolder<?>> responseMappers;
    private final List<RequestMapperHolder<?>> requestMappers;
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
        this.requestMappers = combineWithDefaultRequestMappers(
                builderImpl.requestMappers, jacksonJsonMapper);
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
        HandlerHolder<?> resolvedHandler = resolveHandler(context, route);
        RequestMapperHolder<?> resolvedRequestMapper = resolveRequestMapper(resolvedHandler);
        Object requestEntity;
        try {
            requestEntity = resolvedRequestMapper.mapper.toRequestEntity(context).orElse(null);
        } catch (RuntimeException e) {
            return mapException(context, e);
        }
        Object responseEntity;
        try {
            responseEntity = resolvedHandler.executeHandler(context, requestEntity);
        } catch (RuntimeException e) {
            return mapException(context, e);
        }
        if (responseEntity == null) throw new NullPointerException("Handler must return non-null value");
        ResponseMapperHolder<?> resolvedResponseMapper = resolveResponseMapper(responseEntity);
        try {
            return resolvedResponseMapper.toHttpResponse(context, responseEntity);
        } catch (RuntimeException e) {
            return mapException(context, e);
        }
    }

    private HandlerHolder<?> resolveHandler(RequestContextImpl context, Route route) {
        HandlerHolder<?> resolvedHandler = route.handlerPerMethod.get(context.request().getMethod());
        return resolvedHandler == null ? route.defaultHandler : resolvedHandler;
    }

    private RequestMapperHolder<?> resolveRequestMapper(HandlerHolder<?> resolvedHandler) {
        return requestMappers.stream()
                .filter(holder -> resolvedHandler.type.isAssignableFrom(holder.type))
                .findFirst().orElseThrow(() -> new IllegalStateException("No mapper configured for " + resolvedHandler.type));
    }

    private ResponseMapperHolder<?> resolveResponseMapper(Object responseEntity) {
        return responseMappers.stream()
                .filter(holder -> holder.type.isAssignableFrom(responseEntity.getClass()))
                .findFirst().orElseThrow(() -> new IllegalStateException("No mapper configured for " + responseEntity.getClass()));
    }

    private HttpResponse mapException(RequestContextImpl context, RuntimeException e) {
        log.log(Level.FINE, e, e::getMessage);
        ExceptionMapperHolder<?> mapper = exceptionMappers.stream()
                .filter(holder -> holder.type.isAssignableFrom(e.getClass()))
                .findFirst().orElseThrow(() -> e);
        return mapper.toResponse(context, e);
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
                    throw new RestApiException.NotFound(context.request());
                });
        return ((RouteBuilderImpl)routeBuilder).build();
    }

    private static List<ExceptionMapperHolder<?>> combineWithDefaultExceptionMappers(
            List<ExceptionMapperHolder<?>> configuredExceptionMappers, boolean disableDefaultMappers) {
        List<ExceptionMapperHolder<?>> exceptionMappers = new ArrayList<>(configuredExceptionMappers);
        if (!disableDefaultMappers){
            exceptionMappers.add(new ExceptionMapperHolder<>(RestApiException.class, (context, exception) -> exception.response()));
        }
        // Topologically sort children before superclasses, so most the specific match is found by iterating through mappers in order.
        exceptionMappers.sort((a, b) -> (a.type.isAssignableFrom(b.type) ? 1 : 0) + (b.type.isAssignableFrom(a.type) ? -1 : 0));
        return exceptionMappers;
    }

    private static List<ResponseMapperHolder<?>> combineWithDefaultResponseMappers(
            List<ResponseMapperHolder<?>> configuredResponseMappers, ObjectMapper jacksonJsonMapper, boolean disableDefaultMappers) {
        List<ResponseMapperHolder<?>> responseMappers = new ArrayList<>(configuredResponseMappers);
        if (!disableDefaultMappers) {
            responseMappers.add(new ResponseMapperHolder<>(HttpResponse.class, (context, entity) -> entity));
            responseMappers.add(new ResponseMapperHolder<>(String.class, (context, entity) -> new MessageResponse(entity)));
            responseMappers.add(new ResponseMapperHolder<>(Slime.class, (context, entity) -> new SlimeJsonResponse(entity)));
            responseMappers.add(new ResponseMapperHolder<>(JsonNode.class, (context, entity) -> new JacksonJsonResponse<>(200, entity, jacksonJsonMapper, true)));
        }
        return responseMappers;
    }

    private static List<RequestMapperHolder<?>> combineWithDefaultRequestMappers(
            List<RequestMapperHolder<?>> configuredRequestMappers, ObjectMapper jacksonJsonMapper) {
        List<RequestMapperHolder<?>> requestMappers = new ArrayList<>(configuredRequestMappers);
        requestMappers.add(new RequestMapperHolder<>(Slime.class, RestApiImpl::toSlime));
        requestMappers.add(new RequestMapperHolder<>(JsonNode.class, ctx -> toJsonNode(ctx, jacksonJsonMapper)));
        requestMappers.add(new RequestMapperHolder<>(String.class, RestApiImpl::toString));
        requestMappers.add(new RequestMapperHolder<>(byte[].class, RestApiImpl::toByteArray));
        requestMappers.add(new RequestMapperHolder<>(InputStream.class, RestApiImpl::toInputStream));
        requestMappers.add(new RequestMapperHolder<>(Void.class, ctx -> Optional.empty()));
        return requestMappers;
    }

    private static Optional<InputStream> toInputStream(RequestContext context) {
        return context.requestContent().map(RequestContext.RequestContent::content);
    }

    private static Optional<byte[]> toByteArray(RequestContext context) {
        InputStream in = toInputStream(context).orElse(null);
        if (in == null) return Optional.empty();
        return convertIoException(() -> Optional.of(in.readAllBytes()));
    }

    private static Optional<String> toString(RequestContext context) {
        try {
            return toByteArray(context).map(bytes -> new String(bytes, UTF_8));
        } catch (RuntimeException e) {
            throw new RestApiException.BadRequest("Failed parse request content as UTF-8: " + Exceptions.toMessageString(e), e);
        }
    }

    private static Optional<JsonNode> toJsonNode(RequestContext context, ObjectMapper jacksonJsonMapper) {
        if (log.isLoggable(Level.FINE)) {
            return toString(context).map(string -> {
                log.fine(() -> "Request content: " + string);
                return convertIoException("Failed to parse JSON", () -> jacksonJsonMapper.readTree(string));
            });
        } else {
            return toInputStream(context)
                    .map(in -> convertIoException("Invalid JSON", () -> jacksonJsonMapper.readTree(in)));
        }
    }

    private static Optional<Slime> toSlime(RequestContext context) {
        try {
            return toString(context).map(string -> {
                log.fine(() -> "Request content: " + string);
                return SlimeUtils.jsonToSlimeOrThrow(string);
            });
        } catch (com.yahoo.slime.JsonParseException e) {
            log.log(Level.FINE, e.getMessage(), e);
            throw new RestApiException.BadRequest("Invalid JSON: " + Exceptions.toMessageString(e), e);
        }
    }

    static class BuilderImpl implements RestApi.Builder {
        private final List<Route> routes = new ArrayList<>();
        private final List<ExceptionMapperHolder<?>> exceptionMappers = new ArrayList<>();
        private final List<ResponseMapperHolder<?>> responseMappers = new ArrayList<>();
        private final List<RequestMapperHolder<?>> requestMappers = new ArrayList<>();
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

        @Override public <ENTITY> Builder addRequestMapper(Class<ENTITY> type, RequestMapper<ENTITY> mapper) {
            requestMappers.add(new RequestMapperHolder<>(type, mapper)); return this;
        }

        @Override public <ENTITY> Builder registerJacksonResponseEntity(Class<ENTITY> type) {
            addResponseMapper(type, new JacksonResponseMapper<>()); return this;
        }

        @Override public <ENTITY> Builder registerJacksonRequestEntity(Class<ENTITY> type) {
            addRequestMapper(type, new JacksonRequestMapper<>(type)); return this;
        }

        @Override public Builder disableDefaultExceptionMappers() { this.disableDefaultExceptionMappers = true; return this; }
        @Override public Builder disableDefaultResponseMappers() { this.disableDefaultResponseMappers = true; return this; }

        @Override public RestApi build() { return new RestApiImpl(this); }
    }

    public static class RouteBuilderImpl implements RestApi.RouteBuilder {
        private final String pathPattern;
        private String name;
        private final Map<Method, HandlerHolder<?>> handlerPerMethod = new HashMap<>();
        private final List<RestApi.Filter> filters = new ArrayList<>();
        private HandlerHolder<?> defaultHandler;

        RouteBuilderImpl(String pathPattern) { this.pathPattern = pathPattern; }

        @Override public RestApi.RouteBuilder name(String name) { this.name = name; return this; }
        @Override public RestApi.RouteBuilder get(Handler<?> handler) {
            return addHandler(Method.GET, handler);
        }
        @Override public RestApi.RouteBuilder post(Handler<?> handler) {
            return addHandler(Method.POST, handler);
        }
        @Override public <ENTITY> RouteBuilder post(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler) {
            return addHandler(Method.POST, type, handler);
        }
        @Override public RestApi.RouteBuilder put(Handler<?> handler) {
            return addHandler(Method.PUT, handler);
        }
        @Override public <ENTITY> RouteBuilder put(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler) {
            return addHandler(Method.PUT, type, handler);
        }
        @Override public RestApi.RouteBuilder delete(Handler<?> handler) {
            return addHandler(Method.DELETE, handler);
        }
        @Override public RestApi.RouteBuilder patch(Handler<?> handler) {
            return addHandler(Method.PATCH, handler);
        }
        @Override public <ENTITY> RouteBuilder patch(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler) {
            return addHandler(Method.PATCH, type, handler);
        }
        @Override public RestApi.RouteBuilder defaultHandler(Handler<?> handler) {
            defaultHandler = HandlerHolder.of(handler); return this;
        }
        @Override public <ENTITY> RouteBuilder defaultHandler(Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler) {
            defaultHandler = HandlerHolder.of(type, handler); return this;
        }
        @Override public RestApi.RouteBuilder addFilter(RestApi.Filter filter) { filters.add(filter); return this; }

        private RestApi.RouteBuilder addHandler(Method method, Handler<?> handler) {
            handlerPerMethod.put(method, HandlerHolder.of(handler)); return this;
        }

        private <ENTITY> RestApi.RouteBuilder addHandler(
                Method method, Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler) {
            handlerPerMethod.put(method, HandlerHolder.of(type, handler)); return this;
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
        @Override public ObjectMapper jacksonJsonMapper() { return jacksonJsonMapper; }
        @Override public UriBuilder uriBuilder() {
            URI uri = request.getUri();
            int uriPort = uri.getPort();
            return uriPort != -1
                    ? new UriBuilder(uri.getScheme() + "://" + uri.getHost() + ':' + uriPort)
                    : new UriBuilder(uri.getScheme() + "://" + uri.getHost());
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
            @Override public List<String> getStringList(String name) {
                List<String> result = request.getJDiscRequest().parameters().get(name);
                if (result == null) return List.of();
                return List.copyOf(result);
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
            @Override public InputStream content() { return request.getData(); }
        }

        private class AttributesImpl implements RestApi.RequestContext.Attributes {
            @Override public Optional<Object> get(String name) { return Optional.ofNullable(request.getJDiscRequest().context().get(name)); }
            @Override public void set(String name, Object value) { request.getJDiscRequest().context().put(name, value); }
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

        HttpResponse toResponse(RestApi.RequestContext context, RuntimeException e) { return mapper.toResponse(context, type.cast(e)); }
    }

    private static class ResponseMapperHolder<ENTITY> {
        final Class<ENTITY> type;
        final RestApi.ResponseMapper<ENTITY> mapper;

        ResponseMapperHolder(Class<ENTITY> type, RestApi.ResponseMapper<ENTITY> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        HttpResponse toHttpResponse(RestApi.RequestContext context, Object entity) { return mapper.toHttpResponse(context, type.cast(entity)); }
    }

    private static class HandlerHolder<REQUEST_ENTITY> {
        final Class<REQUEST_ENTITY> type;
        final HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler;

        HandlerHolder(Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler) {
            this.type = type;
            this.handler = handler;
        }

        static <RESPONSE_ENTITY, REQUEST_ENTITY> HandlerHolder<REQUEST_ENTITY> of(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, RESPONSE_ENTITY> handler) {
            return new HandlerHolder<>(type, handler);
        }

        static <RESPONSE_ENTITY> HandlerHolder<Void> of(Handler<RESPONSE_ENTITY> handler) {
            return new HandlerHolder<>(
                    Void.class,
                    (HandlerWithRequestEntity<Void, RESPONSE_ENTITY>) (context, nullEntity) -> handler.handleRequest(context));
        }

        Object executeHandler(RestApi.RequestContext context, Object entity) { return handler.handleRequest(context, type.cast(entity)); }
    }

    private static class RequestMapperHolder<ENTITY> {
        final Class<ENTITY> type;
        final RestApi.RequestMapper<ENTITY> mapper;

        RequestMapperHolder(Class<ENTITY> type, RequestMapper<ENTITY> mapper) {
            this.type = type;
            this.mapper = mapper;
        }
    }

    static class Route {
        private final String pathPattern;
        private final String name;
        private final Map<Method, HandlerHolder<?>> handlerPerMethod;
        private final HandlerHolder<?> defaultHandler;
        private final List<Filter> filters;

        private Route(RestApi.RouteBuilder builder) {
            RouteBuilderImpl builderImpl = (RouteBuilderImpl)builder;
            this.pathPattern = builderImpl.pathPattern;
            this.name = builderImpl.name;
            this.handlerPerMethod = Map.copyOf(builderImpl.handlerPerMethod);
            this.defaultHandler = builderImpl.defaultHandler != null ? builderImpl.defaultHandler : createDefaultMethodHandler();
            this.filters = List.copyOf(builderImpl.filters);
        }

        private HandlerHolder<?> createDefaultMethodHandler() {
            return HandlerHolder.of(context -> { throw new RestApiException.MethodNotAllowed(context.request()); });
        }
    }

    private static class JacksonRequestMapper<ENTITY> implements RequestMapper<ENTITY> {
        private final Class<ENTITY> type;

        JacksonRequestMapper(Class<ENTITY> type) { this.type = type; }

        @Override
        public Optional<ENTITY> toRequestEntity(RequestContext context) throws RestApiException {
            if (log.isLoggable(Level.FINE)) {
                return RestApiImpl.toString(context).map(string -> {
                    log.fine(() -> "Request content: " + string);
                    return convertIoException("Failed to parse JSON", () -> context.jacksonJsonMapper().readValue(string, type));
                });
            } else {
                return RestApiImpl.toInputStream(context)
                        .map(in -> convertIoException("Invalid JSON", () -> context.jacksonJsonMapper().readValue(in, type)));
            }
        }
    }

    private static class JacksonResponseMapper<ENTITY> implements ResponseMapper<ENTITY> {
        @Override
        public HttpResponse toHttpResponse(RequestContext context, ENTITY responseEntity) throws RestApiException {
            return new JacksonJsonResponse<>(200, responseEntity, context.jacksonJsonMapper(), true);
        }
    }

    @FunctionalInterface private interface SupplierThrowingIoException<T> { T get() throws IOException; }
    private static <T> T convertIoException(String messagePrefix, SupplierThrowingIoException<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            log.log(Level.FINE, e.getMessage(), e);
            throw new RestApiException.InternalServerError(messagePrefix + ": " + Exceptions.toMessageString(e), e);
        }
    }

    private static <T> T convertIoException(SupplierThrowingIoException<T> supplier) {
        return convertIoException("Failed to read request content", supplier);
    }
}
