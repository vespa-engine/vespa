// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.chain.ChainRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;

import static com.yahoo.container.jdisc.JdiscBindingsConfig.Handlers;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class ApplicationStatusHandlerTest {

    @Test
    void application_configs_are_rendered()  {
        ApplicationMetadataConfig metaConfig = new ApplicationMetadataConfig(
                new ApplicationMetadataConfig.Builder()
                        .checksum("abc")
                        .name("app")
                        .path("/a/b/c")
                        .timestamp(3000)
                        .user("donald"));

        ApplicationUserdataConfig userConfig = new ApplicationUserdataConfig(
                new ApplicationUserdataConfig.Builder()
                        .version("v1"));

        String json = ApplicationStatusHandler.renderApplicationConfigs(metaConfig, userConfig).toString();
        assertTrue(json.contains("version"));
        assertTrue(json.contains("meta"));
        assertTrue(json.contains("abc"));
        assertTrue(json.contains("app"));
        assertTrue(json.contains("/a/b/c"));
        assertTrue(json.contains("3000"));
        assertTrue(json.contains("donald"));

        assertTrue(json.contains("v1"));
    }

    @Test
    void object_components_are_rendered() {
        HashMap<ComponentId, Object> id2object = new HashMap<>();
        id2object.put(new ComponentId("myComponent"), new Object());

        String json = ApplicationStatusHandler.renderObjectComponents(id2object).toString();
        assertTrue(json.contains("myComponent"));
    }

    @Test
    void request_handlers_are_rendered() {
        final String id = "myHandler";
        final String serverBinding1 = "http://*/serverBinding";
        final String serverBinding2 = "http://*/anotherServerBinding";
        final String clientBinding = "http://*/clientBinding";

        HashMap<ComponentId, RequestHandler> handlersById = new HashMap<>();
        handlersById.put(new ComponentId(id), Mockito.mock(RequestHandler.class));

        JdiscBindingsConfig bindingsConfig = new JdiscBindingsConfig(new JdiscBindingsConfig.Builder()
                .handlers(id, new Handlers.Builder()
                        .serverBindings(serverBinding1)
                        .serverBindings(serverBinding2)
                        .clientBindings(clientBinding))
        );
        String json = ApplicationStatusHandler.renderRequestHandlers(bindingsConfig, handlersById).toString();
        assertTrue(json.contains("\"" + id + "\""));
        assertTrue(json.contains(serverBinding1));
        assertTrue(json.contains(serverBinding2));
        assertTrue(json.contains(clientBinding));
    }

    @Test
    void client_providers_are_rendered() {
        final String id = "myClient";
        final String clientBinding = "http://*/clientBinding";
        final String clientBinding2 = "http://*/anotherClientBinding";
        final String serverBinding = "http://*/serverBinding";

        HashMap<ComponentId, ClientProvider> clientsById = new HashMap<>();
        clientsById.put(new ComponentId(id), Mockito.mock(ClientProvider.class));

        JdiscBindingsConfig bindingsConfig = new JdiscBindingsConfig(new JdiscBindingsConfig.Builder()
                .handlers(id, new Handlers.Builder()
                        .clientBindings(clientBinding)
                        .clientBindings(clientBinding2)
                        .serverBindings(serverBinding))
        );
        String json = ApplicationStatusHandler.renderRequestHandlers(bindingsConfig, clientsById).toString();
        System.out.println(json);
        assertTrue(json.contains("\"" + id + "\""));
        assertTrue(json.contains(clientBinding));
        assertTrue(json.contains(clientBinding2));
        assertTrue(json.contains(serverBinding));
    }

    @Test
    void chains_are_rendered()  {
        ChainRegistry<Processor> chains = new ChainRegistry<>();
        Chain<Processor> chain = new Chain<>("myChain", new VoidProcessor(new ComponentId("voidProcessor")));
        chains.register(new ComponentId("myChain"), chain);

        String json = ApplicationStatusHandler.renderChains(chains).toString();
        assertTrue(json.contains("myChain"));
        assertTrue(json.contains("voidProcessor"));
    }

    private static class VoidProcessor extends Processor {
        private VoidProcessor(ComponentId id) {
            super();
            initId(id);
        }
        @Override
        public Response process(Request request, Execution processorExecution) {
            return null;
        }
    }
}
