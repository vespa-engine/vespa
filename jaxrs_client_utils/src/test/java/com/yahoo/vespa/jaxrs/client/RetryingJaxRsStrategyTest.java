// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.jaxrs.client;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.defaults.Defaults;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RetryingJaxRsStrategyTest {
    private static final String API_PATH = "/";

    @Path(API_PATH)
    private interface TestJaxRsApi {
        @GET
        @Path("/foo/bar")
        String doSomething();
    }

    private static final Set<HostName> SERVER_HOSTS = new HashSet<>(Arrays.asList(
            new HostName("host-1"),
            new HostName("host-2"),
            new HostName("host-3")));
    private static final int REST_PORT = Defaults.getDefaults().vespaWebServicePort();

    private final JaxRsClientFactory jaxRsClientFactory = mock(JaxRsClientFactory.class);
    private final TestJaxRsApi mockApi = mock(TestJaxRsApi.class);
    private final RetryingJaxRsStrategy<TestJaxRsApi> jaxRsStrategy = new RetryingJaxRsStrategy<>(
            SERVER_HOSTS, REST_PORT, jaxRsClientFactory, TestJaxRsApi.class, API_PATH, "http");

    @Before
    public void setup() {
        when(jaxRsClientFactory.createClient(eq(TestJaxRsApi.class), any(HostName.class), anyInt(), anyString(), anyString()))
                .thenReturn(mockApi);
    }

    @Test
    public void noRetryIfNoFailure() throws Exception {
        jaxRsStrategy.apply(TestJaxRsApi::doSomething);

        verify(mockApi, times(1)).doSomething();

        // Check that one of the supplied hosts is contacted.
        final ArgumentCaptor<HostName> hostNameCaptor = ArgumentCaptor.forClass(HostName.class);
        verify(jaxRsClientFactory, times(1))
                .createClient(eq(TestJaxRsApi.class), hostNameCaptor.capture(), eq(REST_PORT), eq(API_PATH), eq("http"));
        assertThat(SERVER_HOSTS.contains(hostNameCaptor.getValue()), is(true));
    }

    @Test
    public void testRetryAfterSingleFailure() throws Exception {
        // Make the first attempt fail.
        when(mockApi.doSomething())
                .thenThrow(new ProcessingException("Fake timeout induced by test"))
                .thenReturn("a response");

        jaxRsStrategy.apply(TestJaxRsApi::doSomething);

        // Check that there was a second attempt.
        verify(mockApi, times(2)).doSomething();
    }

    @Test
    public void testRetryUsesAllAvailableServers() throws Exception {
        when(mockApi.doSomething())
                .thenThrow(new ProcessingException("Fake timeout 1 induced by test"))
                .thenThrow(new ProcessingException("Fake timeout 2 induced by test"))
                .thenReturn("a response");

        jaxRsStrategy.apply(TestJaxRsApi::doSomething);

        verify(mockApi, times(3)).doSomething();
        verifyAllServersContacted(jaxRsClientFactory);
    }

    @Test
    public void testRetryLoopsOverAvailableServers() throws Exception {
        when(mockApi.doSomething())
                .thenThrow(new ProcessingException("Fake timeout 1 induced by test"))
                .thenThrow(new ProcessingException("Fake timeout 2 induced by test"))
                .thenThrow(new ProcessingException("Fake timeout 3 induced by test"))
                .thenThrow(new ProcessingException("Fake timeout 4 induced by test"))
                .thenReturn("a response");

        jaxRsStrategy.apply(TestJaxRsApi::doSomething);

        verify(mockApi, times(5)).doSomething();
        verifyAllServersContacted(jaxRsClientFactory);
    }

    @Test
    public void testRetryGivesUpAfterOneLoopOverAvailableServers() {
        jaxRsStrategy.setMaxIterations(1);
        testRetryGivesUpAfterXIterations(1);
    }

    @Test
    public void testRetryGivesUpAfterTwoLoopsOverAvailableServers() {
        testRetryGivesUpAfterXIterations(2);
    }

    private void testRetryGivesUpAfterXIterations(int iterations) {
        OngoingStubbing<String> stub = when(mockApi.doSomething());
        for (int i = 0; i < iterations; ++i) {
            stub = stub
                    .thenThrow(new ProcessingException("Fake timeout 1 iteration " + i))
                    .thenThrow(new ProcessingException("Fake timeout 2 iteration " + i))
                    .thenThrow(new ProcessingException("Fake timeout 3 iteration " + i));
        }

        try {
            jaxRsStrategy.apply(TestJaxRsApi::doSomething);
            fail("Exception should be thrown from above statement");
        } catch (IOException e) {
            // As expected.
        }

        verify(mockApi, times(iterations * 3)).doSomething();
        verifyAllServersContacted(jaxRsClientFactory);
    }

    private static void verifyAllServersContacted(
            final JaxRsClientFactory jaxRsClientFactory) {
        final ArgumentCaptor<HostName> hostNameCaptor = ArgumentCaptor.forClass(HostName.class);
        verify(jaxRsClientFactory, atLeast(SERVER_HOSTS.size()))
                .createClient(eq(TestJaxRsApi.class), hostNameCaptor.capture(), eq(REST_PORT), eq(API_PATH), eq("http"));
        final Set<HostName> actualServerHostsContacted = new HashSet<>(hostNameCaptor.getAllValues());
        assertThat(actualServerHostsContacted, equalTo(SERVER_HOSTS));
    }
}
