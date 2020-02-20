// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.usability;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.container.Container;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.NavigableRequestHandler;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class BindingsOverviewHandler extends AbstractRequestHandler {

    private final JdiscBindingsConfig bindingsConfig;

    @Inject
    public BindingsOverviewHandler(JdiscBindingsConfig bindingsConfig) {
        this.bindingsConfig = bindingsConfig;
    }

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        JSONObject json;
        int statusToReturn;

        if (request instanceof HttpRequest && ((HttpRequest) request).getMethod() != Method.GET) {
            json = errorMessageInJson();
            statusToReturn = com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
        } else {
            json = new StatusResponse(bindingsConfig, request.getUri()).render();
            statusToReturn = com.yahoo.jdisc.Response.Status.OK;
        }

        FastContentWriter writer = new FastContentWriter(new ResponseDispatch() {
            @Override
            protected com.yahoo.jdisc.Response newResponse() {
                com.yahoo.jdisc.Response response = new com.yahoo.jdisc.Response(statusToReturn);
                response.headers().add("Content-Type", Arrays.asList(new String[]{"application/json"}));
                return response;
            }
        }.connect(handler));

        try {
            writer.write(json.toString());
        } finally {
            writer.close();
        }

        return new IgnoredContent();
    }

    private JSONObject errorMessageInJson() {
        JSONObject error = new JSONObject();
        try {
            error.put("error", "This API, "
                    + this.getClass().getSimpleName()
                    + ", only supports HTTP GET."
                    + " You are probably looking for another API/path.");
        } catch (org.json.JSONException e) {
            // just ignore it
        }
        return error;
    }

    static JSONArray renderRequestHandlers(JdiscBindingsConfig bindingsConfig,
                                           Map<ComponentId, ? extends RequestHandler> handlersById) {
        JSONArray ret = new JSONArray();

        for (Map.Entry<ComponentId, ? extends RequestHandler> handlerEntry : handlersById.entrySet()) {
            String id = handlerEntry.getKey().stringValue();
            RequestHandler handler = handlerEntry.getValue();

            JSONObject handlerJson = renderComponent(handler, handlerEntry.getKey());
            addBindings(bindingsConfig, id, handlerJson);
            ret.put(handlerJson);
        }
        return ret;
    }

    static JSONArray renderLinks(URI requestUri,
                                 JdiscBindingsConfig bindingsConfig,
                                 Map<ComponentId, ? extends RequestHandler> handlersById)
    {
        var entryPointSet = new LinkedHashSet<URI>();

        for (var handlerEntry : handlersById.entrySet()) {
            var requestHandler = handlerEntry.getValue();

            if (! (requestHandler instanceof NavigableRequestHandler)) {
                continue;
            }

            var navigableHandler = (NavigableRequestHandler) requestHandler;
            var componentId = handlerEntry.getKey();
            var bindings = bindingsConfig.handlers(componentId.stringValue());

            // for each binding on the handler, try to construct a URL based on the binding that points to the
            // handler entrypoint.  then we try to match the generated URL against the binding pattern to make
            // sure we never created something that would not match.  if success, we add the URL to the list.
            for (var binding : bindings.serverBindings()) {
                var uriPattern = new UriPattern(binding);
                var bindingPath = bindingPath(binding);

                if (bindingPath == null) {
                    continue;
                }

                for (var entryPoint : navigableHandler.entryPoints()) {
                    var matchingUri = new com.yahoo.restapi.Uri(requestUri)
                            .append(bindingPath)
                            .append(entryPoint.path().getPath())
                            .toURI()
                            .normalize();
                    var match = uriPattern.match(matchingUri);
                    if (match != null)
                        entryPointSet.add(matchingUri);
                }
            }
        }

        return entryPointSet.stream()
                .map(URI::toString)
                .map(uri -> {
                    var obj = new JSONObject();
                    putJson(obj, "url", uri);
                    return obj;
                })
                .collect(Collectors.collectingAndThen(Collectors.toList(), JSONArray::new));
    }

    private static final Pattern bindingPathPattern = Pattern.compile("^.*://[^/]*(/.*)$");

    private static String bindingPath(String binding) {
        var match = bindingPathPattern.matcher(binding);

        if (match.matches()) {
            // binding paths will often end width wildcards.  trim it away so we can use the path binding glob in a
            // URI construction context.
            var pathMatch = match.group(1);
            if (pathMatch.endsWith("*"))
                return pathMatch.substring(0, pathMatch.length() - 1);
            if (pathMatch.endsWith("*/"))
                return pathMatch.substring(0, pathMatch.length() - 2);
            return pathMatch;
        }

        return null;
    }

    private static void addBindings(JdiscBindingsConfig bindingsConfig, String id, JSONObject handlerJson) {
        List<String> serverBindings = new ArrayList<>();

        JdiscBindingsConfig.Handlers handlerConfig = bindingsConfig.handlers(id);
        if (handlerConfig != null) {
            serverBindings = handlerConfig.serverBindings();
        }
        putJson(handlerJson, "serverBindings", renderBindings(serverBindings));
    }

    private static JSONArray renderBindings(List<String> bindings) {
        JSONArray ret = new JSONArray();

        for (String binding : bindings)
            ret.put(binding);

        return ret;
    }

    private static JSONObject renderComponent(Object component, ComponentId id) {
        JSONObject jc = new JSONObject();
        putJson(jc, "id", id.stringValue());
        addBundleInfo(jc, component);
        return jc;
    }

    private static void addBundleInfo(JSONObject jsonObject, Object component) {
        BundleInfo bundleInfo = bundleInfo(component);
        putJson(jsonObject, "class", bundleInfo.className);
        putJson(jsonObject, "bundle", bundleInfo.bundleName);

    }

    private static BundleInfo bundleInfo(Object component) {
        try {
            Bundle bundle = FrameworkUtil.getBundle(component.getClass());

            String bundleName = bundle != null ?
                    bundle.getSymbolicName() + ":" + bundle.getVersion() :
                    "From classpath";
            return new BundleInfo(component.getClass().getName(), bundleName);
        } catch (Exception | NoClassDefFoundError e) {
            return new BundleInfo("Unavailable, reconfiguration in progress.", "");
        }
    }

    private static void putJson(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            // The original JSONException lacks key-value info.
            throw new RuntimeException("Trying to add invalid JSON object with key '" + key + "' and value '" + value + "' - " + e.getMessage(), e);
        }
    }

    static final class BundleInfo {
        public final String className;
        public final String bundleName;
        BundleInfo(String className, String bundleName) {
            this.className = className;
            this.bundleName = bundleName;
        }
    }

    static final class StatusResponse {

        private final JdiscBindingsConfig bindingsConfig;
        private final URI requestUri;

        StatusResponse(JdiscBindingsConfig bindingsConfig, URI requestUri) {
            this.bindingsConfig = bindingsConfig;
            this.requestUri = requestUri;
        }

        public JSONObject render() {
            JSONObject root = new JSONObject();

            putJson(root, "links",
                    renderLinks(requestUri, bindingsConfig, Container.get().getRequestHandlerRegistry().allComponentsById()));

            putJson(root, "handlers",
                    renderRequestHandlers(bindingsConfig, Container.get().getRequestHandlerRegistry().allComponentsById()));

            return root;
        }

    }

    private class IgnoredContent implements ContentChannel {
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            handler.completed();
        }

        @Override
        public void close(CompletionHandler handler) {
            handler.completed();
        }
    }
}
