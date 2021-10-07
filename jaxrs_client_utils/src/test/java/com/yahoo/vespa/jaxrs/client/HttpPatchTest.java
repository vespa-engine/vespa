// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.annotation.PATCH;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author bakksjo
 */
public class HttpPatchTest extends JerseyTest {
    private final TestResource testResourceSingleton = new TestResource();

    @Override
    protected Application configure() {
        return new Application() {
            @Override
            public Set<Class<?>> getClasses() {
                return Collections.emptySet();
            }

            @Override
            public Set<Object> getSingletons() {
                return new HashSet<>(Arrays.asList(testResourceSingleton));
            }
        };
    }

    @Override
    protected void configureClient(final ClientConfig config) {
        config.getConfiguration().property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true);
    }

    private static final String REQUEST_BODY = "Hello there";

    @Test
    public void clientPatchRequest() throws Exception {
        final Response response = target(TestResourceApi.PATH)
                .request()
                .method("PATCH", Entity.text(REQUEST_BODY));
        assertEquals(REQUEST_BODY, testResourceSingleton.invocation.get(60, TimeUnit.SECONDS));
        assertEquals(REQUEST_BODY, response.readEntity(String.class));
    }

    @Test
    public void clientPatchRequestUsingProxyClass() throws Exception {
        final URI targetUri = target(TestResourceApi.PATH).getUri();
        final HostName apiHost = new HostName(targetUri.getHost());
        final int apiPort = targetUri.getPort();
        final String apiPath = targetUri.getPath();

        final JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        final JaxRsStrategyFactory factory = new JaxRsStrategyFactory(
                Collections.singleton(apiHost), apiPort, jaxRsClientFactory, "http");
        final JaxRsStrategy<TestResourceApi> client = factory.apiNoRetries(TestResourceApi.class, apiPath);

        final String responseBody;
        responseBody = client.apply(api -> api.doPatch(REQUEST_BODY));

        assertEquals(REQUEST_BODY, testResourceSingleton.invocation.get(60, TimeUnit.SECONDS));
        assertEquals(REQUEST_BODY, responseBody);
    }

    public interface TestResourceApi {
        String PATH = "test";

        @GET
        String getHello();

        @PATCH
        String doPatch(final String body);
    }

    @Path(TestResourceApi.PATH)
    public static class TestResource implements TestResourceApi {
        public final CompletableFuture<String> invocation = new CompletableFuture<>();

        @GET
        public String getHello() {
            return "Hello World!";
        }

        @PATCH
        public String doPatch(final String body) {
            invocation.complete(body);
            return body;
        }
    }
}
