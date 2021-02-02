// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.usability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
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
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 */
public class BindingsOverviewHandler extends AbstractRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final JdiscBindingsConfig bindingsConfig;

    @Inject
    public BindingsOverviewHandler(JdiscBindingsConfig bindingsConfig) {
        this.bindingsConfig = bindingsConfig;
    }

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        JsonNode json;
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
            writer.write(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } finally {
            writer.close();
        }

        return new IgnoredContent();
    }

    private static JsonNode errorMessageInJson() {
        ObjectNode error = jsonMapper.createObjectNode();
        error.put("error", "This API, "
                + BindingsOverviewHandler.class.getSimpleName()
                + ", only supports HTTP GET."
                + " You are probably looking for another API/path.");
        return error;
    }

    static ArrayNode renderRequestHandlers(JdiscBindingsConfig bindingsConfig,
                                           Map<ComponentId, ? extends RequestHandler> handlersById) {
        ArrayNode ret = jsonMapper.createArrayNode();

        for (Map.Entry<ComponentId, ? extends RequestHandler> handlerEntry : handlersById.entrySet()) {
            String id = handlerEntry.getKey().stringValue();
            RequestHandler handler = handlerEntry.getValue();

            ObjectNode handlerJson = renderComponent(handler, handlerEntry.getKey());
            addBindings(bindingsConfig, id, handlerJson);
            ret.add(handlerJson);
        }
        return ret;
    }

    private static void addBindings(JdiscBindingsConfig bindingsConfig, String id, ObjectNode handlerJson) {
        List<String> serverBindings = new ArrayList<>();

        JdiscBindingsConfig.Handlers handlerConfig = bindingsConfig.handlers(id);
        if (handlerConfig != null) {
            serverBindings = handlerConfig.serverBindings();
        }
        handlerJson.set("serverBindings", renderBindings(serverBindings));
    }

    private static JsonNode renderBindings(List<String> bindings) {
        ArrayNode array = jsonMapper.createArrayNode();
        for (String binding : bindings)
            array.add(binding);
        return array;
    }

    private static ObjectNode renderComponent(Object component, ComponentId id) {
        ObjectNode jc = jsonMapper.createObjectNode();
        jc.put("id", id.stringValue());
        addBundleInfo(jc, component);
        return jc;
    }

    private static void addBundleInfo(ObjectNode jsonObject, Object component) {
        BundleInfo bundleInfo = bundleInfo(component);
        jsonObject.put("class", bundleInfo.className);
        jsonObject.put("bundle", bundleInfo.bundleName);
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

        public JsonNode render() {
            ObjectNode root = jsonMapper.createObjectNode();

            root.set("handlers",
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
