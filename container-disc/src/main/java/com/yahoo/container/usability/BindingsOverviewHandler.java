// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.usability;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.Container;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
            json = new StatusResponse(bindingsConfig).render();
            statusToReturn = com.yahoo.jdisc.Response.Status.OK;
        }

        FastContentWriter writer = new FastContentWriter(new ResponseDispatch() {
            @Override
            protected com.yahoo.jdisc.Response newResponse() {
                com.yahoo.jdisc.Response response = new com.yahoo.jdisc.Response(statusToReturn);
                response.headers().add("Content-Type", List.of("application/json"));
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

    private static void addBindings(JdiscBindingsConfig bindingsConfig, String id, JSONObject handlerJson) {
        List<String> serverBindings = new ArrayList<>();

        JdiscBindingsConfig.Handlers handlerConfig = bindingsConfig.handlers(id);
        if (handlerConfig != null) {
            serverBindings = handlerConfig.serverBindings();
        }
        putJson(handlerJson, "serverBindings", renderBindings(serverBindings));
    }

    private static JSONArray renderBindings(List<String> bindings) {
        JSONArray array = new JSONArray();
        for (String binding : bindings)
            array.put(binding);
        return array;
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

            String bundleName = bundle != null
                                ? bundle.getSymbolicName() + ":" + bundle.getVersion()
                                : "From classpath";
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

        StatusResponse(JdiscBindingsConfig bindingsConfig) {
            this.bindingsConfig = bindingsConfig;
        }

        public JSONObject render() {
            JSONObject root = new JSONObject();

            putJson(root, "handlers",
                    renderRequestHandlers(bindingsConfig, Container.get().getRequestHandlerRegistry().allComponentsById()));

            return root;
        }

    }

    private static class IgnoredContent implements ContentChannel {

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
