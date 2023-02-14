// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.server.jetty.RequestUtils;
import com.yahoo.restapi.RestApiMappers.ExceptionMapperHolder;
import com.yahoo.restapi.RestApiMappers.RequestMapperHolder;
import com.yahoo.restapi.RestApiMappers.ResponseMapperHolder;
import com.yahoo.security.tls.Capability;
import com.yahoo.security.tls.CapabilitySet;
import com.yahoo.security.tls.ConnectionAuthContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import javax.net.ssl.SSLSession;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
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
    private final List<RequestMapperHolder<?>> requestMappers;
    private final List<Filter> filters;
    private final ObjectMapper jacksonJsonMapper;
    private final boolean disableDefaultAclMapping;
    private final CapabilitySet requiredCapabilities;

    private RestApiImpl(RestApi.Builder builder) {
        BuilderImpl builderImpl = (BuilderImpl) builder;
        ObjectMapper jacksonJsonMapper = builderImpl.jacksonJsonMapper != null ? builderImpl.jacksonJsonMapper : JacksonJsonMapper.instance.copy();
        this.defaultRoute = builderImpl.defaultRoute != null ? builderImpl.defaultRoute : createDefaultRoute();
        this.routes = List.copyOf(builderImpl.routes);
        this.exceptionMappers = combineWithDefaultExceptionMappers(
                builderImpl.exceptionMappers, Boolean.TRUE.equals(builderImpl.disableDefaultExceptionMappers));
        this.responseMappers = combineWithDefaultResponseMappers(
                builderImpl.responseMappers, Boolean.TRUE.equals(builderImpl.disableDefaultResponseMappers));
        this.requestMappers = combineWithDefaultRequestMappers(builderImpl.requestMappers);
        this.filters = List.copyOf(builderImpl.filters);
        this.jacksonJsonMapper = jacksonJsonMapper;
        this.disableDefaultAclMapping = Boolean.TRUE.equals(builderImpl.disableDefaultAclMapping);
        this.requiredCapabilities = builderImpl.requiredCapabilities;
    }

    @Override
    public HttpResponse handleRequest(HttpRequest request) {
        Path pathMatcher = new Path(request.getUri());
        Route resolvedRoute = resolveRoute(pathMatcher);
        AclMapping.Action aclAction = getAclMapping(request.getMethod(), request.getUri());
        RequestContextImpl requestContext = new RequestContextImpl(request, pathMatcher, aclAction, jacksonJsonMapper);
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

    @Override
    public RequestHandlerSpec requestHandlerSpec() {
        return RequestHandlerSpec.builder()
                .withAclMapping(requestView -> getAclMapping(requestView.method(), requestView.uri()))
                .build();
    }

    private static final CapabilitySet DEFAULT_REQUIRED_CAPABILITIES = Capability.RESTAPI_UNCLASSIFIED.toCapabilitySet();

    @Override
    public CapabilitySet requiredCapabilities(RequestView req) {
        Path pathMatcher = new Path(req.uri());
        Route route = resolveRoute(pathMatcher);
        HandlerHolder<?> handler = resolveHandler(req.method(), route);
        return Optional.ofNullable(handler.config.requiredCapabilities)
                .or(() -> Optional.ofNullable(route.requiredCapabilities))
                .or(() -> Optional.ofNullable(requiredCapabilities))
                .orElse(DEFAULT_REQUIRED_CAPABILITIES);
    }

    private AclMapping.Action getAclMapping(Method method, URI uri) {
        Path pathMatcher = new Path(uri);
        Route route = resolveRoute(pathMatcher);
        HandlerHolder<?> handler = resolveHandler(method, route);
        AclMapping.Action aclAction = handler.config.aclAction;
        if (aclAction != null) return aclAction;
        if (!disableDefaultAclMapping) {
            // Fallback to default request handler spec which is used by the default implementation of
            // HttpRequestHandler.requestHandlerSpec().
            return RequestHandlerSpec.DEFAULT_INSTANCE.aclMapping().get(
                    new RequestView() {
                        @Override public Method method() { return method; }
                        @Override public URI uri() { return uri; }
                    });
        }
        throw new IllegalStateException(String.format("No ACL mapping configured for '%s' to '%s'", method, route.name));
    }

    private HttpResponse dispatchToRoute(Route route, RequestContextImpl context) {
        HandlerHolder<?> resolvedHandler = resolveHandler(context.request.getMethod(), route);
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

    private HandlerHolder<?> resolveHandler(Method method, Route route) {
        HandlerHolder<?> resolvedHandler = route.handlerPerMethod.get(method);
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
            exceptionMappers.addAll(RestApiMappers.DEFAULT_EXCEPTION_MAPPERS);
        }
        // Topologically sort children before superclasses, so most the specific match is found by iterating through mappers in order.
        exceptionMappers.sort((a, b) -> (a.type.isAssignableFrom(b.type) ? 1 : 0) + (b.type.isAssignableFrom(a.type) ? -1 : 0));
        return exceptionMappers;
    }

    private static List<ResponseMapperHolder<?>> combineWithDefaultResponseMappers(
            List<ResponseMapperHolder<?>> configuredResponseMappers, boolean disableDefaultMappers) {
        List<ResponseMapperHolder<?>> responseMappers = new ArrayList<>(configuredResponseMappers);
        if (!disableDefaultMappers) {
            responseMappers.addAll(RestApiMappers.DEFAULT_RESPONSE_MAPPERS);
        }
        return responseMappers;
    }

    private static List<RequestMapperHolder<?>> combineWithDefaultRequestMappers(
            List<RequestMapperHolder<?>> configuredRequestMappers) {
        List<RequestMapperHolder<?>> requestMappers = new ArrayList<>(configuredRequestMappers);
        requestMappers.addAll(RestApiMappers.DEFAULT_REQUEST_MAPPERS);
        return requestMappers;
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
        private Boolean disableDefaultAclMapping;
        private CapabilitySet requiredCapabilities;

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
            addResponseMapper(type, new RestApiMappers.JacksonResponseMapper<>()); return this;
        }

        @Override public <ENTITY> Builder registerJacksonRequestEntity(Class<ENTITY> type) {
            addRequestMapper(type, new RestApiMappers.JacksonRequestMapper<>(type)); return this;
        }

        @Override public Builder disableDefaultExceptionMappers() { this.disableDefaultExceptionMappers = true; return this; }
        @Override public Builder disableDefaultResponseMappers() { this.disableDefaultResponseMappers = true; return this; }
        @Override public Builder disableDefaultAclMapping() { this.disableDefaultAclMapping = true; return this; }

        @Override public Builder requiredCapabilities(Capability... capabilities) {
            return requiredCapabilities(CapabilitySet.of(capabilities));
        }
        @Override public Builder requiredCapabilities(CapabilitySet capabilities) {
            if (requiredCapabilities != null) throw new IllegalStateException("Capabilities already set");
            requiredCapabilities = capabilities;
            return this;
        }

        @Override public RestApi build() { return new RestApiImpl(this); }
    }

    static class RouteBuilderImpl implements RestApi.RouteBuilder {
        private final String pathPattern;
        private String name;
        private final Map<Method, HandlerHolder<?>> handlerPerMethod = new HashMap<>();
        private final List<RestApi.Filter> filters = new ArrayList<>();
        private HandlerHolder<?> defaultHandler;
        private CapabilitySet requiredCapabilities;

        RouteBuilderImpl(String pathPattern) { this.pathPattern = pathPattern; }

        @Override public RestApi.RouteBuilder name(String name) { this.name = name; return this; }

        @Override public RestApi.RouteBuilder requiredCapabilities(Capability... capabilities) {
            return requiredCapabilities(CapabilitySet.of(capabilities));
        }
        @Override public RestApi.RouteBuilder requiredCapabilities(CapabilitySet capabilities) {
            if (requiredCapabilities != null) throw new IllegalStateException("Capabilities already set");
            requiredCapabilities = capabilities;
            return this;
        }

        @Override public RestApi.RouteBuilder addFilter(RestApi.Filter filter) { filters.add(filter); return this; }

        // GET
        @Override public RouteBuilder get(Handler<?> handler) { return get(handler, null); }
        @Override public RouteBuilder get(Handler<?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.GET, handler, config);
        }

        // POST
        @Override public RouteBuilder post(Handler<?> handler) { return post(handler, null); }
        @Override public <REQUEST_ENTITY> RouteBuilder post(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler) {
            return post(type, handler, null);
        }
        @Override public RouteBuilder post(Handler<?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.POST, handler, config);
        }
        @Override public <REQUEST_ENTITY> RouteBuilder post(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.POST, type, handler, config);
        }

        // PUT
        @Override public RouteBuilder put(Handler<?> handler) { return put(handler, null); }
        @Override public <REQUEST_ENTITY> RouteBuilder put(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler) {
            return put(type, handler, null);
        }
        @Override public RouteBuilder put(Handler<?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.PUT, handler, null);
        }
        @Override public <REQUEST_ENTITY> RouteBuilder put(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.PUT, type, handler, config);
        }

        // DELETE
        @Override public RouteBuilder delete(Handler<?> handler) { return delete(handler, null); }
        @Override public RouteBuilder delete(Handler<?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.DELETE, handler, config);
        }

        // PATCH
        @Override public RouteBuilder patch(Handler<?> handler) { return patch(handler, null); }
        @Override public <REQUEST_ENTITY> RouteBuilder patch(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler) {
            return patch(type, handler, null);
        }
        @Override public RouteBuilder patch(Handler<?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.PATCH, handler, config);
        }
        @Override public <REQUEST_ENTITY> RouteBuilder patch(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config) {
            return addHandler(Method.PATCH, type, handler, config);
        }

        // Default
        @Override public RouteBuilder defaultHandler(Handler<?> handler) {
            return defaultHandler(handler, null);
        }
        @Override public RouteBuilder defaultHandler(Handler<?> handler, HandlerConfigBuilder config) {
            defaultHandler = HandlerHolder.of(handler, build(config)); return this;
        }
        @Override public <REQUEST_ENTITY> RouteBuilder defaultHandler(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler) {
            return defaultHandler(type, handler, null);
        }
        @Override
        public <REQUEST_ENTITY> RouteBuilder defaultHandler(
                Class<REQUEST_ENTITY> type, HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler, HandlerConfigBuilder config) {
            defaultHandler = HandlerHolder.of(type, handler, build(config)); return this;
        }

        private RestApi.RouteBuilder addHandler(Method method, Handler<?> handler, HandlerConfigBuilder config) {
            handlerPerMethod.put(method, HandlerHolder.of(handler, build(config))); return this;
        }

        private <ENTITY> RestApi.RouteBuilder addHandler(
                Method method, Class<ENTITY> type, HandlerWithRequestEntity<ENTITY, ?> handler, HandlerConfigBuilder config) {
            handlerPerMethod.put(method, HandlerHolder.of(type, handler, build(config))); return this;
        }

        private static HandlerConfig build(HandlerConfigBuilder builder) {
            if (builder == null) return HandlerConfig.empty();
            return ((HandlerConfigBuilderImpl)builder).build();
        }

        private Route build() { return new Route(this); }
    }

    static class HandlerConfigBuilderImpl implements HandlerConfigBuilder {
        private AclMapping.Action aclAction;
        private CapabilitySet requiredCapabilities;

        @Override public HandlerConfigBuilder withRequiredCapabilities(Capability... capabilities) {
            return withRequiredCapabilities(CapabilitySet.of(capabilities));
        }
        @Override public HandlerConfigBuilder withRequiredCapabilities(CapabilitySet capabilities) {
            if (requiredCapabilities != null) throw new IllegalStateException("Capabilities already set");
            requiredCapabilities = capabilities;
            return this;
        }
        @Override public HandlerConfigBuilder withReadAclAction() { return withCustomAclAction(AclMapping.Action.READ); }
        @Override public HandlerConfigBuilder withWriteAclAction() { return withCustomAclAction(AclMapping.Action.WRITE); }
        @Override public HandlerConfigBuilder withCustomAclAction(AclMapping.Action action) {
            this.aclAction = action; return this;
        }

        HandlerConfig build() { return new HandlerConfig(this); }
    }

    private static class HandlerConfig {
        final AclMapping.Action aclAction;
        final CapabilitySet requiredCapabilities;

        HandlerConfig(HandlerConfigBuilderImpl builder) {
            this.aclAction = builder.aclAction;
            this.requiredCapabilities = builder.requiredCapabilities;
        }

        static HandlerConfig empty() { return new HandlerConfigBuilderImpl().build(); }
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
        final AclMapping.Action aclAction;

        RequestContextImpl(HttpRequest request, Path pathMatcher, AclMapping.Action aclAction, ObjectMapper jacksonJsonMapper) {
            this.request = request;
            this.pathMatcher = pathMatcher;
            this.jacksonJsonMapper = jacksonJsonMapper;
            this.requestContent = request.getData() != null ? new RequestContentImpl() : null;
            this.aclAction = aclAction;
        }

        @Override public HttpRequest request() { return request; }
        @Override public Method method() { return request.getMethod(); }
        @Override public PathParameters pathParameters() { return pathParameters; }
        @Override public QueryParameters queryParameters() { return queryParameters; }
        @Override public Headers headers() { return headers; }
        @Override public Attributes attributes() { return attributes; }
        @Override public Optional<RequestContent> requestContent() { return Optional.ofNullable(requestContent); }
        @Override public RequestContent requestContentOrThrow() {
            return requestContent().orElseThrow(() -> new RestApiException.BadRequest("Request content missing"));
        }
        @Override public ObjectMapper jacksonJsonMapper() { return jacksonJsonMapper; }
        @Override public HttpURL baseRequestURL() {
            URI uri = request.getUri();
            // Reconstruct the URI used by the client to access the API.
            // This is needed for producing URIs in the response that links to other parts of the Rest API.
            // request.getUri() cannot be used as its port is the local listen port (as it's intended for request routing).
            StringBuilder sb = new StringBuilder(uri.getScheme()).append("://");
            String hostHeader = request.getHeader("X-Forwarded-Host");
            if (hostHeader == null || hostHeader.isBlank()) {
                hostHeader = request.getHeader("Host");
            }
            if (hostHeader != null && ! hostHeader.isBlank()) {
                sb.append(hostHeader);
            } else {
                sb.append(uri.getHost());
                if (uri.getPort() > 0) {
                    sb.append(":").append(uri.getPort());
                }
            }
            return HttpURL.from(URI.create(sb.toString()));
        }
        @Override public AclMapping.Action aclAction() { return aclAction; }
        @Override public Optional<Principal> userPrincipal() {
            return Optional.ofNullable(request.getJDiscRequest().getUserPrincipal());
        }
        @Override public Principal userPrincipalOrThrow() {
            return userPrincipal().orElseThrow(RestApiException.Unauthorized::new);
        }
        @Override public Optional<SSLSession> sslSession() {
            return Optional.ofNullable((SSLSession) request.context().get(RequestUtils.JDISC_REQUEST_SSLSESSION));
        }
        @Override public Optional<ConnectionAuthContext> connectionAuthContext() {
            return sslSession().flatMap(TransportSecurityUtils::getConnectionAuthContext);
        }


        private class PathParametersImpl implements RestApi.RequestContext.PathParameters {
            @Override
            public Optional<String> getString(String name) {
                return Optional.ofNullable(pathMatcher.get(name));
            }
            @Override public String getStringOrThrow(String name) {
                return getString(name)
                        .orElseThrow(() -> new RestApiException.BadRequest("Path parameter '" + name + "' is missing"));
            }
            @Override public HttpURL.Path getFullPath() {
                return pathMatcher.getPath();
            }
            @Override public Optional<HttpURL.Path> getRest() {
                return Optional.ofNullable(pathMatcher.getRest());
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
            @Override public HttpURL.Query getFullQuery() { return Query.empty().add(request.getJDiscRequest().parameters()); }
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

    private static class HandlerHolder<REQUEST_ENTITY> {
        final Class<REQUEST_ENTITY> type;
        final HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler;
        final HandlerConfig config;

        private HandlerHolder(
                Class<REQUEST_ENTITY> type,
                HandlerWithRequestEntity<REQUEST_ENTITY, ?> handler,
                HandlerConfig config) {
            this.type = type;
            this.handler = handler;
            this.config = config;
        }

        static <RESPONSE_ENTITY, REQUEST_ENTITY> HandlerHolder<REQUEST_ENTITY> of(
                Class<REQUEST_ENTITY> type,
                HandlerWithRequestEntity<REQUEST_ENTITY, RESPONSE_ENTITY> handler,
                HandlerConfig config) {
            return new HandlerHolder<>(type, handler, config);
        }

        static <RESPONSE_ENTITY> HandlerHolder<Void> of(Handler<RESPONSE_ENTITY> handler, HandlerConfig config) {
            return new HandlerHolder<>(
                    Void.class,
                    (HandlerWithRequestEntity<Void, RESPONSE_ENTITY>) (context, nullEntity) -> handler.handleRequest(context),
                    config);
        }

        Object executeHandler(RestApi.RequestContext context, Object entity) { return handler.handleRequest(context, type.cast(entity)); }
    }

    static class Route {
        private final String pathPattern;
        private final String name;
        private final Map<Method, HandlerHolder<?>> handlerPerMethod;
        private final HandlerHolder<?> defaultHandler;
        private final List<Filter> filters;
        private final CapabilitySet requiredCapabilities;

        private Route(RestApi.RouteBuilder builder) {
            RouteBuilderImpl builderImpl = (RouteBuilderImpl)builder;
            this.pathPattern = builderImpl.pathPattern;
            this.name = builderImpl.name;
            this.handlerPerMethod = Map.copyOf(builderImpl.handlerPerMethod);
            this.defaultHandler = builderImpl.defaultHandler != null ? builderImpl.defaultHandler : createDefaultMethodHandler();
            this.filters = List.copyOf(builderImpl.filters);
            this.requiredCapabilities = builderImpl.requiredCapabilities;
        }

        private HandlerHolder<?> createDefaultMethodHandler() {
            return HandlerHolder.of(
                    context -> { throw new RestApiException.MethodNotAllowed(context.request()); },
                    HandlerConfig.empty());
        }
    }

}
