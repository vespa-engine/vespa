// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.config.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.language.process.Embedder;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.QueryType;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class XmlReadingTestCase {

    @Test
    void testInheritance() {
        QueryProfileRegistry registry =
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/inheritance");

        CompiledQueryProfile cProfile = registry.getComponent("child").compile(null);
        Query q = new Query("?query=foo", cProfile);
        assertEquals("a.b-parent", q.properties().getString("a.b"));
        assertEquals("d-parent", q.properties().getString("d"));
    }

    @Test
    void testValid() {
        QueryProfileRegistry registry =
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/validxml");
        CompiledQueryProfileRegistry cRegistry = registry.compile();

        QueryProfileType rootType = registry.getType("rootType");
        assertEquals(1, rootType.inherited().size());
        assertEquals("native", rootType.inherited().get(0).getId().getName());
        assertTrue(rootType.isStrict());
        assertTrue(rootType.getMatchAsPath());
        FieldDescription timeField = rootType.getField("time");
        assertTrue(timeField.isMandatory());
        assertEquals("long", timeField.getType().toInstanceDescription());
        FieldDescription userField = rootType.getField("user");
        assertFalse(userField.isMandatory());
        assertEquals("reference to a query profile of type 'user'", userField.getType().toInstanceDescription());

        QueryProfileType user = registry.getType("user");
        assertEquals(0, user.inherited().size());
        assertFalse(user.isStrict());
        assertFalse(user.getMatchAsPath());
        assertTrue(userField.isOverridable());
        FieldDescription ageField = user.getField("age");
        assertTrue(ageField.isMandatory());
        assertEquals("integer", ageField.getType().toInstanceDescription());
        FieldDescription robotField = user.getField("robot");
        assertFalse(robotField.isMandatory());
        assertFalse(robotField.isOverridable());
        assertEquals("boolean", robotField.getType().toInstanceDescription());

        CompiledQueryProfile defaultProfile = cRegistry.getComponent("default");
        assertNull(defaultProfile.getType());
        assertEquals("20", defaultProfile.get("hits"));
        assertFalse(defaultProfile.isOverridable(CompoundName.from("hits"), null));
        assertFalse(defaultProfile.isOverridable(CompoundName.from("user.trusted"), null));
        assertEquals("false", defaultProfile.get("user.trusted"));

        CompiledQueryProfile referencingProfile = cRegistry.getComponent("referencingModelSettings");
        assertNull(referencingProfile.getType());
        assertEquals("some query", referencingProfile.get("model.queryString"));
        assertEquals("aDefaultIndex", referencingProfile.get("model.defaultIndex"));

        // Request parameters here should be ignored
        HttpRequest request = HttpRequest.createTestRequest("?query=foo&user.trusted=true&default-index=title", Method.GET);
        Query query = new Query(request, defaultProfile);
        assertEquals("false", query.properties().get("user.trusted"));
        assertEquals("default", query.getModel().getDefaultIndex());
        assertEquals("default", query.properties().get("default-index"));

        CompiledQueryProfile rootProfile = cRegistry.getComponent("root");
        assertEquals("rootType", rootProfile.getType().getId().getName());
        assertEquals(30, rootProfile.get("hits"));
        //assertEquals(3, rootProfile.get("traceLevel"));
        assertTrue(rootProfile.isOverridable(CompoundName.from("hits"), null));
        query = new Query(request, rootProfile);
        assertEquals(3, query.getTrace().getLevel());

        QueryProfile someUser = registry.getComponent("someUser");
        assertEquals("5", someUser.get("sub.test"));
        assertEquals(18, someUser.get("age"));

        // aliases
        assertEquals(18, someUser.get("alder"));
        assertEquals(18, someUser.get("anno"));
        assertEquals(18, someUser.get("aLdER"));
        assertEquals(18, someUser.get("ANNO"));
        assertNull(someUser.get("Age")); // Only aliases are case insensitive

        Map<String, String> context = new HashMap<>();
        context.put("x", "x1");
        assertEquals(37, someUser.get("alder", context, null));
        assertEquals(37, someUser.get("anno", context, null));
        assertEquals(37, someUser.get("aLdER", context, null));
        assertEquals(37, someUser.get("ANNO", context, null));
        assertEquals("male", someUser.get("gender", context, null));
        assertEquals("male", someUser.get("sex", context, null));
        assertEquals("male", someUser.get("Sex", context, null));
        assertNull(someUser.get("Gender", context, null)); // Only aliases are case insensitive
    }

    @Test
    void testBasicsNoProfile() {
        Query q = new Query(HttpRequest.createTestRequest("?query=test", Method.GET));
        assertEquals("test", q.properties().get("query"));
        assertEquals("test", q.properties().get("QueRY"));
        assertEquals("test", q.properties().get("model.queryString"));
        assertEquals("test", q.getModel().getQueryString());
    }

    @Test
    void testBasicsWithProfile() {
        QueryProfile p = new QueryProfile("default");
        p.set("a", "foo", null);
        Query q = new Query(HttpRequest.createTestRequest("?query=test", Method.GET), p.compile(null));
        assertEquals("test", q.properties().get("query"));
        assertEquals("test", q.properties().get("QueRY"));
        assertEquals("test", q.properties().get("model.queryString"));
        assertEquals("test", q.getModel().getQueryString());
    }

    /** Test reading a (built-in) query profile that has a value at a non-root: query.type */
    @Test
    void testQueryType() {
        QueryProfileRegistry registry =
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/querytype");
        var cRegistry = registry.compile();
        var query = new Query("?test", cRegistry.findQueryProfile("default"));
        QueryType queryType = query.getModel().getQueryType();
        assertEquals(Query.Type.ALL, queryType.getType());
        assertEquals(QueryType.Syntax.web, queryType.getSyntax());
    }

    /** Tests a subset of the configuration in the system test of this */
    @Test
    void testSystemtest() {
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

        Map<String, Object> ps = p.listProperties();
        assertEquals("bar", ps.get("foo"));
        assertEquals(5, ps.get("hits"));
        assertEquals("tit", ps.get("subst"));
        assertEquals("le", ps.get("subst.end"));
        assertEquals("title", ps.get("model.defaultIndex"));
        assertEquals("test", ps.get("model.queryString"));
    }

    @Test
    void testInvalid1() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml1");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Error reading query profile 'illegalSetting' of type 'native': Could not set 'model.notDeclared' to 'value': 'notDeclared' is not declared in query profile type 'model', and the type is strict", Exceptions.toMessageString(e));
        }
    }

    @Test
    void testInvalid2() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml2");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
            assertEquals("Could not parse 'unparseable.xml', error at line 2, column 21: Element type \"query-profile\" must be followed by either attribute specifications, \">\" or \"/>\".", Exceptions.toMessageString(e));
        }
    }

    @Test
    void testInvalid3() {
        try {
            new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/invalidxml3");
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            assertEquals("The file name of query profile 'MyProfile' must be 'MyProfile.xml' but was 'default.xml'", Exceptions.toMessageString(e));
        }
    }

    @Test
    void testQueryProfileVariants() {
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
    void testQueryProfileVariantsWithOverridableFalse() {
        QueryProfileXMLReader reader = new QueryProfileXMLReader();
        CompiledQueryProfileRegistry registry = reader.read("src/test/java/com/yahoo/search/query/profile/config/test/variants/").compile();
        CompiledQueryProfile profile = registry.findQueryProfile("default");

        assertEquals("a.b.c-value", new Query("?d1=d1v", profile).properties().get("a.b.c"));
        assertEquals("a.b.c-variant-value", new Query("?d1=d1v&d2=d2v", profile).properties().get("a.b.c"));

        assertTrue(profile.isOverridable(CompoundName.from("a.b.c"), Map.of("d1", "d1v")));
        assertFalse(profile.isOverridable(CompoundName.from("a.b.c"), Map.of("d1", "d1v", "d2", "d2v")));
    }

    @Test
    void testNewsFE1() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newsfe").compile();

        String queryString = "tiled?vertical=news&query=barack&intl=us&resulttypes=article&testid=&clientintl=us&SpellState=&rss=0&tracelevel=5";

        Query query = new Query(HttpRequest.createTestRequest(queryString, Method.GET), registry.getComponent("default"));
        assertEquals("13", query.properties().listProperties().get("source.news.discovery.sources.count"));
        assertEquals("13", query.properties().get("source.news.discovery.sources.count"));
        assertEquals("sources", query.properties().listProperties().get("source.news.discovery"));
        assertEquals("sources", query.properties().get("source.news.discovery"));
    }

    @Test
    void testQueryProfileVariants2() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/queryprofilevariants2").compile();
        CompiledQueryProfile multi = registry.getComponent("multi");

        {
            Query query = new Query(HttpRequest.createTestRequest("?queryProfile=multi", Method.GET), multi);
            query.validate();
            assertEquals("best", query.properties().get("model.queryString"));
            assertEquals("best", query.getModel().getQueryString());
        }
        {
            Query query = new Query(HttpRequest.createTestRequest("?queryProfile=multi&myindex=default", Method.GET), multi);
            query.validate();
            assertEquals("best", query.properties().get("model.queryString"));
            assertEquals("best", query.getModel().getQueryString());
            assertEquals("default", query.getModel().getDefaultIndex());
        }
        {
            Query query = new Query(HttpRequest.createTestRequest("?queryProfile=multi&myindex=default&myquery=love", Method.GET), multi);
            query.validate();
            assertEquals("love", query.properties().get("model.queryString"));
            assertEquals("love", query.getModel().getQueryString());
            assertEquals("default", query.getModel().getDefaultIndex());
        }
        {
            Query query = new Query(HttpRequest.createTestRequest("?model=querybest", Method.GET), multi);
            query.validate();
            assertEquals("best", query.getModel().getQueryString());
            assertEquals("title", query.properties().get("model.defaultIndex"));
            assertEquals("title", query.getModel().getDefaultIndex());
        }
    }

    @Test
    void testKlee() {
        QueryProfileRegistry registry =
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/klee");

        QueryProfile pv = registry.getComponent("twitter_dd-us:0.2.4");
        assertEquals("0.2.4", pv.getId().getVersion().toString());
        assertEquals("[query profile 'production']", pv.inherited().toString());

        QueryProfile p = registry.getComponent("twitter_dd-us:0.0.0");
        assertEquals("", p.getId().getVersion().toString()); // that is 0.0.0
        assertEquals("[query profile 'twitter_dd']", p.inherited().toString());
    }

    @Test
    void testVersions() {
        QueryProfileRegistry registry =
                new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/versions");
        registry.freeze();

        assertEquals("1.20.100", registry.findQueryProfile("testprofile:1.20.100").getId().getVersion().toString());
        assertEquals("1.20.100", registry.findQueryProfile("testprofile:1.20").getId().getVersion().toString());
        assertEquals("1.20.100", registry.findQueryProfile("testprofile:1").getId().getVersion().toString());
        assertEquals("1.20.100", registry.findQueryProfile("testprofile").getId().getVersion().toString());
    }

    @Test
    void testNewsFE2() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newsfe2").compile();

        String queryString = "tiled?query=a&intl=tw&mode=adv&mode=adv";

        Query query = new Query(HttpRequest.createTestRequest(queryString, Method.GET), registry.getComponent("default"));
        assertEquals("news_adv", query.properties().listProperties().get("provider"));
        assertEquals("news_adv", query.properties().get("provider"));
    }

    @Test
    void testSourceProvider() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/sourceprovider").compile();

        String queryString = "tiled?query=india&queryProfile=myprofile&source.common.intl=tw&source.common.mode=adv";

        Query query = new Query(HttpRequest.createTestRequest(queryString, Method.GET), registry.getComponent("myprofile"));
        assertEquals("news", query.properties().listProperties().get("source.common.provider"));
        assertEquals("news", query.properties().get("source.common.provider"));
    }

    @Test
    void testNewsCase1() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase1").compile();

        Query query;
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),
                registry.getComponent("default"));
        assertEquals(0.0, query.properties().get("ranking.features.b"));
        assertEquals("0.0", query.properties().listProperties().get("ranking.features.b"));
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),
                registry.getComponent("default"));
        assertEquals(0.1, query.properties().get("ranking.features.b"));
        assertEquals("0.1", query.properties().listProperties().get("ranking.features.b"));
    }

    @Test
    void testNewsCase2() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase2").compile();

        Query query;
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),
                registry.getComponent("default"));
        assertEquals("0.0", query.properties().get("a.features.b"));
        assertEquals("0.0", query.properties().listProperties().get("a.features.b"));
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),
                registry.getComponent("default"));
        assertEquals("0.1", query.properties().get("a.features.b"));
        assertEquals("0.1", query.properties().listProperties().get("a.features.b"));
    }

    @Test
    void testNewsCase3() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase3").compile();

        Query query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),
                registry.getComponent("default"));
        assertEquals("0.0", query.properties().get("a.features"));
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),
                registry.getComponent("default"));
        assertEquals("0.1", query.properties().get("a.features"));
    }

    @Test
    void testNewsCase4() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/newscase4").compile();

        Query query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent", Method.GET),
                registry.getComponent("default"));
        assertEquals(0.0, query.properties().get("ranking.features.foo"));
        query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=parent&custid_2=child", Method.GET),
                registry.getComponent("default"));
        assertEquals(0.1, query.properties().get("ranking.features.foo"));
    }

    @Test
    void testVersionRefs() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/versionrefs").compile();

        Query query = new Query(HttpRequest.createTestRequest("?query=test", Method.GET), registry.getComponent("default"));
        assertEquals("MyProfile:1.0.2", query.properties().get("profile1.name"));
    }

    @Test
    void testRefOverride() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/refoverride").compile();

        {
            // Original reference
            Query query = new Query(HttpRequest.createTestRequest("?query=test", Method.GET),
                    registry.getComponent("default"));
            assertNull(query.properties().get("profileRef"));
            assertEquals("MyProfile1", query.properties().get("profileRef.name"));
            assertEquals("myProfile1Only", query.properties().get("profileRef.myProfile1Only"));
            assertNull(query.properties().get("profileRef.myProfile2Only"));
        }

        {
            // Overridden reference
            Query query = new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET), registry.getComponent("default"));
            assertNull(query.properties().get("profileRef"));
            assertEquals("MyProfile2", query.properties().get("profileRef.name"));
            assertEquals("myProfile2Only", query.properties().get("profileRef.myProfile2Only"));
            assertNull(query.properties().get("profileRef.myProfile1Only"));

            // later assignment
            query.properties().set("profileRef.name", "newName");
            assertEquals("newName", query.properties().get("profileRef.name"));
            // ...will not impact others
            query = new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET),
                    registry.getComponent("default"));
            assertEquals("MyProfile2", query.properties().get("profileRef.name"));
        }

    }

    @Test
    void testRefOverrideTyped() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/refoverridetyped").compile();

        {
            // Original reference
            Query query = new Query(HttpRequest.createTestRequest("?query=test", Method.GET), registry.getComponent("default"));
            assertNull(query.properties().get("profileRef"));
            assertEquals("MyProfile1", query.properties().get("profileRef.name"));
            assertEquals("myProfile1Only", query.properties().get("profileRef.myProfile1Only"));
            assertNull(query.properties().get("profileRef.myProfile2Only"));
        }

        {
            // Overridden reference
            Query query = new Query(HttpRequest.createTestRequest("?query=test&profileRef=MyProfile2", Method.GET), registry.getComponent("default"));
            assertNull(query.properties().get("profileRef"));
            assertEquals("MyProfile2", query.properties().get("profileRef.name"));
            assertEquals("myProfile2Only", query.properties().get("profileRef.myProfile2Only"));
            assertNull(query.properties().get("profileRef.myProfile1Only"));

            // later assignment
            query.properties().set("profileRef.name", "newName");
            assertEquals("newName", query.properties().get("profileRef.name"));
            // ...will not impact others
            query = new Query(HttpRequest.createTestRequest("?query=test&profileRef=ref:MyProfile2", Method.GET), registry.getComponent("default"));
            assertEquals("MyProfile2", query.properties().get("profileRef.name"));
        }

    }

    @Test
    void testAnonymousIdsAreStableBetweenImports() {
        QueryProfileRegistry registry1 = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/typedinheritance");
        var childIn1 = registry1.findQueryProfile("child");
        var childTypeIn1 = registry1.getType("childType");

        QueryProfileRegistry registry2 = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/typedinheritance");
        var childIn2 = registry2.findQueryProfile("child");
        var childTypeIn2 = registry2.getType("childType");

        assertEquals(((QueryProfile) childIn1.lookup("a", Map.of())).getId().stringValue(),
                ((QueryProfile) childIn2.lookup("a", Map.of())).getId().stringValue());

        assertEquals(childTypeIn1.getType("a").getId().stringValue(),
                childTypeIn2.getType("a").getId().stringValue());
    }

    @Test
    void testTensorTypes() {
        CompiledQueryProfileRegistry registry = new QueryProfileXMLReader().read("src/test/java/com/yahoo/search/query/profile/config/test/tensortypes").compile();

        QueryProfileType type1 = registry.getTypeRegistry().getComponent("type1");
        assertEquals(TensorType.fromSpec("tensor<float>(x[1])"),
                type1.getFieldType(CompoundName.from("ranking.features.query(tensor_1)")).asTensorType());
        assertNull(type1.getFieldType(CompoundName.from("ranking.features.query(tensor_2)")));
        assertNull(type1.getFieldType(CompoundName.from("ranking.features.query(tensor_3)")));
        assertEquals(TensorType.fromSpec("tensor(key{})"),
                type1.getFieldType(CompoundName.from("ranking.features.query(tensor_4)")).asTensorType());

        QueryProfileType type2 = registry.getTypeRegistry().getComponent("type2");
        assertNull(type2.getFieldType(CompoundName.from("ranking.features.query(tensor_1)")));
        assertEquals(TensorType.fromSpec("tensor<float>(x[2])"),
                type2.getFieldType(CompoundName.from("ranking.features.query(tensor_2)")).asTensorType());
        assertEquals(TensorType.fromSpec("tensor<float>(x[3])"),
                type2.getFieldType(CompoundName.from("ranking.features.query(tensor_3)")).asTensorType());

        Query queryProfile1 = new Query.Builder().setQueryProfile(registry.getComponent("profile1"))
                .setRequest("?query=test&ranking.features.query(tensor_1)=[1.200]")
                .build();
        assertEquals(Tensor.from("tensor<float>(x[1]):[1.2]"),
                queryProfile1.properties().get("ranking.features.query(tensor_1)"),
                "tensor_1 received as a tensor tensor");
        assertEquals(Tensor.from("tensor(key{}):{pre_key1_post:1.0}"),
                queryProfile1.properties().get("ranking.features.query(tensor_4)"),
                "tensor_4 contained in the profile is a tensor");

        Query queryProfile2 = new Query.Builder().setQueryProfile(registry.getComponent("profile2"))
                .setEmbedder(new MockEmbedder("text-to-embed",
                        Tensor.from("tensor(x[3]):[1, 2, 3]")))
                .setRequest("?query=test&ranking.features.query(tensor_1)=[1.200]")
                .build();
        assertEquals("[1.200]",
                queryProfile2.properties().get("ranking.features.query(tensor_1)"),
                "tensor_1 received as a string as it is not in type2");
        //assertEquals(Tensor.from("tensor(x[3]):[1, 2, 3]"),
        //             queryProfile2.properties().get("ranking.features.query(tensor_3)"));
    }

    private static final class MockEmbedder implements Embedder {

        private final String expectedText;
        private final Tensor tensorToReturn;

        public MockEmbedder(String expectedText, Tensor tensorToReturn) {
            this.expectedText = expectedText;
            this.tensorToReturn = tensorToReturn;
        }

        @Override
        public List<Integer> embed(String text, Context context) {
            fail("Unexpected call");
            return null;
        }

        @Override
        public Tensor embed(String text, Context context, TensorType tensorType) {
            assertEquals(expectedText, text);
            assertEquals(tensorToReturn.type(), tensorType);
            return tensorToReturn;
        }

    }
}
