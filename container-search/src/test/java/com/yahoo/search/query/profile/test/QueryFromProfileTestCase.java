// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.QueryProfileType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test using the profile to set the query to execute
 *
 * @author bratseth
 */
public class QueryFromProfileTestCase {

    @Test
    public void testQueryFromProfile1() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile topLevel = new QueryProfile("topLevel");
        topLevel.setType(registry.getTypeRegistry().getComponent("native"));
        registry.register(topLevel);

        QueryProfile queryBest = new QueryProfile("querybest");
        queryBest.setType(registry.getTypeRegistry().getComponent("model"));
        queryBest.set("queryString", "best", registry);
        registry.register(queryBest);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        Query query = new Query(HttpRequest.createTestRequest("?model=querybest", Method.GET), cRegistry.getComponent("topLevel"));
        assertEquals("best", query.properties().get("model.queryString"));
        assertEquals("best", query.getModel().getQueryTree().toString());
    }

    @Test
    public void testQueryFromProfile2() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileType rootType = new QueryProfileType("root");
        rootType.inherited().add(registry.getTypeRegistry().getComponent("native"));
        registry.getTypeRegistry().register(rootType);

        QueryProfile root = new QueryProfile("root");
        root.setType(rootType);
        registry.register(root);

        QueryProfile queryBest=new QueryProfile("querybest");
        queryBest.setType(registry.getTypeRegistry().getComponent("model"));
        queryBest.set("queryString", "best", registry);
        registry.register(queryBest);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        Query query = new Query(HttpRequest.createTestRequest("?query=overrides&model=querybest", Method.GET), cRegistry.getComponent("root"));
        assertEquals("overrides", query.properties().get("model.queryString"));
        assertEquals("overrides", query.getModel().getQueryTree().toString());
    }

    @Test
    public void testQueryFromProfile3() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileType rootType = new QueryProfileType("root");
        rootType.inherited().add(registry.getTypeRegistry().getComponent("native"));
        registry.getTypeRegistry().register(rootType);

        QueryProfile root = new QueryProfile("root");
        root.setType(rootType);
        registry.register(root);

        QueryProfile queryBest=new QueryProfile("querybest");
        queryBest.setType(registry.getTypeRegistry().getComponent("model"));
        queryBest.set("queryString", "best", registry);
        registry.register(queryBest);

        CompiledQueryProfileRegistry cRegistry = registry.compile();

        Query query = new Query(HttpRequest.createTestRequest("?query=overrides&model=querybest", Method.GET), cRegistry.getComponent("root"));
        assertEquals("overrides", query.properties().get("model.queryString"));
        assertEquals("overrides", query.getModel().getQueryTree().toString());
    }

}
