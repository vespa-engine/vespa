// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import org.junit.Test;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ParametersTestCase {

    @Test
    public void testSettingRankProperty() {
        Query query=new Query("?query=test&ranking.properties.dotProduct.X=(a:1,b:2)");
        assertEquals("[(a:1,b:2)]",query.getRanking().getProperties().get("dotProduct.X").toString());
    }

    @Test
    public void testSettingRankPropertyAsAlias() {
        Query query=new Query("?query=test&rankproperty.dotProduct.X=(a:1,b:2)");
        assertEquals("[(a:1,b:2)]",query.getRanking().getProperties().get("dotProduct.X").toString());
    }

    @Test
    public void testSettingRankFeature() {
        Query query=new Query("?query=test&ranking.features.matches=3");
        assertEquals("3",query.getRanking().getFeatures().get("matches").toString());
    }

    @Test
    public void testSettingRankFeatureAsAlias() {
        Query query=new Query("?query=test&rankfeature.matches=3");
        assertEquals("3",query.getRanking().getFeatures().get("matches").toString());
    }

    @Test
    public void testSettingRankPropertyWithQueryProfile() {
        Query query=new Query(HttpRequest.createTestRequest("?query=test&ranking.properties.dotProduct.X=(a:1,b:2)", Method.GET), createProfile());
        assertEquals("[(a:1,b:2)]",query.getRanking().getProperties().get("dotProduct.X").toString());
    }

    @Test
    public void testSettingRankPropertyAsAliasWithQueryProfile() {
        Query query=new Query(HttpRequest.createTestRequest("?query=test&rankproperty.dotProduct.X=(a:1,b:2)", Method.GET), createProfile());
        assertEquals("[(a:1,b:2)]",query.getRanking().getProperties().get("dotProduct.X").toString());
    }

    @Test
    public void testSettingRankFeatureWithQueryProfile() {
        Query query=new Query(HttpRequest.createTestRequest("?query=test&ranking.features.matches=3", Method.GET), createProfile());
        assertEquals("3",query.getRanking().getFeatures().get("matches").toString());
    }

    @Test
    public void testSettingRankFeatureAsAliasWithQueryProfile() {
        Query query=new Query(HttpRequest.createTestRequest("?query=test&rankfeature.matches=3", Method.GET), createProfile());
        assertEquals("3",query.getRanking().getFeatures().get("matches").toString());
    }

    public CompiledQueryProfile createProfile() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfile profile = new QueryProfile("test");
        profile.set("model.filter", "+year:2001", registry);
        profile.set("model.language", "en", registry);
        return registry.compile().findQueryProfile("test");
    }

}
