// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import java.io.IOException;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NoRetryJaxRsStrategyTest {
    private static final String API_PATH = "/foo/bar";

    @Path(API_PATH)
    private interface TestJaxRsApi {
        @GET
        @Path("/foo/bar")
        String doSomething();
    }

    private static final HostName SERVER_HOST = new HostName("host-1");
    private static final int REST_PORT = Defaults.getDefaults().vespaWebServicePort();

    private final JaxRsClientFactory jaxRsClientFactory = mock(JaxRsClientFactory.class);
    private final TestJaxRsApi mockApi = mock(TestJaxRsApi.class);
    private final JaxRsStrategy<TestJaxRsApi> jaxRsStrategy = new NoRetryJaxRsStrategy<>(
            SERVER_HOST, REST_PORT, jaxRsClientFactory, TestJaxRsApi.class, API_PATH, "http");

    @Before
    public void setup() {
        when(jaxRsClientFactory.createClient(eq(TestJaxRsApi.class), any(HostName.class), anyInt(), anyString(), anyString()))
                .thenReturn(mockApi);
    }

    @Test
    public void noRetryIfNoFailure() throws Exception {
        jaxRsStrategy.apply(TestJaxRsApi::doSomething);

        verify(mockApi, times(1)).doSomething();

        verify(jaxRsClientFactory, times(1))
                .createClient(eq(TestJaxRsApi.class), eq(SERVER_HOST), eq(REST_PORT), eq(API_PATH), eq("http"));
    }

    @Test
    public void testNoRetryAfterFailure() throws Exception {
        // Make the first call fail.
        when(mockApi.doSomething())
                .thenThrow(new ProcessingException("Fake timeout induced by test"))
                .thenReturn("a response");

        try {
            jaxRsStrategy.apply(TestJaxRsApi::doSomething);
            fail("The above statement should throw");
        } catch (IOException e) {
            // As expected.
        }

        // Check that there was no second attempt.
        verify(mockApi, times(1)).doSomething();
    }
}
