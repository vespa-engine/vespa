// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.processing.request.Properties;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests untyped query profiles
 *
 * @author bratseth
 */
public class QueryProfileTestCase {

    @Test
    public void testBasics() {
        QueryProfile profile = new QueryProfile("test");
        profile.set("a","a-value", null);
        profile.set("b.c","b.c-value", null);
        profile.set("d.e.f","d.e.f-value", null);

        CompiledQueryProfile cprofile = profile.compile(null);

        assertEquals("a-value",cprofile.get("a"));
        assertEquals("b.c-value",cprofile.get("b.c"));
        assertEquals("d.e.f-value",cprofile.get("d.e.f"));

        assertNull(cprofile.get("nonexistent"));
        assertNull(cprofile.get("nested.nonexistent"));

        assertTrue(profile.lookup("b",null).getClass()==QueryProfile.class);
        assertTrue(profile.lookup("b",null).getClass()==QueryProfile.class);
    }

    /** Tests cloning, with wrappers used in production in place */
    @Test
    public void testCloning() {
        QueryProfile classProfile=new QueryProfile("test");
        classProfile.set("a","aValue", null);
        classProfile.set("b",3, null);

        Properties properties = new QueryProfileProperties(classProfile.compile(null));

        Properties propertiesClone=properties.clone();
        assertEquals("aValue",propertiesClone.get("a"));
        assertEquals(3,propertiesClone.get("b"));
        properties.set("a","aNewValue");
        assertEquals("aNewValue",properties.get("a"));
        assertEquals("aValue",propertiesClone.get("a"));
    }

    @Test
    public void testFreezing() {
        QueryProfile profile=new QueryProfile("test");
        profile.set("a","a-value", null);
        profile.set("b.c","b.c-value", null);
        profile.set("d.e.f","d.e.f-value", null);

        assertFalse(profile.isFrozen());
        assertEquals("a-value",profile.get("a"));

        profile.freeze();

        assertTrue(profile.isFrozen());
        assertTrue(((QueryProfile)profile.lookup("b",null)).isFrozen());
        assertTrue(((QueryProfile)profile.lookup("d.e",null)).isFrozen());

        try {
            profile.set("a","value", null);
            fail("Expected exception");
        }
        catch (IllegalStateException e) {
        }
    }

    private void assertSameObjects(CompiledQueryProfile profile, String path, List<String> expectedKeys) {
        Map<String, Object> subObjects = profile.listValues(path);
        assertEquals("Sub-objects list equal for path " + path, new HashSet<>(expectedKeys), subObjects.keySet());
        for(String key : expectedKeys) {
            assertEquals("Equal for key " + key, profile.get(key),subObjects.get(path + "." + key));
        }

    }

    @Test
    public void testGetSubObjects() {
        QueryProfile barn=new QueryProfile("barn");
        QueryProfile mor=new QueryProfile("mor");
        QueryProfile far=new QueryProfile("far");
        QueryProfile mormor=new QueryProfile("mormor");
        QueryProfile morfar=new QueryProfile("morfar");
        QueryProfile farfar=new QueryProfile("farfar");
        mor.addInherited(mormor);
        mor.addInherited(morfar);
        far.addInherited(farfar);
        barn.addInherited(mor);
        barn.addInherited(far);
        mormor.set("a.mormor","a.mormor", null);
        barn.set("a.barn","a.barn", null);
        mor.set("b.mor", "b.mor", null);
        far.set("b.far", "b.far", null);
        far.set("a.far","a.far", null);
        CompiledQueryProfile cbarn = barn.compile(null);

        assertSameObjects(cbarn, "a", Arrays.asList("mormor","far","barn"));

        assertEquals("b.mor", cbarn.get("b.mor"));
        assertEquals("b.far", cbarn.get("b.far"));
    }

    @Test
    public void testInheritance() {
        QueryProfile barn=new QueryProfile("barn");
        QueryProfile mor=new QueryProfile("mor");
        QueryProfile far=new QueryProfile("far");
        QueryProfile mormor=new QueryProfile("mormor");
        QueryProfile morfar=new QueryProfile("morfar");
        QueryProfile farfar=new QueryProfile("farfar");
        barn.addInherited(mor);
        barn.addInherited(far);
        mor.addInherited(mormor);
        mor.addInherited(morfar);
        far.addInherited(farfar);

        morfar.set("a","morfar-a", null);
        mormor.set("a","mormor-a", null);
        farfar.set("a","farfar-a", null);
        mor.set("a","mor-a", null);
        far.set("a","far-a", null);
        barn.set("a","barn-a", null);

        mormor.set("b","mormor-b", null);
        far.set("b","far-b", null);

        mor.set("c","mor-c", null);
        far.set("c","far-c", null);

        mor.set("d.a","mor-d.a", null);
        barn.set("d.b","barn-d.b", null);

        QueryProfile annetBarn=new QueryProfile("annetBarn");
        annetBarn.set("venn",barn, null);

        CompiledQueryProfile cbarn = barn.compile(null);
        CompiledQueryProfile cannetBarn = annetBarn.compile(null);

        assertEquals("barn-a", cbarn.get("a"));
        assertEquals("mormor-b", cbarn.get("b"));
        assertEquals("mor-c", cbarn.get("c"));

        assertEquals("barn-a", cannetBarn.get("venn.a"));
        assertEquals("mormor-b", cannetBarn.get("venn.b"));
        assertEquals("mor-c", cannetBarn.get("venn.c"));

        assertEquals("barn-d.b", cbarn.get("d.b"));
        assertEquals("mor-d.a", cbarn.get("d.a"));
    }

    @Test
    public void testInheritance2Level() {
        QueryProfile barn=new QueryProfile("barn");
        QueryProfile mor=new QueryProfile("mor");
        QueryProfile far=new QueryProfile("far");
        QueryProfile mormor=new QueryProfile("mormor");
        QueryProfile morfar=new QueryProfile("morfar");
        QueryProfile farfar=new QueryProfile("farfar");
        barn.addInherited(mor);
        barn.addInherited(far);
        mor.addInherited(mormor);
        mor.addInherited(morfar);
        far.addInherited(farfar);

        morfar.set("a.x","morfar-a", null);
        mormor.set("a.x","mormor-a", null);
        farfar.set("a.x","farfar-a", null);
        mor.set("a.x","mor-a", null);
        far.set("a.x","far-a", null);
        barn.set("a.x","barn-a", null);

        mormor.set("b.x","mormor-b", null);
        far.set("b.x","far-b", null);

        mor.set("c.x","mor-c", null);
        far.set("c.x","far-c", null);

        mor.set("d.a.x","mor-d.a", null);
        barn.set("d.b.x","barn-d.b", null);

        QueryProfile annetBarn=new QueryProfile("annetBarn");
        annetBarn.set("venn",barn, null);

        CompiledQueryProfile cbarn = barn.compile(null);
        CompiledQueryProfile cannetBarn = annetBarn.compile(null);

        assertEquals("barn-a", cbarn.get("a.x"));
        assertEquals("mormor-b", cbarn.get("b.x"));
        assertEquals("mor-c", cbarn.get("c.x"));

        assertEquals("barn-a", cannetBarn.get("venn.a.x"));
        assertEquals("mormor-b", cannetBarn.get("venn.b.x"));
        assertEquals("mor-c", cannetBarn.get("venn.c.x"));

        assertEquals("barn-d.b", cbarn.get("d.b.x"));
        assertEquals("mor-d.a", cbarn.get("d.a.x"));
    }

    @Test
    public void testInheritance3Level() {
        QueryProfile barn=new QueryProfile("barn");
        QueryProfile mor=new QueryProfile("mor");
        QueryProfile far=new QueryProfile("far");
        QueryProfile mormor=new QueryProfile("mormor");
        QueryProfile morfar=new QueryProfile("morfar");
        QueryProfile farfar=new QueryProfile("farfar");
        barn.addInherited(mor);
        barn.addInherited(far);
        mor.addInherited(mormor);
        mor.addInherited(morfar);
        far.addInherited(farfar);

        morfar.set("y.a.x","morfar-a", null);
        mormor.set("y.a.x","mormor-a", null);
        farfar.set("y.a.x","farfar-a", null);
        mor.set("y.a.x","mor-a", null);
        far.set("y.a.x","far-a", null);
        barn.set("y.a.x","barn-a", null);

        mormor.set("y.b.x","mormor-b", null);
        far.set("y.b.x","far-b", null);

        mor.set("y.c.x","mor-c", null);
        far.set("y.c.x","far-c", null);

        mor.set("y.d.a.x","mor-d.a", null);
        barn.set("y.d.b.x","barn-d.b", null);

        QueryProfile annetBarn=new QueryProfile("annetBarn");
        annetBarn.set("venn",barn, null);

        CompiledQueryProfile cbarn = barn.compile(null);
        CompiledQueryProfile cannetBarn = annetBarn.compile(null);

        assertEquals("barn-a", cbarn.get("y.a.x"));
        assertEquals("mormor-b", cbarn.get("y.b.x"));
        assertEquals("mor-c", cbarn.get("y.c.x"));

        assertEquals("barn-a", cannetBarn.get("venn.y.a.x"));
        assertEquals("mormor-b", cannetBarn.get("venn.y.b.x"));
        assertEquals("mor-c", cannetBarn.get("venn.y.c.x"));

        assertEquals("barn-d.b", cbarn.get("y.d.b.x"));
        assertEquals("mor-d.a", cbarn.get("y.d.a.x"));
    }

    @Test
    public void testListProperties() {
        QueryProfile barn=new QueryProfile("barn");
        QueryProfile mor=new QueryProfile("mor");
        QueryProfile far=new QueryProfile("far");
        QueryProfile mormor=new QueryProfile("mormor");
        QueryProfile morfar=new QueryProfile("morfar");
        QueryProfile farfar=new QueryProfile("farfar");
        barn.addInherited(mor);
        barn.addInherited(far);
        mor.addInherited(mormor);
        mor.addInherited(morfar);
        far.addInherited(farfar);

        morfar.set("a","morfar-a", null);
        morfar.set("model.b","morfar-model.b", null);
        mormor.set("a","mormor-a", null);
        mormor.set("model.b","mormor-model.b", null);
        farfar.set("a","farfar-a", null);
        mor.set("a","mor-a", null);
        far.set("a","far-a", null);
        barn.set("a","barn-a", null);
        mormor.set("b","mormor-b", null);
        far.set("b","far-b", null);
        mor.set("c","mor-c", null);
        far.set("c","far-c", null);

        CompiledQueryProfile cbarn = barn.compile(null);

        QueryProfileProperties properties = new QueryProfileProperties(cbarn);

        assertEquals("barn-a", cbarn.get("a"));
        assertEquals("mormor-b", cbarn.get("b"));

        Map<String, Object> rootMap = properties.listProperties();
        assertEquals("barn-a", rootMap.get("a"));
        assertEquals("mormor-b", rootMap.get("b"));
        assertEquals("mor-c", rootMap.get("c"));

        Map<String, Object> modelMap = properties.listProperties("model");
        assertEquals("mormor-model.b", modelMap.get("b"));

        QueryProfile annetBarn=new QueryProfile("annetBarn");
        annetBarn.set("venn", barn, (QueryProfileRegistry)null);
        CompiledQueryProfile cannetBarn = annetBarn.compile(null);

        Map<String, Object> annetBarnMap = new QueryProfileProperties(cannetBarn).listProperties();
        assertEquals("barn-a", annetBarnMap.get("venn.a"));
        assertEquals("mormor-b", annetBarnMap.get("venn.b"));
        assertEquals("mor-c", annetBarnMap.get("venn.c"));
        assertEquals("mormor-model.b", annetBarnMap.get("venn.model.b"));
    }

    /** Tests that dots are followed when setting overridability */
    @Test
    public void testInstanceOverridable() {
        QueryProfile profile=new QueryProfile("root/unoverridableIndex");
        profile.set("model.defaultIndex","default", null);
        profile.setOverridable("model.defaultIndex",false,null);

        assertFalse(profile.isDeclaredOverridable("model.defaultIndex",null).booleanValue());

        // Parameters should be ignored
        Query query = new Query(HttpRequest.createTestRequest("?model.defaultIndex=title", Method.GET), profile.compile(null));
        assertEquals("default",query.getModel().getDefaultIndex());

        // Parameters should be ignored
        query = new Query(HttpRequest.createTestRequest("?model.defaultIndex=title&model.language=de", Method.GET), profile.compile(null));
        assertEquals("default",query.getModel().getDefaultIndex());
        assertEquals("de",query.getModel().getLanguage().languageCode());
    }

    /** Tests that dots are followed when setting overridability...also with variants */
    @Test
    public void testInstanceOverridableWithVariants() {
        QueryProfile profile=new QueryProfile("root/unoverridableIndex");
        profile.setDimensions(new String[] {"x"});
        profile.set("model.defaultIndex","default", null);
        profile.setOverridable("model.defaultIndex",false,null);

        assertFalse(profile.isDeclaredOverridable("model.defaultIndex",null));

        // Parameters should be ignored
        Query query = new Query(HttpRequest.createTestRequest("?x=x1&model.defaultIndex=title", Method.GET), profile.compile(null));
        assertEquals("default",query.getModel().getDefaultIndex());

        // Parameters should be ignored
        query = new Query(HttpRequest.createTestRequest("?x=x1&model.default-index=title&model.language=de", Method.GET), profile.compile(null));
        assertEquals("default",query.getModel().getDefaultIndex());
        assertEquals("de",query.getModel().getLanguage().languageCode());
    }

    @Test
    public void testSimpleInstanceOverridableWithVariants1() {
        QueryProfile profile=new QueryProfile("test");
        profile.setDimensions(new String[] {"x"});
        profile.set("a","original", null);
        profile.setOverridable("a",false,null);

        assertFalse(profile.isDeclaredOverridable("a",null));

        Query query = new Query(HttpRequest.createTestRequest("?x=x1&a=overridden", Method.GET), profile.compile(null));
        assertEquals("original",query.properties().get("a"));
    }

    @Test
    public void testSimpleInstanceOverridableWithVariants2() {
        QueryProfile profile=new QueryProfile("test");
        profile.setDimensions(new String[] {"x"});
        profile.set("a","original",new String[] {"x1"}, null);
        profile.setOverridable("a",false,null);

        assertFalse(profile.isDeclaredOverridable("a",null));

        Query query = new Query(HttpRequest.createTestRequest("?x=x1&a=overridden", Method.GET), profile.compile(null));
        assertEquals("original",query.properties().get("a"));
    }

    /** Tests having both an explicit reference and an override */
    @Test
    public void testExplicitReferenceOverride() {
        QueryProfile a1=new QueryProfile("a1");
        a1.set("b","a1.b", null);
        QueryProfile profile=new QueryProfile("test");
        profile.set("a",a1, null);
        profile.set("a.b","a.b", null);
        assertEquals("a.b",profile.compile(null).get("a.b"));
    }

    @Test
    public void testSettingNonLeaf1() {
        QueryProfile p=new QueryProfile("test");
        p.set("a","a-value", null);
        p.set("a.b","a.b-value", null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));
        assertEquals("a-value", cp.get("a"));
        assertEquals("a.b-value", cp.get("a.b"));
    }

    @Test
    public void testSettingNonLeaf2() {
        QueryProfile p=new QueryProfile("test");
        p.set("a.b","a.b-value", null);
        p.set("a","a-value", null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));
        assertEquals("a-value", cp.get("a"));
        assertEquals("a.b-value", cp.get("a.b"));
    }

    @Test
    public void testSettingNonLeaf3a() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a.b","a.b-value", null);
        p.set("a","a-value",new String[] {"x1"}, null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));

        assertNull(p.get("a"));
        assertEquals("a.b-value", cp.get("a.b"));
        assertEquals("a-value", cp.get("a", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
        assertEquals("a.b-value", cp.get("a.b", new String[] {"x1"}));
    }

    @Test
    public void testSettingNonLeaf3b() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a","a-value",new String[] {"x1"}, null);
        p.set("a.b","a.b-value", null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));

        assertNull(cp.get("a"));
        assertEquals("a.b-value", cp.get("a.b"));
        assertEquals("a-value", cp.get("a", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
        assertEquals("a.b-value", cp.get("a.b",new String[] {"x1"}));
    }

    @Test
    public void testSettingNonLeaf4a() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a.b","a.b-value",new String[] {"x1"}, null);
        p.set("a","a-value", null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));

        assertEquals("a-value", cp.get("a"));
        assertNull(cp.get("a.b"));
        assertEquals("a-value", cp.get("a",new String[] {"x1"}));
        assertEquals("a.b-value", cp.get("a.b", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
    }

    public void testSettingNonLeaf4b() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a","a-value", (QueryProfileRegistry)null);
        p.set("a.b","a.b-value",new String[] {"x1"}, null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));

        assertEquals("a-value", cp.get("a"));
        assertNull(cp.get("a.b"));
        assertEquals("a-value", cp.get("a",new String[] {"x1"}));
        assertEquals("a.b-value", cp.get("a.b", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
    }

    @Test
    public void testSettingNonLeaf5() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x"});
        p.set("a.b","a.b-value",new String[] {"x1"}, null);
        p.set("a","a-value",new String[] {"x1"}, null);

        QueryProfileProperties cp = new QueryProfileProperties(p.compile(null));

        assertNull(cp.get("a"));
        assertNull(cp.get("a.b"));
        assertEquals("a-value", cp.get("a", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
        assertEquals("a.b-value", cp.get("a.b", QueryProfileVariantsTestCase.toMap(p, new String[] {"x1"})));
    }

    @Test
    public void testListingWithNonLeafs() {
        QueryProfile p=new QueryProfile("test");
        p.set("a","a-value", null);
        p.set("a.b","a.b-value", null);
        Map<String,Object> values = p.compile(null).listValues("a");
        assertEquals(1,values.size());
        assertEquals("a.b-value",values.get("b"));
    }

    @Test
    public void testRankTypeNames() {
         QueryProfile p=new QueryProfile("test");
         p.set("a.$b","foo", null);
         p.set("a.query(b)","bar", null);
         p.set("a.b.default-index","fuu", null);
         CompiledQueryProfile cp = p.compile(null);

         assertEquals("foo", cp.get("a.$b"));
         assertEquals("bar", cp.get("a.query(b)"));
         assertEquals("fuu", cp.get("a.b.default-index"));

         Map<String,Object> p1 = cp.listValues("");
         assertEquals("foo", p1.get("a.$b"));
         assertEquals("bar", p1.get("a.query(b)"));
         assertEquals("fuu", p1.get("a.b.default-index"));

         Map<String,Object> p2 = cp.listValues("a");
         assertEquals("foo", p2.get("$b"));
         assertEquals("bar", p2.get("query(b)"));
         assertEquals("fuu", p2.get("b.default-index"));
    }

    @Test
    public void testQueryProfileInlineValueReassignment() {
        QueryProfile p=new QueryProfile("test");
        p.set("source.rel.params.query","%{model.queryString}", null);
        p.freeze();
        Query q = new Query(HttpRequest.createTestRequest("?query=foo", Method.GET), p.compile(null));
        assertEquals("foo",q.properties().get("source.rel.params.query"));
        assertEquals("foo",q.properties().listProperties().get("source.rel.params.query"));
        q.getModel().setQueryString("bar");
        assertEquals("bar",q.properties().get("source.rel.params.query"));
        assertEquals("foo",q.properties().listProperties().get("source.rel.params.query")); // Is still foo because model variables are not supported with the list function
    }

    @Test
    public void testQueryProfileInlineValueReassignmentSimpleName() {
        QueryProfile p=new QueryProfile("test");
        p.set("key","%{model.queryString}", null);
        p.freeze();
        Query q = new Query(HttpRequest.createTestRequest("?query=foo", Method.GET), p.compile(null));
        assertEquals("foo",q.properties().get("key"));
        assertEquals("foo",q.properties().listProperties().get("key"));
        q.getModel().setQueryString("bar");
        assertEquals("bar",q.properties().get("key"));
        assertEquals("foo",q.properties().listProperties().get("key")); // Is still bar because model variables are not supported with the list function
    }

    @Test
    public void testQueryProfileInlineValueReassignmentSimpleNameGenericProperty() {
        QueryProfile p=new QueryProfile("test");
        p.set("key","%{value}", null);
        p.freeze();
        Query q = new Query(HttpRequest.createTestRequest("?query=test&value=foo", Method.GET), p.compile(null));
        assertEquals("foo",q.properties().get("key"));
        assertEquals("foo",q.properties().listProperties().get("key"));
        q.properties().set("value","bar");
        assertEquals("bar",q.properties().get("key"));
        assertEquals("bar",q.properties().listProperties().get("key"));
    }

    @Test
    public void testQueryProfileModelValueListing() {
        QueryProfile p=new QueryProfile("test");
        p.freeze();
        Query q = new Query(HttpRequest.createTestRequest("?query=bar", Method.GET), p.compile(null));
        assertEquals("bar",q.properties().get("model.queryString"));
        assertEquals("bar",q.properties().listProperties().get("model.queryString"));
        q.getModel().setQueryString("baz");
        assertEquals("baz",q.properties().get("model.queryString"));
        assertEquals("bar",q.properties().listProperties().get("model.queryString")); // Is still bar because model variables are not supported with the list function
    }

    @Test
    public void testEmptyBoolean() {
        QueryProfile p=new QueryProfile("test");
        p.setDimensions(new String[] {"x","y"});
        p.set("clustering.something","bar", null);
        p.set("clustering.something","bar", new String[] {"x1","y1"}, null);
        p.freeze();
        Query q = new Query(HttpRequest.createTestRequest("?x=x1&y=y1&query=bar&clustering.timeline.kano=tur&" +
                                                          "clustering.enable=true&clustering.timeline.bucketspec=-" +
                                                          "7d/3h&clustering.timeline.tophit=false&clustering.timeli" +
                                                          "ne=true", Method.GET),p.compile(null));
        assertEquals(true,q.properties().getBoolean("clustering.timeline",false));
    }

}
