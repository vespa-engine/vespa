// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.annotation.PATCH;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.JerseyInvocation;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author bakksjo
 */
public class HttpPatchTest extends JerseyTest {

    private static final String patch = "PATCH";
    private static final String put = "PUT";
    private static final String REQUEST_BODY = "Hello there";

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
    protected void configureClient(ClientConfig config) {
        config.getConfiguration()
              .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
              .property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
    }

    @Test
    public void clientPatchRequest() throws Exception {
        Response response = target(TestResourceApi.PATH)
                .request()
                .method(patch, Entity.text(REQUEST_BODY));
        assertThat(testResourceSingleton.invocations.get(patch).get(60, TimeUnit.SECONDS), is(REQUEST_BODY));
        assertThat(response.readEntity(String.class), is(REQUEST_BODY));
    }

    @Test
    public void suppressEmptyPutBodyWarning() throws Exception {
        JerseyJaxRsClientFactory.disableHttpComplianceWarningLogging();
        Logger.getLogger(JerseyInvocation.class.getName()).addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                assertFalse(record.getMessage().contains("Entity must not be null for http method PUT"));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        assertEquals(200, target(TestResourceApi.PATH).request().method(put).getStatus());
        assertEquals("", testResourceSingleton.invocations.get(put).get());
    }

    @Test
    public void clientPatchRequestUsingProxyClass() throws Exception {
        URI targetUri = target(TestResourceApi.PATH).getUri();
        HostName apiHost = new HostName(targetUri.getHost());
        int apiPort = targetUri.getPort();
        String apiPath = targetUri.getPath();

        JaxRsClientFactory jaxRsClientFactory = new JerseyJaxRsClientFactory();
        JaxRsStrategyFactory factory = new JaxRsStrategyFactory(
                Collections.singleton(apiHost), apiPort, jaxRsClientFactory, "http");
        JaxRsStrategy<TestResourceApi> client = factory.apiNoRetries(TestResourceApi.class, apiPath);

        String responseBody;
        responseBody = client.apply(api -> api.doPatch(REQUEST_BODY));

        assertThat(testResourceSingleton.invocations.get(patch).get(60, TimeUnit.SECONDS), is(REQUEST_BODY));
        assertThat(responseBody, is(REQUEST_BODY));
    }

    public interface TestResourceApi {
        String PATH = "test";

        @GET
        String getHello();

        @PATCH
        String doPatch(String body);

        @PUT
        String doPut();
    }

    @Path(TestResourceApi.PATH)
    public static class TestResource implements TestResourceApi {

        public final Map<String, CompletableFuture<String>> invocations = new HashMap<>();

        public TestResource() {
            invocations.put(patch, new CompletableFuture<>());
            invocations.put(put, new CompletableFuture<>());
        }

        @GET
        public String getHello() {
            return "Hello World!";
        }

        @PATCH
        public String doPatch(String body) {
            invocations.get(patch).complete(body);
            return body;
        }

        @Override
        public String doPut() {
            invocations.get(put).complete("");
            return "";
        }
    }

}

