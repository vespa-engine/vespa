// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.StupidSingleThreadedHttpServer;
import com.yahoo.search.federation.ProviderConfig.PingOption;
import com.yahoo.search.federation.http.Connection;
import com.yahoo.search.federation.http.HTTPProviderSearcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.junit.Test;

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;

/**
 * See bug #3234696.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class HttpPostTestCase {

    @Test
    public void testPostingSearcher() throws Exception {
        StupidSingleThreadedHttpServer server = new StupidSingleThreadedHttpServer();
        server.start();

        TestPostSearcher searcher = new TestPostSearcher(new ComponentId("foo:1"),
                                                         Arrays.asList(new Connection("localhost", server.getServerPort())),
                                                         "/");
        Query q = new Query("");
        q.setTimeout(10000000L);
        Execution e = new Execution(searcher, Execution.Context.createContextStub());

        searcher.search(q, e);

        assertThat(server.getRequest(), containsString("My POST body"));
        server.stop();
    }

    private static class TestPostSearcher extends HTTPProviderSearcher {
        public TestPostSearcher(ComponentId id, List<Connection> connections, String path) {
            super(id, connections, httpParameters(path), Statistics.nullImplementation);
        }

        private static HTTPParameters httpParameters(String path) {
            HTTPParameters httpParameters = new HTTPParameters(path);
            httpParameters.setPingOption(PingOption.Enum.DISABLE);
            return httpParameters;
        }

        @Override
        protected HttpUriRequest createRequest(String method, URI uri, HttpEntity entity) {
            HttpPost request = new HttpPost(uri);
            request.setEntity(entity);
            return request;
        }

        @Override
        protected HttpEntity getRequestEntity(Query query, Hit requestMeta) {
            try {
                return new StringEntity("My POST body");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Map<String, String> getCacheKey(Query q) {
            return new HashMap<>(0);
        }

        @Override
        public void unmarshal(final InputStream stream, long contentLength, final Result result) throws IOException {
            // do nothing with the result
        }

        @Override
        protected void fill(Result result, String summaryClass, Execution execution, Connection connection) {
            //Empty
        }
    }
}
