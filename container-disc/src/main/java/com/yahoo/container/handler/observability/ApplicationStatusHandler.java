// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.Component;
import com.yahoo.component.ComponentId;
import com.yahoo.component.Vtag;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.Container;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
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
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Handler that outputs meta-info about the deployed Vespa application, and status of components and chains.
 *
 * @author gjoranv
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ApplicationStatusHandler extends AbstractRequestHandler {

    public interface Extension {
        Map<String, ? extends JsonNode> produceExtraFields(ApplicationStatusHandler handler);
    }

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private final JsonNode applicationJson;
    private final JsonNode clientsJson;
    private final JsonNode serversJson;
    private final JsonNode requestFiltersJson;
    private final JsonNode responseFiltersJson;
    private final JdiscBindingsConfig bindingsConfig;
    private final Collection<Extension> extensions;

    @Inject
    public ApplicationStatusHandler(ApplicationMetadataConfig metaConfig,
                                    ApplicationUserdataConfig userConfig,
                                    JdiscBindingsConfig bindingsConfig,
                                    ComponentRegistry<ClientProvider> clientProviderRegistry,
                                    ComponentRegistry<ServerProvider> serverProviderRegistry,
                                    ComponentRegistry<RequestFilterBase> requestFilterRegistry,
                                    ComponentRegistry<ResponseFilterBase> responseFilterRegistry,
                                    ComponentRegistry<Extension> extensions) {
        applicationJson = renderApplicationConfigs(metaConfig, userConfig);
        clientsJson = renderRequestHandlers(bindingsConfig, clientProviderRegistry.allComponentsById());
        serversJson = renderObjectComponents(serverProviderRegistry.allComponentsById());
        requestFiltersJson = renderObjectComponents(requestFilterRegistry.allComponentsById());
        responseFiltersJson = renderObjectComponents(responseFilterRegistry.allComponentsById());

        this.bindingsConfig = bindingsConfig;
        this.extensions = extensions.allComponents();
    }

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        FastContentWriter writer = new FastContentWriter(new ResponseDispatch() {
            @Override
            protected com.yahoo.jdisc.Response newResponse() {
                com.yahoo.jdisc.Response response = new com.yahoo.jdisc.Response(com.yahoo.jdisc.Response.Status.OK);
                response.headers().add("Content-Type", List.of("application/json"));
                return response;
            }
        }.connect(handler));

        try {
            writer.write(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(render()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid JSON: " + e.getMessage(), e);
        }
        writer.close();

        return new IgnoredContent();
    }

    public ObjectMapper jsonMapper() { return jsonMapper; }

    public Collection<RequestHandler> requestHandlers() { return container().getRequestHandlerRegistry().allComponents(); }

    private Map<ComponentId, RequestHandler> requestHandlersById() { return container().getRequestHandlerRegistry().allComponentsById(); }

    private List<? extends Component> components() { return container().getComponentRegistry().allComponents(); }

    private static Container container() { return Container.get();  }

    static JsonNode renderApplicationConfigs(ApplicationMetadataConfig metaConfig,
                                      ApplicationUserdataConfig userConfig) {
        ObjectNode vespa = jsonMapper.createObjectNode();
        vespa.put("version", Vtag.currentVersion.toString());

        ObjectNode meta = jsonMapper.createObjectNode();
        meta.put("name", metaConfig.name());
        meta.put("user", metaConfig.user());
        meta.put("path", metaConfig.path());
        meta.put("generation", metaConfig.generation());
        meta.put("timestamp", metaConfig.timestamp());
        meta.put("date", new Date(metaConfig.timestamp()).toString());
        meta.put("checksum", metaConfig.checksum());

        ObjectNode user = jsonMapper.createObjectNode();
        user.put("version", userConfig.version());

        ObjectNode application = jsonMapper.createObjectNode();
        application.set("vespa", vespa);
        application.set("meta", meta);
        application.set("user", user);
        return application;
    }

    static JsonNode renderObjectComponents(Map<ComponentId, ?> componentsById) {
        ArrayNode ret = jsonMapper.createArrayNode();

        for (Map.Entry<ComponentId, ?> componentEntry : componentsById.entrySet()) {
            JsonNode jc = renderComponent(componentEntry.getValue(), componentEntry.getKey());
            ret.add(jc);
        }
        return ret;
    }

    static JsonNode renderRequestHandlers(JdiscBindingsConfig bindingsConfig,
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
        List<String> clientBindings = new ArrayList<>();

        JdiscBindingsConfig.Handlers handlerConfig = bindingsConfig.handlers(id);
        if (handlerConfig != null) {
            serverBindings = handlerConfig.serverBindings();
            clientBindings = handlerConfig.clientBindings();
        }
        handlerJson.set("serverBindings", renderBindings(serverBindings));
        handlerJson.set("clientBindings", renderBindings(clientBindings));
    }

    private static JsonNode renderBindings(List<String> bindings) {
        ArrayNode ret = jsonMapper.createArrayNode();

        for (String binding : bindings)
            ret.add(binding);

        return ret;
    }

    private static JsonNode renderAbstractComponents(List<? extends Component> components) {
        ArrayNode ret = jsonMapper.createArrayNode();

        for (Component c : components) {
            JsonNode jc = renderComponent(c, c.getId());
            ret.add(jc);
        }
        return ret;
    }

    public static ObjectNode renderComponent(Object component, ComponentId id) {
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

            String bundleName = bundle != null ?
                    bundle.getSymbolicName() + ":" + bundle.getVersion() :
                    "From classpath";
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

    JsonNode render() {
        ObjectNode root = jsonMapper.createObjectNode();

        root.set("application", applicationJson);
        root.set("abstractComponents",
                renderAbstractComponents(components()));

        root.set("handlers", renderRequestHandlers(bindingsConfig, requestHandlersById()));
        root.set("clients", clientsJson);
        root.set("servers", serversJson);
        root.set("httpRequestFilters", requestFiltersJson);
        root.set("httpResponseFilters", responseFiltersJson);

        root.set("processingChains", renderProcessingChains());
        for (Extension extension : extensions) {
            extension.produceExtraFields(this).forEach((field, json) -> {
                if (root.has(field)) throw new IllegalArgumentException("Field '" + field + "' already defined");
                root.set(field, json);
            });

        }
        return root;
    }

    private JsonNode renderProcessingChains() {
        JsonNode ret = jsonMapper.createObjectNode();
        for (RequestHandler h : requestHandlers()) {
            if (h instanceof ProcessingHandler) {
                ChainRegistry<Processor> registry = ((ProcessingHandler) h).getChainRegistry();
                return renderChains(registry);
            }
        }
        return ret;
    }

    // Note the generic param here! The key to make this work is '? extends Chain', but why?
    public static JsonNode renderChains(ComponentRegistry<? extends Chain<?>> chains) {
        ObjectNode ret = jsonMapper.createObjectNode();
        for (Chain<?> chain : chains.allComponents()) {
            ret.set(chain.getId().stringValue(), renderAbstractComponents(chain.components()));
        }
        return ret;
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
