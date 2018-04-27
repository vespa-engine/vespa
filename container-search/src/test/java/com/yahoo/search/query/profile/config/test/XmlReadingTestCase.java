// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class XmlReadingTestCase {

    @Test
    public void testValid() {
        QueryProfileRegistry registry=
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/validxml");
        CompiledQueryProfileRegistry cRegistry= registry.compile();

        QueryProfileType rootType=registry.getType("rootType");
        assertEquals(1,rootType.inherited().size());
        assertEquals("native",rootType.inherited().get(0).getId().getName());
        assertTrue(rootType.isStrict());
        assertTrue(rootType.getMatchAsPath());
        FieldDescription timeField=rootType.getField("time");
        assertTrue(timeField.isMandatory());
        assertEquals("long",timeField.getType().toInstanceDescription());
        FieldDescription userField=rootType.getField("user");
        assertFalse(userField.isMandatory());
        assertEquals("reference to a query profile of type 'user'",userField.getType().toInstanceDescription());

        QueryProfileType user=registry.getType("user");
        assertEquals(0,user.inherited().size());
        assertFalse(user.isStrict());
        assertFalse(user.getMatchAsPath());
        assertTrue(userField.isOverridable());
        FieldDescription ageField=user.getField("age");
        assertTrue(ageField.isMandatory());
        assertEquals("integer",ageField.getType().toInstanceDescription());
        FieldDescription robotField=user.getField("robot");
        assertFalse(robotField.isMandatory());
        assertFalse(robotField.isOverridable());
        assertEquals("boolean",robotField.getType().toInstanceDescription());

        CompiledQueryProfile defaultProfile=cRegistry.getComponent("default");
        assertNull(defaultProfile.getType());
        assertEquals("20",defaultProfile.get("hits"));
        assertFalse(defaultProfile.isOverridable(new CompoundName("hits"), null));
        assertFalse(defaultProfile.isOverridable(new CompoundName("user.trusted"), null));
        assertEquals("false",defaultProfile.get("user.trusted"));

        CompiledQueryProfile referencingProfile=cRegistry.getComponent("referencingModelSettings");
        assertNull(referencingProfile.getType());
        assertEquals("some query",referencingProfile.get("model.queryString"));
        assertEquals("aDefaultIndex",referencingProfile.get("model.defaultIndex"));

        // Request parameters here should be ignored
        HttpRequest request=HttpRequest.createTestRequest("?query=foo&user.trusted=true&default-index=title", Method.GET);
        Query query=new Query(request, defaultProfile);
        assertEquals("false",query.properties().get("user.trusted"));
        assertEquals("default",query.getModel().getDefaultIndex());
        assertEquals("default",query.properties().get("default-index"));

        CompiledQueryProfile rootProfile=cRegistry.getComponent("root");
        assertEquals("rootType",rootProfile.getType().getId().getName());
        assertEquals(30,rootProfile.get("hits"));
        assertEquals(3,rootProfile.get("traceLevel"));
        assertTrue(rootProfile.isOverridable(new CompoundName("hits"), null));

        QueryProfile someUser=registry.getComponent("someUser");
        assertEquals("5",someUser.get("sub.test"));
        assertEquals(18,someUser.get("age"));

        // aliases
        assertEquals(18,someUser.get("alder"));
        assertEquals(18,someUser.get("anno"));
        assertEquals(18,someUser.get("aLdER"));
        assertEquals(18,someUser.get("ANNO"));
        assertNull(someUser.get("Age")); // Only aliases are case insensitive

        Map<String, String> context = new HashMap<>();
        context.put("x", "x1");
        assertEquals(37, someUser.get("alder", context, null));
        assertEquals(37,someUser.get("anno", context, null));
        assertEquals(37,someUser.get("aLdER", context, null));
        assertEquals(37,someUser.get("ANNO", context, null));
        assertEquals("male",someUser.get("gender", context, null));
        assertEquals("male",someUser.get("sex", context, null));
        assertEquals("male",someUser.get("Sex", context, null));
        assertNull(someUser.get("Gender", context, null)); // Only aliases are case insensitive
    }

    @Test
    public void testBasicsNoProfile() {
        Query q=new Query(HttpRequest.createTestRequest("?query=test", Method.GET));
        assertEquals("test",q.properties().get("query"));
        assertEquals("test",q.properties().get("QueRY"));
        assertEquals("test",q.properties().get("model.queryString"));
        assertEquals("test",q.getModel().getQueryString());
    }

    @Test
    public void testBasicsWithProfile() {
        QueryProfile p = new QueryProfile("default");
        p.set("a", "foo", null);
        Query q=new Query(HttpRequest.createTestRequest("?query=test", Method.GET), p.compile(null));
        assertEquals("test", q.properties().get("query"));
        assertEquals("test", q.properties().get("QueRY"));
        assertEquals("test", q.properties().get("model.queryString"));
        assertEquals("test", q.getModel().getQueryString());
    }

    /** Tests a subset of the configuration in the system test of this */
    @Test
    public void testSystemtest() {
        String queryString = "?query=test";

        QueryProfileXMLReader reader = new QueryProfileXMLReader();
        CompiledQueryProfileRegistry registry = reader.read("src/test/java/com/yahoo/search/query/profile/config/test/systemtest/").compile();
        HttpRequest request = HttpRequest.createTestRequest(queryString, Method.GET);
        CompiledQueryProfile profile = registry.findQueryProfile("default");
        Query query = new Query(request, profile);
        Properties p = query.properties();

        assertEquals("test", query.getModel().getQueryString());
        assertEquals("test", p.get("query"));
        assertEquals("test", p.get("QueRY"));
        assertEquals("test", p.get("model.queryString"));
        assertEquals("bar", p.get("foo"));
        assertEquals(5, p.get("hits"));
        assertEquals("tit", p.get("subst"));
        assertEquals("le", p.get("subst.end"));
        assertEquals("title", p.get("model.defaultIndex"));

        Map<String,Object> ps = p.listProperties();
        assertEquals("bar", ps.get("foo"));
        assertEquals(5, ps.get("hits"));
        assertEquals("tit", ps.get("subst"));
        assertEquals("le", ps.get("subst.end"));
        assertEquals("title", ps.get("model.defaultIndex"));
        assertEquals("test", ps.get("model.queryString"));
    }

    @Test
    public void testInvalid1() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml1");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Error reading query profile 'illegalSetting' of type 'native': Could not set 'model.notDeclared' to 'value': 'notDeclared' is not declared in query profile type 'model', and the type is strict", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testInvalid2() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml2");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not parse 'unparseable.xml', error at line 2, column 21: Element type \"query-profile\" must be followed by either attribute specifications, \">\" or \"/>\".", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testInvalid3() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml3");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("The file name of query profile 'MyProfile' must be 'MyProfile.xml' but was 'default.xml'", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void testQueryProfileVariants() {
        String query = "?query=test&dim1=yahoo&dim2=uk&dim3=test";

        QueryProfileXMLReader reader = new QueryProfileXMLReader();
        CompiledQueryProfileRegistry registry = reader.read("src/test/java/com/yahoo/search/query/profile/config/test/news/").compile();
        HttpRequest request = HttpRequest.createTestRequest(query, Method.GET);
        CompiledQueryProfile profile = registry.findQueryProfile("default");
        Query q = new Query(request, profile);

        assertEquals("c", q.properties().get("a.c"));
        assertEquals("b", q.properties().get("a.b"));
    }

    @Test
    public void testNewsFE1() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newsfe").compile();

        String queryString="tiled?vertical=news&query=barack&intl=us&resulttypes=article&testid=&clientintl=us&SpellState=&rss=0&tracelevel=5";

        Query query=new Query(HttpRequest.createTestRequest(queryString, Method.GET), registry.getComponent("default"));
        assertEquals("13",query.properties().listProperties().get("source.news.discovery.sources.count"));
        assertEquals("13",query.properties().get("source.news.discovery.sources.count"));
        assertEquals("sources",query.properties().listProperties().get("source.news.discovery"));
        assertEquals("sources",query.properties().get("source.news.discovery"));
    }

    @Test
    public void testQueryProfileVariants2() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/queryprofilevariants2").compile();
        CompiledQueryProfile multi = registry.getComponent("multi");

        {
            Query query=new Query(HttpRequest.createTestRequest("?queryProfile=multi", Method.GET), multi);
            query.validate();
            assertEquals("best",query.properties().get("model.queryString"));
            assertEquals("best",query.getModel().getQueryString());
        }
        {
            Query query=new Query(HttpRequest.createTestRequest("?queryProfile=multi&myindex=default", Method.GET), multi);
            query.validate();
            assertEquals("best", query.properties().get("model.queryString"));
            assertEquals("best", query.getModel().getQueryString());
            assertEquals("default", query.getModel().getDefaultIndex());
        }
        {
            Query query=new Query(HttpRequest.createTestRequest("?queryProfile=multi&myindex=default&myquery=love", Method.GET), multi);
            query.validate();
            assertEquals("love", query.properties().get("model.queryString"));
            assertEquals("love", query.getModel().getQueryString());
            assertEquals("default", query.getModel().getDefaultIndex());
        }
        {
            Query query=new Query(HttpRequest.createTestRequest("?model=querybest", Method.GET), multi);
            query.validate();
            assertEquals("best",query.getModel().getQueryString());
            assertEquals("title",query.properties().get("model.defaultIndex"));
            assertEquals("title",query.getModel().getDefaultIndex());
        }
    }

    @Test
    public void testKlee() {
        QueryProfileRegistry registry=
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/klee");

        QueryProfile pv=registry.getComponent("twitter_dd-us:0.2.4");
        assertEquals("0.2.4",pv.getId().getVersion().toString());
        assertEquals("[query profile 'production']",pv.inherited().toString());

        QueryProfile p=registry.getComponent("twitter_dd-us:0.0.0");
        assertEquals("",p.getId().getVersion().toString()); // that is 0.0.0
        assertEquals("[query profile 'twitter_dd']",p.inherited().toString());
    }

    @Test
    public void testVersions() {
        QueryProfileRegistry registry=
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/versions");
        registry.freeze();

        assertEquals("1.20.100",registry.findQueryProfile("testprofile:1.20.100").getId().getVersion().toString());
        assertEquals("1.20.100",registry.findQueryProfile("testprofile:1.20").getId().getVersion().toString());
        assertEquals("1.20.100",registry.findQueryProfile("testprofile:1").getId().getVersion().toString());
        assertEquals("1.20.100",registry.findQueryProfile("testprofile").getId().getVersion().toString());
    }

    @Test
    public void testNewsFE2() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newsfe2").compile();

        String queryString="tiled?query=a&intl=tw&mode=adv&mode=adv";

        Query query=new Query(HttpRequest.createTestRequest(queryString, Method.GET),registry.getComponent("default"));
        assertEquals("news_adv",query.properties().listProperties().get("provider"));
        assertEquals("news_adv",query.properties().get("provider"));
    }

    @Test
    public void testSourceProvider() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/sourceprovider").compile();

        String queryString="tiled?query=india&queryProfile=myprofile&source.common.intl=tw&source.common.mode=adv";

        Query query=new Query(HttpRequest.createTestRequest(queryString, Method.GET), registry.getComponent("myprofile"));
        for (Map.Entry e : query.properties().listProperties().entrySet())
            System.out.println(e);
        assertEquals("news",query.properties().listProperties().get("source.common.provider"));
        assertEquals("news",query.properties().get("source.common.provider"));
    }

    @Test
    public void testNewsCase1() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase1").compile();

        Query query;
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),registry.getComponent("default"));
        assertEquals("0.0",query.properties().get("ranking.features.b"));
        assertEquals("0.0",query.properties().listProperties().get("ranking.features.b"));
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),registry.getComponent("default"));
        assertEquals("0.1",query.properties().get("ranking.features.b"));
        assertEquals("0.1",query.properties().listProperties().get("ranking.features.b"));
    }

    @Test
    public void testNewsCase2() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase2").compile();

        Query query;
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),registry.getComponent("default"));
        assertEquals("0.0",query.properties().get("a.features.b"));
        assertEquals("0.0",query.properties().listProperties().get("a.features.b"));
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),registry.getComponent("default"));
        assertEquals("0.1",query.properties().get("a.features.b"));
        assertEquals("0.1",query.properties().listProperties().get("a.features.b"));
    }

    @Test
    public void testNewsCase3() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase3").compile();

        Query query;
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),registry.getComponent("default"));
        assertEquals("0.0",query.properties().get("a.features"));
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),registry.getComponent("default"));
        assertEquals("0.1",query.properties().get("a.features"));
    }

    // Should cause an exception on the first line as we are trying to create a profile setting an illegal value in "ranking"
    @Test
    public void testNewsCase4() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase4").compile();

        Query query;
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),registry.getComponent("default"));
        assertEquals("0.0",query.properties().get("ranking.features"));
        query=new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),registry.getComponent("default"));
        assertEquals("0.1",query.properties().get("ranking.features"));
    }

    @Test
    public void testVersionRefs() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/versionrefs").compile();

        Query query=new Query(HttpRequest.createTestRequest("?query=test", Method.GET),registry.getComponent("default"));
        assertEquals("MyProfile:1.0.2",query.properties().get("profile1.name"));
    }

    @Test
    public void testRefOverride() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/refoverride").compile();

        {
            // Original reference
            Query query=new Query(HttpRequest.createTestRequest("?query=test", Method.GET),registry.getComponent("default"));
            assertEquals(null,query.properties().get("profileRef"));
            assertEquals("MyProfile1",query.properties().get("profileRef.name"));
            assertEquals("myProfile1Only",query.properties().get("profileRef.myProfile1Only"));
            assertNull(query.properties().get("profileRef.myProfile2Only"));
        }

        {
            // Overridden reference
            Query query=new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET),registry.getComponent("default"));
            assertEquals(null,query.properties().get("profileRef"));
            assertEquals("MyProfile2",query.properties().get("profileRef.name"));
            assertEquals("myProfile2Only",query.properties().get("profileRef.myProfile2Only"));
            assertNull(query.properties().get("profileRef.myProfile1Only"));

            // later assignment
            query.properties().set("profileRef.name","newName");
            assertEquals("newName",query.properties().get("profileRef.name"));
            // ...will not impact others
            query=new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET),registry.getComponent("default"));
            assertEquals("MyProfile2",query.properties().get("profileRef.name"));
        }

    }

    @Test
    public void testRefOverrideTyped() {
        CompiledQueryProfileRegistry registry=new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/refoverridetyped").compile();

        {
            // Original reference
            Query query=new Query(HttpRequest.createTestRequest("?query=test", Method.GET),registry.getComponent("default"));
            assertEquals(null,query.properties().get("profileRef"));
            assertEquals("MyProfile1",query.properties().get("profileRef.name"));
            assertEquals("myProfile1Only",query.properties().get("profileRef.myProfile1Only"));
            assertNull(query.properties().get("profileRef.myProfile2Only"));
        }

        {
            // Overridden reference
            Query query=new Query(HttpRequest.createTestRequest("?query=test&profileRef=MyProfile2", Method.GET),registry.getComponent("default"));
            assertEquals(null,query.properties().get("profileRef"));
            assertEquals("MyProfile2",query.properties().get("profileRef.name"));
            assertEquals("myProfile2Only",query.properties().get("profileRef.myProfile2Only"));
            assertNull(query.properties().get("profileRef.myProfile1Only"));

            // later assignment
            query.properties().set("profileRef.name","newName");
            assertEquals("newName",query.properties().get("profileRef.name"));
            // ...will not impact others
            query=new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET),registry.getComponent("default"));
            assertEquals("MyProfile2",query.properties().get("profileRef.name"));
        }
    }

}
