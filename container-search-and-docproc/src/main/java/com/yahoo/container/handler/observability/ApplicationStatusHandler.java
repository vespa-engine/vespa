// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.Vtag;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.Container;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.docproc.Call;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.RequestFilterBase;
import com.yahoo.jdisc.http.filter.ResponseFilterBase;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.processing.Processor;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.handler.ProcessingHandler;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.SearchChainRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handler that outputs meta-info about the deployed Vespa application, and status of components and chains.
 *
 * @author gjoranv
 * @author Einar M R Rosenvinge
 */
public class ApplicationStatusHandler extends AbstractRequestHandler {

    private final JSONObject applicationJson;
    private final JSONArray clientsJson;
    private final JSONArray serversJson;
    private final JSONArray requestFiltersJson;
    private final JSONArray responseFiltersJson;
    private final JdiscBindingsConfig bindingsConfig;

    @Inject
    public ApplicationStatusHandler(ApplicationMetadataConfig metaConfig,
                                    ApplicationUserdataConfig userConfig,
                                    JdiscBindingsConfig bindingsConfig,
                                    ComponentRegistry<ClientProvider> clientProviderRegistry,
                                    ComponentRegistry<ServerProvider> serverProviderRegistry,
                                    ComponentRegistry<RequestFilterBase> requestFilterRegistry,
                                    ComponentRegistry<ResponseFilterBase> responseFilterRegistry) {

        applicationJson = renderApplicationConfigs(metaConfig, userConfig);
        clientsJson = renderRequestHandlers(bindingsConfig, clientProviderRegistry.allComponentsById());
        serversJson = renderObjectComponents(serverProviderRegistry.allComponentsById());
        requestFiltersJson = renderObjectComponents(requestFilterRegistry.allComponentsById());
        responseFiltersJson = renderObjectComponents(responseFilterRegistry.allComponentsById());

        this.bindingsConfig = bindingsConfig;
    }

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        JSONObject json = new StatusResponse(applicationJson, clientsJson, serversJson,
                                             requestFiltersJson, responseFiltersJson, bindingsConfig)
                .render();

        FastContentWriter writer = new FastContentWriter(new ResponseDispatch() {
            @Override
            protected com.yahoo.jdisc.Response newResponse() {
                com.yahoo.jdisc.Response response = new com.yahoo.jdisc.Response(com.yahoo.jdisc.Response.Status.OK);
                response.headers().add("Content-Type", List.of("application/json"));
                return response;
            }
        }.connect(handler));

        writer.write(json.toString());
        writer.close();

        return new IgnoredContent();
    }

    static JSONObject renderApplicationConfigs(ApplicationMetadataConfig metaConfig,
                                               ApplicationUserdataConfig userConfig) {
        JSONObject vespa = new JSONObject();
        putJson(vespa, "version", Vtag.currentVersion);

        JSONObject meta = new JSONObject();
        putJson(meta, "name", metaConfig.name());
        putJson(meta, "user", metaConfig.user());
        putJson(meta, "path", metaConfig.path());
        putJson(meta, "generation", metaConfig.generation());
        putJson(meta, "timestamp", metaConfig.timestamp());
        putJson(meta, "date", new Date(metaConfig.timestamp()).toString());
        putJson(meta, "checksum", metaConfig.checksum());

        JSONObject user = new JSONObject();
        putJson(user, "version", userConfig.version());

        JSONObject application = new JSONObject();
        putJson(application, "vespa", vespa);
        putJson(application, "meta", meta);
        putJson(application, "user", user);
        return application;
    }

    static JSONArray renderObjectComponents(Map<ComponentId, ?> componentsById) {
        JSONArray ret = new JSONArray();

        for (Map.Entry<ComponentId, ?> componentEntry : componentsById.entrySet()) {
            JSONObject jc = renderComponent(componentEntry.getValue(), componentEntry.getKey());
            ret.put(jc);
        }
        return ret;
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
        List<String> clientBindings = new ArrayList<>();

        JdiscBindingsConfig.Handlers handlerConfig = bindingsConfig.handlers(id);
        if (handlerConfig != null) {
            serverBindings = handlerConfig.serverBindings();
            clientBindings = handlerConfig.clientBindings();
        }
        putJson(handlerJson, "serverBindings", renderBindings(serverBindings));
        putJson(handlerJson, "clientBindings", renderBindings(clientBindings));
    }

    private static JSONArray renderBindings(List<String> bindings) {
        JSONArray ret = new JSONArray();

        for (String binding : bindings)
            ret.put(binding);

        return ret;
    }

    private static JSONArray renderAbstractComponents(List<? extends AbstractComponent> components) {
        JSONArray ret = new JSONArray();

        for (AbstractComponent c : components) {
            JSONObject jc = renderComponent(c, c.getId());
            ret.put(jc);
        }
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
        private final JSONObject applicationJson;
        private final JSONArray clientsJson;
        private final JSONArray serversJson;
        private final JSONArray requestFiltersJson;
        private final JSONArray responseFiltersJson;
        private final JdiscBindingsConfig bindingsConfig;

        StatusResponse(JSONObject applicationJson,
                       JSONArray clientsJson,
                       JSONArray serversJson,
                       JSONArray requestFiltersJson,
                       JSONArray responseFiltersJson,
                       JdiscBindingsConfig bindingsConfig) {
            this.applicationJson = applicationJson;
            this.clientsJson = clientsJson;
            this.serversJson = serversJson;
            this.requestFiltersJson = requestFiltersJson;
            this.responseFiltersJson = responseFiltersJson;
            this.bindingsConfig = bindingsConfig;
        }

        public JSONObject render() {
            JSONObject root = new JSONObject();

            putJson(root, "application", applicationJson);
            putJson(root, "abstractComponents",
                    renderAbstractComponents(Container.get().getComponentRegistry().allComponents()));

            putJson(root, "handlers",
                    renderRequestHandlers(bindingsConfig, Container.get().getRequestHandlerRegistry().allComponentsById()));
            putJson(root, "clients", clientsJson);
            putJson(root, "servers", serversJson);
            putJson(root, "httpRequestFilters", requestFiltersJson);
            putJson(root, "httpResponseFilters", responseFiltersJson);

            putJson(root, "searchChains", renderSearchChains(Container.get()));
            putJson(root, "docprocChains", renderDocprocChains(Container.get()));
            putJson(root, "processingChains", renderProcessingChains(Container.get()));

            return root;
        }

        private static JSONObject renderSearchChains(Container container) {
            for (RequestHandler h : container.getRequestHandlerRegistry().allComponents()) {
                if (h instanceof SearchHandler) {
                    SearchChainRegistry scReg = ((SearchHandler) h).getSearchChainRegistry();
                    return renderChains(scReg);
                }
            }
            return new JSONObject();
        }

        private static JSONObject renderDocprocChains(Container container) {
            JSONObject ret = new JSONObject();
            for (RequestHandler h : container.getRequestHandlerRegistry().allComponents()) {
                if (h instanceof DocumentProcessingHandler) {
                    ComponentRegistry<DocprocService> registry = ((DocumentProcessingHandler) h).getDocprocServiceRegistry();
                    for (DocprocService service : registry.allComponents()) {
                        putJson(ret, service.getId().stringValue(), renderCalls(service.getCallStack().iterator()));
                    }
                }
            }
            return ret;
        }

        private static JSONObject renderProcessingChains(Container container) {
            JSONObject ret = new JSONObject();
            for (RequestHandler h : container.getRequestHandlerRegistry().allComponents()) {
                if (h instanceof ProcessingHandler) {
                    ChainRegistry<Processor> registry = ((ProcessingHandler) h).getChainRegistry();
                    return renderChains(registry);
                }
            }
            return ret;
        }

        // Note the generic param here! The key to make this work is '? extends Chain', but why?
        static JSONObject renderChains(ComponentRegistry<? extends Chain<?>> chains) {
            JSONObject ret = new JSONObject();
            for (Chain<?> chain : chains.allComponents()) {
                putJson(ret, chain.getId().stringValue(), renderAbstractComponents(chain.components()));
            }
            return ret;
        }

        private static JSONArray renderCalls(Iterator<Call> components) {
            JSONArray ret = new JSONArray();
            while (components.hasNext()) {
                Call c = components.next();
                JSONObject jc = renderComponent(c.getDocumentProcessor(), c.getDocumentProcessor().getId());
                ret.put(jc);
            }
            return ret;
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
