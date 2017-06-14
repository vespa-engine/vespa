// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import org.mockito.Mockito;

import com.yahoo.component.chain.Chain;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.HttpRequest.Version;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.ConnectionControlSearcher;

/**
 * Functionality tests for
 * {@link com.yahoo.search.searchers.ConnectionControlSearcher}.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ConnectionControlSearcherTestCase {

    @Test
    public final void test() throws URISyntaxException {
        URI uri = new URI("http://finance.yahoo.com/?connectioncontrol.maxlifetime=1");
        long connectedAtMillis = 0L;
        long nowMillis = 2L * 1000L;
        Result r = doSearch(uri, connectedAtMillis, nowMillis);
        assertEquals("Close", r.getHeaders(false).get("Connection").get(0));
    }

    @Test
    public final void testForcedClose() throws URISyntaxException {
        URI uri = new URI("http://finance.yahoo.com/?connectioncontrol.maxlifetime=0");
        long connectedAtMillis = 0L;
        long nowMillis = 0L;
        Result r = doSearch(uri, connectedAtMillis, nowMillis);
        assertEquals("Close", r.getHeaders(false).get("Connection").get(0));
    }

    @Test
    public final void testNormalCloseWithoutJdisc() {
        long nowMillis = 2L;
        Query query = new Query("/?connectioncontrol.maxlifetime=1");
        Execution e = new Execution(new Chain<Searcher>(ConnectionControlSearcher.createTestInstance(() -> nowMillis)),
                Execution.Context.createContextStub());
        Result r = e.search(query);
        assertNull(r.getHeaders(false));
    }

    @Test
    public final void testNoMaxLifetime() throws URISyntaxException {
        URI uri = new URI("http://finance.yahoo.com/");
        long connectedAtMillis = 0L;
        long nowMillis = 0L;
        Result r = doSearch(uri, connectedAtMillis, nowMillis);
        assertNull(r.getHeaders(false));
    }

    @Test
    public final void testYoungEnoughConnection() throws URISyntaxException {
        URI uri = new URI("http://finance.yahoo.com/?connectioncontrol.maxlifetime=1");
        long connectedAtMillis = 0L;
        long nowMillis = 500L;
        Result r = doSearch(uri, connectedAtMillis, nowMillis);
        assertNull(r.getHeaders(false));
    }


    private Result doSearch(URI uri, long connectedAtMillis, long nowMillis) {
        SocketAddress remoteAddress = Mockito.mock(SocketAddress.class);
        Version version = Version.HTTP_1_1;
        Method method = Method.GET;
        CurrentContainer container = Mockito.mock(CurrentContainer.class);
        Mockito.when(container.newReference(Mockito.any())).thenReturn(Mockito.mock(Container.class));
        final com.yahoo.jdisc.http.HttpRequest serverRequest = com.yahoo.jdisc.http.HttpRequest
                .newServerRequest(container, uri, method, version, remoteAddress, connectedAtMillis);
        HttpRequest incoming = new HttpRequest(serverRequest, new ByteArrayInputStream(new byte[0]));
        Query query = new Query(incoming);
        Execution e = new Execution(new Chain<Searcher>(ConnectionControlSearcher.createTestInstance(() -> nowMillis)),
                Execution.Context.createContextStub());
        Result r = e.search(query);
        return r;
    }

}
