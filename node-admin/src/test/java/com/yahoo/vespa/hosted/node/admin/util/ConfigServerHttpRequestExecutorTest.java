package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ConfigServerHttpRequestExecutorTest {


    @Ignore
    @Test
    public void test() throws Exception {
        HttpClient httpMock = mock(HttpClient.class);
        when(httpMock.execute(any())).thenAnswer(new Answer<HttpResponse>() {
            @Override
            public HttpResponse answer(InvocationOnMock invocationOnMock) throws Throwable {
                return mock(HttpResponse.class);
            }
        });
        Set<HostName> configServers = new HashSet<>();
        configServers.add(HostName.apply("host1"));
        configServers.add(HostName.apply("host2"));
        ConfigServerHttpRequestExecutor executor = new ConfigServerHttpRequestExecutor(configServers, httpMock);
        String answer = executor.get("path", 666, String.class);
    }

}