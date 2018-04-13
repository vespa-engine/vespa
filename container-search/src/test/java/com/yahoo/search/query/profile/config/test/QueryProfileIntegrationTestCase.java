// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.HttpSearchResponse;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests using query profiles in searches
 *
 * @author bratseth
 */
public class QueryProfileIntegrationTestCase {

    @org.junit.After
    public void tearDown() {
        System.getProperties().remove("config.id");
    }

    @Test
    public void testUntyped() {
        String configId = "dir:src/test/java/com/yahoo/search/query/profile/config/test/untyped";
        System.setProperty("config.id", configId);
        Container container = new Container();
        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper(container, configId);
        SearchHandler searchHandler = (SearchHandler) configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());

        // Should get "default" query profile containing the "test" search chain containing the "test" searcher
        HttpRequest request = HttpRequest.createTestRequest("search", Method.GET);
        HttpSearchResponse response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:test"));

        // Should get the "test' query profile containing the "default" search chain containing the "default" searcher
        request = HttpRequest.createTestRequest("search?queryProfile=test", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:default"));

        // Should get "default" query profile, but override the search chain to default
        request = HttpRequest.createTestRequest("search?searchChain=default", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:default"));

        // Tests a profile setting hits and offset
        request = HttpRequest.createTestRequest("search?queryProfile=hitsoffset", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertEquals(20,response.getQuery().getHits());
        assertEquals(80,response.getQuery().getOffset());

        // Tests a non-resolved profile request
        request = HttpRequest.createTestRequest("search?queryProfile=none", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull("Got an error",response.getResult().hits().getError());
        assertEquals("Could not resolve query profile 'none'",response.getResult().hits().getError().getDetailedMessage());

        // Tests that properties in objects owned by query is handled correctly
        request = HttpRequest.createTestRequest("search?query=word&queryProfile=test", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertEquals("index",response.getQuery().getModel().getDefaultIndex());
        assertEquals("index:word",response.getQuery().getModel().getQueryTree().toString());
        configurer.shutdown();
    }

    @Test
    public void testTyped() {
        String configId = "dir:src/test/java/com/yahoo/search/query/profile/config/test/typed";
        System.setProperty("config.id", configId);
        Container container = new Container();
        HandlersConfigurerTestWrapper configurer = new HandlersConfigurerTestWrapper(container, configId);
        SearchHandler searchHandler = (SearchHandler) configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());

        // Should get "default" query profile containing the "test" search chain containing the "test" searcher
        HttpRequest request = HttpRequest.createTestRequest("search", Method.GET);
        HttpSearchResponse response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:test"));

        // Should get the "test' query profile containing the "default" search chain containing the "default" searcher
        request = HttpRequest.createTestRequest("search?queryProfile=test", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:default"));

        // Should get "default" query profile, but override the search chain to default
        request = HttpRequest.createTestRequest("search?searchChain=default", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull(response.getResult().hits().get("from:default"));

        // Tests a profile setting hits and offset
        request = HttpRequest.createTestRequest("search?queryProfile=hitsoffset", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertEquals(22,response.getQuery().getHits());
        assertEquals(80,response.getQuery().getOffset());

        // Tests a non-resolved profile request
        request = HttpRequest.createTestRequest("search?queryProfile=none", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertNotNull("Got an error",response.getResult().hits().getError());
        assertEquals("Could not resolve query profile 'none'",response.getResult().hits().getError().getDetailedMessage());

        // Test overriding a sub-profile in the request
        request = HttpRequest.createTestRequest("search?queryProfile=root&sub=newsub", Method.GET);
        response = (HttpSearchResponse)searchHandler.handle(request); // Cast to access content directly
        assertEquals("newsubvalue1",response.getQuery().properties().get("sub.value1"));
        assertEquals("newsubvalue2",response.getQuery().properties().get("sub.value2"));
        configurer.shutdown();
    }

    public static class DefaultSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            result.hits().add(new Hit("from:default"));
            return result;
        }

    }

    public static class TestSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            Result result=execution.search(query);
            result.hits().add(new Hit("from:test"));
            return result;
        }

    }

    /** Tests searcher communication - setting */
    @Provides("SomeObject")
    public static class SettingSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            SomeObject.setTo(query,new SomeObject());
            return execution.search(query);
        }

    }

    /** Tests searcher communication - receiving */
    @After("SomeObject")
    public static class ReceivingSearcher extends Searcher {

        @Override
        public Result search(Query query,Execution execution) {
            assertNotNull(SomeObject.getFrom(query));
            assertEquals(SomeObject.class,SomeObject.getFrom(query).getClass());
            return execution.search(query);
        }

    }

    /** An example of a model object */
    private static class SomeObject {

        public static void setTo(Query query,SomeObject someObject) {
            query.properties().set("SomeObject",someObject);
        }

        public static SomeObject getFrom(Query query) {
            // In some cases we want to create if this does not exist here
            return (SomeObject)query.properties().get("SomeObject");
        }

    }

}
