// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.chain.ChainRegistry;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.concurrent.Executors;

import static com.yahoo.container.jdisc.JdiscBindingsConfig.Handlers;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * @author gjoranv
 * @since 5.1.10
 */
public class ApplicationStatusHandlerTest {

    @Test
    public void application_configs_are_rendered() throws Exception {
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
        assertThat(json, containsString("version"));
        assertThat(json, containsString("meta"));
        assertThat(json, containsString("abc"));
        assertThat(json, containsString("app"));
        assertThat(json, containsString("/a/b/c"));
        assertThat(json, containsString("3000"));
        assertThat(json, containsString("donald"));

        assertThat(json, containsString("v1"));
    }

    @Test
    public void object_components_are_rendered() throws Exception {
        HashMap<ComponentId, Object> id2object = new HashMap<>();
        id2object.put(new ComponentId("myComponent"), new Object());

        String json = ApplicationStatusHandler.renderObjectComponents(id2object).toString();
        assertThat(json, containsString("myComponent"));
    }

    @Test
    public void request_handlers_are_rendered() throws Exception {
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
        assertThat(json, containsString("\"" + id + "\""));
        assertThat(json, containsString(serverBinding1));
        assertThat(json, containsString(serverBinding2));
        assertThat(json, containsString(clientBinding));
    }

    @Test
    public void client_providers_are_rendered() throws Exception {
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
        assertThat(json, containsString("\"" + id + "\""));
        assertThat(json, containsString(clientBinding));
        assertThat(json, containsString(clientBinding2));
        assertThat(json, containsString(serverBinding));
    }

    @Test
    public void chains_are_rendered() throws Exception {
        ChainRegistry<Processor> chains = new ChainRegistry<>();
        Chain<Processor> chain = new Chain<Processor>("myChain", new VoidProcessor(new ComponentId("voidProcessor")));
        chains.register(new ComponentId("myChain"), chain);

        String json = ApplicationStatusHandler.StatusResponse.renderChains(chains).toString();
        assertThat(json, containsString("myChain"));
        assertThat(json, containsString("voidProcessor"));
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
