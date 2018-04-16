// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.Query;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.profile.BackedOverridableQueryProfile;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class QueryProfileVariantsTestCase {

    @Test
    public void testSimple() {
        QueryProfile profile=new QueryProfile("a");
        profile.set("a","a.deflt", null);
        profile.setDimensions(new String[] {"x","y","z"});
        profile.set("a","a.1.*.*",new String[] {"x1",null,null}, null);
        profile.set("a","a.1.*.1",new String[] {"x1",null,"z1"}, null);
        profile.set("a","a.1.*.5",new String[] {"x1",null,"z5"}, null);
        profile.set("a","a.1.1.*",new String[] {"x1","y1",null}, null);
        profile.set("a","a.1.5.*",new String[] {"x1","y5",null}, null);
        profile.set("a","a.1.1.1",new String[] {"x1","y1","z1"}, null);
        profile.set("a","a.2.1.1",new String[] {"x2","y1","z1"}, null);
        profile.set("a","a.1.2.2",new String[] {"x1","y2","z2"}, null);
        profile.set("a","a.1.2.3",new String[] {"x1","y2","z3"}, null);
        profile.set("a","a.2.*.*",new String[] {"x2"          }, null); // Same as ,null,null
        CompiledQueryProfile cprofile = profile.compile(null);

        // Perfect matches
        assertGet("a.deflt","a",new String[] {null,null,null}, profile, cprofile);
        assertGet("a.1.*.*","a",new String[] {"x1",null,null}, profile, cprofile);
        assertGet("a.1.1.*","a",new String[] {"x1","y1",null}, profile, cprofile);
        assertGet("a.1.5.*","a",new String[] {"x1","y5",null}, profile, cprofile);
        assertGet("a.1.*.1","a",new String[] {"x1",null,"z1"}, profile, cprofile);
        assertGet("a.1.*.5","a",new String[] {"x1",null,"z5"}, profile, cprofile);
        assertGet("a.1.1.1","a",new String[] {"x1","y1","z1"}, profile, cprofile);
        assertGet("a.2.1.1","a",new String[] {"x2","y1","z1"}, profile, cprofile);
        assertGet("a.1.2.2","a",new String[] {"x1","y2","z2"}, profile, cprofile);
        assertGet("a.1.2.3","a",new String[] {"x1","y2","z3"}, profile, cprofile);
        assertGet("a.2.*.*","a",new String[] {"x2",null,null}, profile, cprofile);

        // Wildcard matches
        assertGet("a.deflt","a",new String[] {"x?","y?","z?"}, profile, cprofile);
        assertGet("a.deflt","a",new String[] {"x?","y1","z1"}, profile, cprofile);
        assertGet("a.1.*.*","a",new String[] {"x1","y?","z?"}, profile, cprofile);
        assertGet("a.1.*.*","a",new String[] {"x1","y?","z?"}, profile, cprofile);
        assertGet("a.1.1.*","a",new String[] {"x1","y1","z?"}, profile, cprofile);
        assertGet("a.1.*.1","a",new String[] {"x1","y?","z1"}, profile, cprofile);
        assertGet("a.1.5.*","a",new String[] {"x1","y5","z?"}, profile, cprofile);
        assertGet("a.1.*.5","a",new String[] {"x1","y?","z5"}, profile, cprofile);
        assertGet("a.1.5.*","a",new String[] {"x1","y5","z5"}, profile, cprofile); // Left dimension gets precedence
        assertGet("a.2.*.*","a",new String[] {"x2","y?","z?"}, profile, cprofile);
    }

    @Test
    public void testVariantsOfInlineCompound() {
        QueryProfile profile=new QueryProfile("test");
        profile.setDimensions(new String[] {"x"});
        profile.set("a.b","a.b", null);
        profile.set("a.b","a.b.x1",new String[] {"x1"}, null);
        profile.set("a.b","a.b.x2",new String[] {"x2"}, null);

        CompiledQueryProfile cprofile = profile.compile(null);

        assertEquals("a.b",cprofile.get("a.b"));
        assertEquals("a.b.x1",cprofile.get("a.b", toMap("x=x1")));
        assertEquals("a.b.x2",cprofile.get("a.b", toMap("x=x2")));
    }

    @Test
    public void testVariantsOfExplicitCompound() {
        QueryProfile a1=new QueryProfile("a1");
        a1.set("b","a.b", null);

        QueryProfile profile=new QueryProfile("test");
        profile.setDimensions(new String[] {"x"});
        profile.set("a",a1, null);
        profile.set("a.b","a.b.x1",new String[] {"x1"}, null);
        profile.set("a.b","a.b.x2",new String[] {"x2"}, null);

        CompiledQueryProfile cprofile = profile.compile(null);

        assertEquals("a.b",cprofile.get("a.b"));
        assertEquals("a.b.x1",cprofile.get("a.b", toMap("x=x1")));
        assertEquals("a.b.x2",cprofile.get("a.b", toMap("x=x2")));
    }

    @Test
    public void testCompound() {
        // Configuration phase

        QueryProfile profile=new QueryProfile("test");
        profile.setDimensions(new String[] {"x","y"});

        QueryProfile a1=new QueryProfile("a1");
        a1.set("b","a1.b.default", null);
        a1.set("c","a1.c.default", null);
        a1.set("d","a1.d.default", null);
        a1.set("e","a1.e.default", null);

        QueryProfile a2=new QueryProfile("a2");
        a2.set("b","a2.b.default", null);
        a2.set("c","a2.c.default", null);
        a2.set("d","a2.d.default", null);
        a2.set("e","a2.e.default", null);

        profile.set("a",a1, null); // Must set profile references before overrides
        profile.set("a.b","a.b.default-override", null);
        profile.set("a.c","a.c.default-override", null);
        profile.set("a.d","a.d.default-override", null);
        profile.set("a.g","a.g.default-override", null);

        String[] d1=new String[] { "x1","y1" };
        profile.set("a",a1,d1, null);
        profile.set("a.b","x1.y1.a.b.default-override",d1, null);
        profile.set("a.c","x1.y1.a.c.default-override",d1, null);
        profile.set("a.g","x1.y1.a.g.default-override",d1, null); // This value is never manifest because the runtime override overrides all variants

        String[] d2=new String[] { "x1","y2" };
        profile.set("a.b","x1.y2.a.b.default-override",d2, null);
        profile.set("a.c","x1.y2.a.c.default-override",d2, null);

        String[] d3=new String[] { "x2","y1" };
        profile.set("a",a2,d3, null);
        profile.set("a.b","x2.y1.a.b.default-override",d3, null);
        profile.set("a.c","x2.y1.a.c.default-override",d3, null);


        // Runtime phase - four simultaneous requests using different variants makes their own overrides
        QueryProfileProperties defaultRuntimeProfile = new QueryProfileProperties(profile.compile(null));
        defaultRuntimeProfile.set("a.f", "a.f.runtime-override");
        defaultRuntimeProfile.set("a.g", "a.g.runtime-override");

        QueryProfileProperties d1RuntimeProfile = new QueryProfileProperties(profile.compile(null));
        d1RuntimeProfile.set("a.f", "a.f.d1.runtime-override", toMap("x=x1", "y=y1"));
        d1RuntimeProfile.set("a.g", "a.g.d1.runtime-override", toMap("x=x1", "y=y1"));

        QueryProfileProperties d2RuntimeProfile = new QueryProfileProperties(profile.compile(null));
        d2RuntimeProfile.set("a.f", "a.f.d2.runtime-override",toMap("x=x1", "y=y2"));
        d2RuntimeProfile.set("a.g", "a.g.d2.runtime-override",toMap("x=x1", "y=y2"));

        QueryProfileProperties d3RuntimeProfile = new QueryProfileProperties(profile.compile(null));
        d3RuntimeProfile.set("a.f", "a.f.d3.runtime-override", toMap("x=x2", "y=y1"));
        d3RuntimeProfile.set("a.g", "a.g.d3.runtime-override", toMap("x=x2", "y=y1"));

        // Lookups
        assertEquals("a.b.default-override", defaultRuntimeProfile.get("a.b"));
        assertEquals("a.c.default-override", defaultRuntimeProfile.get("a.c"));
        assertEquals("a.d.default-override", defaultRuntimeProfile.get("a.d"));
        assertEquals("a1.e.default",         defaultRuntimeProfile.get("a.e"));
        assertEquals("a.f.runtime-override", defaultRuntimeProfile.get("a.f"));
        assertEquals("a.g.runtime-override", defaultRuntimeProfile.get("a.g"));

        assertEquals("x1.y1.a.b.default-override", d1RuntimeProfile.get("a.b", toMap("x=x1", "y=y1")));
        assertEquals("x1.y1.a.c.default-override", d1RuntimeProfile.get("a.c", toMap("x=x1", "y=y1")));
        assertEquals("a1.d.default",               d1RuntimeProfile.get("a.d", toMap("x=x1", "y=y1")));
        assertEquals("a1.e.default",               d1RuntimeProfile.get("a.e", toMap("x=x1", "y=y1")));
        assertEquals("a.f.d1.runtime-override",    d1RuntimeProfile.get("a.f", toMap("x=x1", "y=y1")));
        assertEquals("a.g.d1.runtime-override",    d1RuntimeProfile.get("a.g", toMap("x=x1", "y=y1")));

        assertEquals("x1.y2.a.b.default-override", d2RuntimeProfile.get("a.b", toMap("x=x1", "y=y2")));
        assertEquals("x1.y2.a.c.default-override", d2RuntimeProfile.get("a.c", toMap("x=x1", "y=y2")));
        assertEquals("a.d.default-override",       d2RuntimeProfile.get("a.d", toMap("x=x1", "y=y2"))); // Because this variant does not itself refer to a
        assertEquals("a1.e.default",               d2RuntimeProfile.get("a.e", toMap("x=x1", "y=y2")));
        assertEquals("a.f.d2.runtime-override",    d2RuntimeProfile.get("a.f", toMap("x=x1", "y=y2")));
        assertEquals("a.g.d2.runtime-override",    d2RuntimeProfile.get("a.g", toMap("x=x1", "y=y2")));

        assertEquals("x2.y1.a.b.default-override", d3RuntimeProfile.get("a.b", toMap("x=x2", "y=y1")));
        assertEquals("x2.y1.a.c.default-override", d3RuntimeProfile.get("a.c", toMap("x=x2", "y=y1")));
        assertEquals("a2.d.default",               d3RuntimeProfile.get("a.d", toMap("x=x2", "y=y1")));
        assertEquals("a2.e.default",               d3RuntimeProfile.get("a.e", toMap("x=x2", "y=y1")));
        assertEquals("a.f.d3.runtime-override",    d3RuntimeProfile.get("a.f", toMap("x=x2", "y=y1")));
        assertEquals("a.g.d3.runtime-override",    d3RuntimeProfile.get("a.g", toMap("x=x2", "y=y1")));
    }

    @Test
    public void testVariantNotInBase() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});
        test.set("InX1Only","x1",new String[] {"x1"}, null);

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals("x1",ctest.get("InX1Only", toMap("x=x1")));
        assertEquals(null,ctest.get("InX1Only", toMap("x=x2")));
        assertEquals(null,ctest.get("InX1Only"));
    }

    @Test
    public void testVariantNotInBaseSpaceVariantValue() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});
        test.set("InX1Only","x1",new String[] {"x 1"}, null);

        CompiledQueryProfile ctest = test.compile(null);

        assertEquals("x1",ctest.get("InX1Only", toMap("x=x 1")));
        assertEquals(null,ctest.get("InX1Only", toMap("x=x 2")));
        assertEquals(null,ctest.get("InX1Only"));
    }

    @Test
    public void testDimensionsInSuperType() {
        QueryProfile parent=new QueryProfile("parent");
        parent.setDimensions(new String[] {"x","y"});
        QueryProfile child=new QueryProfile("child");
        child.addInherited(parent);
        child.set("a","a.default", null);
        child.set("a","a.x1.y1",new String[] {"x1","y1"}, null);
        child.set("a","a.x1.y2",new String[] {"x1","y2"}, null);

        CompiledQueryProfile cchild = child.compile(null);

        assertEquals("a.default",cchild.get("a"));
        assertEquals("a.x1.y1",cchild.get("a", toMap("x=x1","y=y1")));
        assertEquals("a.x1.y2",cchild.get("a", toMap("x=x1","y=y2")));
    }

    @Test
    public void testDimensionsInSuperTypeRuntime() {
        QueryProfile parent=new QueryProfile("parent");
        parent.setDimensions(new String[] {"x","y"});
        QueryProfile child=new QueryProfile("child");
        child.addInherited(parent);
        child.set("a","a.default", null);
        child.set("a", "a.x1.y1", new String[]{"x1", "y1"}, null);
        child.set("a", "a.x1.y2", new String[]{"x1", "y2"}, null);
        Properties overridable=new QueryProfileProperties(child.compile(null));

        assertEquals("a.default", child.get("a"));
        assertEquals("a.x1.y1", overridable.get("a", toMap("x=x1", "y=y1")));
        assertEquals("a.x1.y2", overridable.get("a", toMap("x=x1", "y=y2")));
    }

    @Test
    public void testVariantsAreResolvedBeforeInheritance() {
        QueryProfile parent=new QueryProfile("parent");
        parent.setDimensions(new String[] {"x","y"});
        parent.set("a","p.a.default", null);
        parent.set("a","p.a.x1.y1",new String[] {"x1","y1"}, null);
        parent.set("a","p.a.x1.y2",new String[] {"x1","y2"}, null);
        parent.set("b","p.b.default", null);
        parent.set("b","p.b.x1.y1",new String[] {"x1","y1"}, null);
        parent.set("b","p.b.x1.y2",new String[] {"x1","y2"}, null);
        QueryProfile child=new QueryProfile("child");
        child.setDimensions(new String[] {"x","y"});
        child.addInherited(parent);
        child.set("a","c.a.default", null);
        child.set("a","c.a.x1.y1",new String[] {"x1","y1"}, null);

        CompiledQueryProfile cchild = child.compile(null);
        assertEquals("c.a.default",cchild.get("a"));
        assertEquals("c.a.x1.y1",cchild.get("a", toMap("x=x1", "y=y1")));
        assertEquals("c.a.default",cchild.get("a", toMap("x=x1", "y=y2")));
        assertEquals("p.b.default",cchild.get("b"));
        assertEquals("p.b.x1.y1",cchild.get("b", toMap("x=x1", "y=y1")));
        assertEquals("p.b.x1.y2",cchild.get("b", toMap("x=x1", "y=y2")));
    }

    @Test
    public void testVariantsAreResolvedBeforeInheritanceSimplified() {
        QueryProfile parent=new QueryProfile("parent");
        parent.setDimensions(new String[] {"x","y"});
        parent.set("a","p.a.x1.y2",new String[] {"x1","y2"}, null);

        QueryProfile child=new QueryProfile("child");
        child.setDimensions(new String[] {"x","y"});
        child.addInherited(parent);
        child.set("a","c.a.default", null);

        assertEquals("c.a.default",child.compile(null).get("a", toMap("x=x1", "y=y2")));
    }

    @Test
    public void testVariantInheritance() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x","y"});
        QueryProfile defaultParent=new QueryProfile("defaultParent");
        defaultParent.set("a","a-default", null);
        QueryProfile x1Parent=new QueryProfile("x1Parent");
        x1Parent.set("a","a-x1", null);
        x1Parent.set("d","d-x1", null);
        x1Parent.set("e","e-x1", null);
        QueryProfile x1y1Parent=new QueryProfile("x1y1Parent");
        x1y1Parent.set("a","a-x1y1", null);
        QueryProfile x1y2Parent=new QueryProfile("x1y2Parent");
        x1y2Parent.set("a","a-x1y2", null);
        x1y2Parent.set("b","b-x1y2", null);
        x1y2Parent.set("c","c-x1y2", null);
        test.addInherited(defaultParent);
        test.addInherited(x1Parent,new String[] {"x1"});
        test.addInherited(x1y1Parent,new String[] {"x1","y1"});
        test.addInherited(x1y2Parent,new String[] {"x1","y2"});
        test.set("c","c-x1",new String[] {"x1"}, null);
        test.set("e","e-x1y2",new String[] {"x1","y2"}, null);

        CompiledQueryProfile ctest = test.compile(null);

        assertEquals("a-default",ctest.get("a"));
        assertEquals("a-x1",ctest.get("a", toMap("x=x1")));
        assertEquals("a-x1y1",ctest.get("a", toMap("x=x1", "y=y1")));
        assertEquals("a-x1y2",ctest.get("a", toMap("x=x1", "y=y2")));

        assertEquals(null,ctest.get("b"));
        assertEquals(null,ctest.get("b", toMap("x=x1")));
        assertEquals(null,ctest.get("b", toMap("x=x1", "y=y1")));
        assertEquals("b-x1y2",ctest.get("b", toMap("x=x1", "y=y2")));

        assertEquals(null,ctest.get("c"));
        assertEquals("c-x1",ctest.get("c", toMap("x=x1")));
        assertEquals("c-x1",ctest.get("c", toMap("x=x1", "y=y1")));
        assertEquals("c-x1y2",ctest.get("c", toMap("x=x1", "y=y2")));

        assertEquals(null,ctest.get("d"));
        assertEquals("d-x1",ctest.get("d", toMap("x=x1")));

        assertEquals("d-x1",ctest.get("d", toMap("x=x1", "y=y1")));
        assertEquals("d-x1",ctest.get("d", toMap("x=x1", "y=y2")));

        assertEquals(null,ctest.get("d"));
        assertEquals("e-x1",ctest.get("e", toMap("x=x1")));
        assertEquals("e-x1",ctest.get("e", toMap("x=x1", "y=y1")));
        assertEquals("e-x1y2",ctest.get("e", toMap("x=x1", "y=y2")));
    }

    @Test
    public void testVariantInheritanceSimplified() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x","y"});
        QueryProfile x1y2Parent=new QueryProfile("x1y2Parent");
        x1y2Parent.set("c","c-x1y2", null);
        test.addInherited(x1y2Parent,new String[] {"x1","y2"});
        test.set("c","c-x1",new String[] {"x1"}, null);

        CompiledQueryProfile ctest = test.compile(null);

        assertEquals(null,ctest.get("c"));
        assertEquals("c-x1",ctest.get("c", toMap("x=x1")));
        assertEquals("c-x1", ctest.get("c", toMap("x=x1", "y=y1")));
        assertEquals("c-x1y2",ctest.get("c", toMap("x=x1", "y=y2")));
    }

    @Test
    public void testVariantInheritanceWithCompoundReferences() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});
        test.set("a.b","default-a.b", null);

        QueryProfile ac=new QueryProfile("ac");
        ac.set("a.c","referenced-a.c", null);
        test.addInherited(ac,new String[] {"x1"});
        test.set("a.b","x1-a.b",new String[] {"x1"}, null);

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals("Basic functionality","default-a.b",ctest.get("a.b"));
        assertEquals("Inherited variance reference works","referenced-a.c",ctest.get("a.c", toMap("x=x1")));
        assertEquals("Inherited variance reference overriding works","x1-a.b",ctest.get("a.b", toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceWithTwoLevelCompoundReferencesVariantAtFirstLevel() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});
        test.set("o.a.b","default-a.b", null);

        QueryProfile ac=new QueryProfile("ac");
        ac.set("o.a.c","referenced-a.c", null);
        test.addInherited(ac,new String[] {"x1"});
        test.set("o.a.b","x1-a.b",new String[] {"x1"}, null);

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals("Basic functionality","default-a.b",ctest.get("o.a.b"));
        assertEquals("Inherited variance reference works","referenced-a.c",ctest.get("o.a.c", toMap("x=x1")));
        assertEquals("Inherited variance reference overriding works","x1-a.b",ctest.get("o.a.b", toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceWithTwoLevelCompoundReferencesVariantAtSecondLevel() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});

        QueryProfile ac=new QueryProfile("ac");
        ac.set("a.c","referenced-a.c", null);
        test.addInherited(ac,new String[] {"x1"});
        test.set("a.b","x1-a.b",new String[] {"x1"}, null);

        QueryProfile top=new QueryProfile("top");
        top.set("o.a.b","default-a.b", null);
        top.set("o",test, null);

        CompiledQueryProfile ctop = top.compile(null);
        assertEquals("Basic functionality","default-a.b",ctop.get("o.a.b"));
        assertEquals("Inherited variance reference works","referenced-a.c",ctop.get("o.a.c", toMap("x=x1")));
        assertEquals("Inherited variance reference does not override value set in referent","default-a.b",ctop.get("o.a.b", toMap("x=x1"))); // Note: Changed from x1-a.b in 4.2.3
    }

    @Test
    public void testVariantInheritanceOverridesBaseInheritance1() {
        QueryProfile test=new QueryProfile("test");
        QueryProfile baseInherited=new QueryProfile("baseInherited");
        baseInherited.set("a.b","baseInherited-a.b", null);
        QueryProfile variantInherited=new QueryProfile("variantInherited");
        variantInherited.set("a.b","variantInherited-a.b", null);
        test.setDimensions(new String[] {"x"});
        test.addInherited(baseInherited);
        test.addInherited(variantInherited,new String[] {"x1"});

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals("baseInherited-a.b",ctest.get("a.b"));
        assertEquals("variantInherited-a.b",ctest.get("a.b",toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceOverridesBaseInheritance2() {
        QueryProfile test=new QueryProfile("test");
        QueryProfile baseInherited=new QueryProfile("baseInherited");
        baseInherited.set("a.b","baseInherited-a.b", null);
        QueryProfile variantInherited=new QueryProfile("variantInherited");
        variantInherited.set("a.b","variantInherited-a.b", null);
        test.setDimensions(new String[] {"x"});
        test.addInherited(baseInherited);
        test.addInherited(variantInherited,new String[] {"x1"});
        test.set("a.c","variant-a.c",new String[] {"x1"}, null);

        CompiledQueryProfile ctest = test.compile(null);
        assertEquals("baseInherited-a.b",ctest.get("a.b"));
        assertEquals("variantInherited-a.b",ctest.get("a.b", toMap("x=x1")));
        assertEquals("variant-a.c",ctest.get("a.c", toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceOverridesBaseInheritanceComplex() {
        QueryProfile defaultQP=new QueryProfile("default");
        defaultQP.set("model.defaultIndex","title", null);

        QueryProfile root=new QueryProfile("root");
        root.addInherited(defaultQP);
        root.set("model.defaultIndex","default", null);

        QueryProfile querybest=new QueryProfile("querybest");
        querybest.set("defaultIndex","title", null);
        querybest.set("queryString","best", null);

        QueryProfile multi=new QueryProfile("multi");
        multi.setDimensions(new String[] {"x"});
        multi.addInherited(defaultQP);
        multi.set("model",querybest, null);
        multi.addInherited(root,new String[] {"x1"});
        multi.set("model.queryString","love",new String[] {"x1"}, null);

        // Rumtimize
        defaultQP.freeze();
        root.freeze();
        querybest.freeze();
        multi.freeze();
        Properties runtime = new QueryProfileProperties(multi.compile(null));

        assertEquals("default",runtime.get("model.defaultIndex", toMap("x=x1")));
        assertEquals("love",runtime.get("model.queryString", toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceOverridesBaseInheritanceComplexSimplified() {
        QueryProfile root=new QueryProfile("root");
        root.set("model.defaultIndex","default", null);

        QueryProfile multi=new QueryProfile("multi");
        multi.setDimensions(new String[] {"x"});
        multi.set("model.defaultIndex","title", null);
        multi.addInherited(root,new String[] {"x1"});

        assertEquals("default",multi.compile(null).get("model.defaultIndex", toMap("x=x1")));
    }

    @Test
    public void testVariantInheritanceOverridesBaseInheritanceMixed() {
        QueryProfile root=new QueryProfile("root");
        root.set("model.defaultIndex","default", null);

        QueryProfile multi=new QueryProfile("multi");
        multi.setDimensions(new String[] {"x"});
        multi.set("model.defaultIndex","title", null);
        multi.set("model.queryString","modelQuery", null);
        multi.addInherited(root,new String[] {"x1"});
        multi.set("model.queryString","modelVariantQuery",new String[] {"x1"}, null);

        CompiledQueryProfile cmulti = multi.compile(null);
        assertEquals("default",cmulti.get("model.defaultIndex", toMap("x=x1")));
        assertEquals("modelVariantQuery",cmulti.get("model.queryString", toMap("x=x1")));
    }

    @Test
    public void testListVariantPropertiesNoCompounds() {
        QueryProfile parent1=new QueryProfile("parent1");
        parent1.set("a","parent1-a", null); // Defined everywhere
        parent1.set("b","parent1-b", null); // Defined everywhere, but no variants
        parent1.set("c","parent1-c", null); // Defined in both parents only

        QueryProfile parent2=new QueryProfile("parent2");
        parent2.set("a","parent2-a", null);
        parent2.set("b","parent2-b", null);
        parent2.set("c","parent2-c", null);
        parent2.set("d","parent2-d", null); // Defined in second parent only

        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x","y"});
        main.addInherited(parent1);
        main.addInherited(parent2);
        main.set("a","main-a", null);
        main.set("a","main-a-x1",new String[] {"x1"}, null);
        main.set("e","main-e-x1",new String[] {"x1"}, null); // Defined in two variants only
        main.set("f","main-f-x1",new String[] {"x1"}, null); // Defined in one variants only
        main.set("a","main-a-x1.y1",new String[] {"x1","y1"}, null);
        main.set("a","main-a-x1.y2",new String[] {"x1","y2"}, null);
        main.set("e","main-e-x1.y2",new String[] {"x1","y2"}, null);
        main.set("g","main-g-x1.y2",new String[] {"x1","y2"}, null); // Defined in one variant only
        main.set("b","main-b", null);

        QueryProfile inheritedVariant1=new QueryProfile("inheritedVariant1");
        inheritedVariant1.set("a","inheritedVariant1-a", null);
        inheritedVariant1.set("h","inheritedVariant1-h", null); // Only defined in two inherited variants

        QueryProfile inheritedVariant2=new QueryProfile("inheritedVariant2");
        inheritedVariant2.set("a","inheritedVariant2-a", null);
        inheritedVariant2.set("h","inheritedVariant2-h", null); // Only defined in two inherited variants
        inheritedVariant2.set("i","inheritedVariant2-i", null); // Only defined in one inherited variant

        QueryProfile inheritedVariant3=new QueryProfile("inheritedVariant3");
        inheritedVariant3.set("j","inheritedVariant3-j", null); // Only defined in one inherited variant, but inherited twice

        main.addInherited(inheritedVariant1,new String[] {"x1"});
        main.addInherited(inheritedVariant3,new String[] {"x1"});
        main.addInherited(inheritedVariant2,new String[] {"x1","y2"});
        main.addInherited(inheritedVariant3,new String[] {"x1","y2"});

        // Runtime-ify
        Properties properties=new QueryProfileProperties(main.compile(null));

        int expectedBaseSize=4;

        // No context
        Map<String,Object> listed=properties.listProperties();
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));

        // Context x=x1
        listed=properties.listProperties(toMap(main, new String[] {"x1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));
        assertEquals("main-e-x1",listed.get("e"));
        assertEquals("main-f-x1",listed.get("f"));
        assertEquals("inheritedVariant1-h",listed.get("h"));
        assertEquals("inheritedVariant3-j",listed.get("j"));

        // Context x=x1,y=y1
        listed=properties.listProperties(toMap(main, new String[] {"x1","y1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1.y1",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));
        assertEquals("main-e-x1",listed.get("e"));
        assertEquals("main-f-x1",listed.get("f"));
        assertEquals("inheritedVariant1-h",listed.get("h"));
        assertEquals("inheritedVariant3-j",listed.get("j"));

        // Context x=x1,y=y2
        listed=properties.listProperties(toMap(main, new String[] {"x1","y2"}));
        assertEquals(expectedBaseSize+6,listed.size());
        assertEquals("main-a-x1.y2",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));
        assertEquals("main-e-x1.y2",listed.get("e"));
        assertEquals("main-f-x1",listed.get("f"));
        assertEquals("main-g-x1.y2",listed.get("g"));
        assertEquals("inheritedVariant2-h",listed.get("h"));
        assertEquals("inheritedVariant2-i",listed.get("i"));
        assertEquals("inheritedVariant3-j",listed.get("j"));

        // Context x=x1,y=y3
        listed=properties.listProperties(toMap(main, new String[] {"x1","y3"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));
        assertEquals("main-e-x1",listed.get("e"));
        assertEquals("main-f-x1",listed.get("f"));
        assertEquals("inheritedVariant1-h",listed.get("h"));
        assertEquals("inheritedVariant3-j",listed.get("j"));

        // Context x=x2,y=y1
        listed=properties.listProperties(toMap(main, new String[] {"x2","y1"}));
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("a"));
        assertEquals("main-b",listed.get("b"));
        assertEquals("parent1-c",listed.get("c"));
        assertEquals("parent2-d",listed.get("d"));
    }

    @Test
    public void testListVariantPropertiesCompounds1Simplified() {
        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x","y"});
        main.set("a.p1","main-a-x1",new String[] {"x1"}, null);

        QueryProfile inheritedVariant1=new QueryProfile("inheritedVariant1");
        inheritedVariant1.set("a.p1","inheritedVariant1-a", null);
        main.addInherited(inheritedVariant1,new String[] {"x1"});

        Properties properties=new QueryProfileProperties(main.compile(null));

        // Context x=x1
        Map<String,Object> listed=properties.listProperties(toMap(main,new String[] {"x1"}));
        assertEquals("main-a-x1",listed.get("a.p1"));
    }

    @Test
    public void testListVariantPropertiesCompounds1() {
        QueryProfile parent1=new QueryProfile("parent1");
        parent1.set("a.p1","parent1-a", null); // Defined everywhere
        parent1.set("b.p1","parent1-b", null); // Defined everywhere, but no variants
        parent1.set("c.p1","parent1-c", null); // Defined in both parents only

        QueryProfile parent2=new QueryProfile("parent2");
        parent2.set("a.p1","parent2-a", null);
        parent2.set("b.p1","parent2-b", null);
        parent2.set("c.p1","parent2-c", null);
        parent2.set("d.p1","parent2-d", null); // Defined in second parent only

        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x","y"});
        main.addInherited(parent1);
        main.addInherited(parent2);
        main.set("a.p1","main-a", null);
        main.set("a.p1","main-a-x1",new String[] {"x1"}, null);
        main.set("e.p1","main-e-x1",new String[] {"x1"}, null); // Defined in two variants only
        main.set("f.p1","main-f-x1",new String[] {"x1"}, null); // Defined in one variants only
        main.set("a.p1","main-a-x1.y1",new String[] {"x1","y1"}, null);
        main.set("a.p1","main-a-x1.y2",new String[] {"x1","y2"}, null);
        main.set("e.p1","main-e-x1.y2",new String[] {"x1","y2"}, null);
        main.set("g.p1","main-g-x1.y2",new String[] {"x1","y2"}, null); // Defined in one variant only
        main.set("b.p1","main-b", null);

        QueryProfile inheritedVariant1=new QueryProfile("inheritedVariant1");
        inheritedVariant1.set("a.p1","inheritedVariant1-a", null);
        inheritedVariant1.set("h.p1","inheritedVariant1-h", null); // Only defined in two inherited variants

        QueryProfile inheritedVariant2=new QueryProfile("inheritedVariant2");
        inheritedVariant2.set("a.p1","inheritedVariant2-a", null);
        inheritedVariant2.set("h.p1","inheritedVariant2-h", null); // Only defined in two inherited variants
        inheritedVariant2.set("i.p1","inheritedVariant2-i", null); // Only defined in one inherited variant

        QueryProfile inheritedVariant3=new QueryProfile("inheritedVariant3");
        inheritedVariant3.set("j.p1","inheritedVariant3-j", null); // Only defined in one inherited variant, but inherited twice

        main.addInherited(inheritedVariant1,new String[] {"x1"});
        main.addInherited(inheritedVariant3,new String[] {"x1"});
        main.addInherited(inheritedVariant2,new String[] {"x1","y2"});
        main.addInherited(inheritedVariant3,new String[] {"x1","y2"});

        Properties properties=new QueryProfileProperties(main.compile(null));

        int expectedBaseSize=4;

        // No context
        Map<String,Object> listed=properties.listProperties();
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));

        // Context x=x1
        listed=properties.listProperties(toMap(main,new String[] {"x1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));
        assertEquals("main-e-x1",listed.get("e.p1"));
        assertEquals("main-f-x1",listed.get("f.p1"));
        assertEquals("inheritedVariant1-h",listed.get("h.p1"));
        assertEquals("inheritedVariant3-j",listed.get("j.p1"));

        // Context x=x1,y=y1
        listed=properties.listProperties(toMap(main,new String[] {"x1","y1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1.y1",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));
        assertEquals("main-e-x1",listed.get("e.p1"));
        assertEquals("main-f-x1",listed.get("f.p1"));
        assertEquals("inheritedVariant1-h",listed.get("h.p1"));
        assertEquals("inheritedVariant3-j",listed.get("j.p1"));

        // Context x=x1,y=y2
        listed=properties.listProperties(toMap(main,new String[] {"x1","y2"}));
        assertEquals(expectedBaseSize+6,listed.size());
        assertEquals("main-a-x1.y2",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));
        assertEquals("main-e-x1.y2",listed.get("e.p1"));
        assertEquals("main-f-x1",listed.get("f.p1"));
        assertEquals("main-g-x1.y2",listed.get("g.p1"));
        assertEquals("inheritedVariant2-h",listed.get("h.p1"));
        assertEquals("inheritedVariant2-i",listed.get("i.p1"));
        assertEquals("inheritedVariant3-j",listed.get("j.p1"));

        // Context x=x1,y=y3
        listed=properties.listProperties(toMap(main,new String[] {"x1","y3"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));
        assertEquals("main-e-x1",listed.get("e.p1"));
        assertEquals("main-f-x1",listed.get("f.p1"));
        assertEquals("inheritedVariant1-h",listed.get("h.p1"));
        assertEquals("inheritedVariant3-j",listed.get("j.p1"));

        // Context x=x2,y=y1
        listed=properties.listProperties(toMap(main,new String[] {"x2","y1"}));
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("a.p1"));
        assertEquals("main-b",listed.get("b.p1"));
        assertEquals("parent1-c",listed.get("c.p1"));
        assertEquals("parent2-d",listed.get("d.p1"));
    }

    @Test
    public void testListVariantPropertiesCompounds2() {
        QueryProfile parent1=new QueryProfile("parent1");
        parent1.set("p1.a","parent1-a", null); // Defined everywhere
        parent1.set("p1.b","parent1-b", null); // Defined everywhere, but no variants
        parent1.set("p1.c","parent1-c", null); // Defined in both parents only

        QueryProfile parent2=new QueryProfile("parent2");
        parent2.set("p1.a","parent2-a", null);
        parent2.set("p1.b","parent2-b", null);
        parent2.set("p1.c","parent2-c", null);
        parent2.set("p1.d","parent2-d", null); // Defined in second parent only

        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x","y"});
        main.addInherited(parent1);
        main.addInherited(parent2);
        main.set("p1.a","main-a", null);
        main.set("p1.a","main-a-x1",new String[] {"x1"}, null);
        main.set("p1.e","main-e-x1",new String[] {"x1"}, null); // Defined in two variants only
        main.set("p1.f","main-f-x1",new String[] {"x1"}, null); // Defined in one variants only
        main.set("p1.a","main-a-x1.y1",new String[] {"x1","y1"}, null);
        main.set("p1.a","main-a-x1.y2",new String[] {"x1","y2"}, null);
        main.set("p1.e","main-e-x1.y2",new String[] {"x1","y2"}, null);
        main.set("p1.g","main-g-x1.y2",new String[] {"x1","y2"}, null); // Defined in one variant only
        main.set("p1.b","main-b", null);

        QueryProfile inheritedVariant1=new QueryProfile("inheritedVariant1");
        inheritedVariant1.set("p1.a","inheritedVariant1-a", null);
        inheritedVariant1.set("p1.h","inheritedVariant1-h", null); // Only defined in two inherited variants

        QueryProfile inheritedVariant2=new QueryProfile("inheritedVariant2");
        inheritedVariant2.set("p1.a","inheritedVariant2-a", null);
        inheritedVariant2.set("p1.h","inheritedVariant2-h", null); // Only defined in two inherited variants
        inheritedVariant2.set("p1.i","inheritedVariant2-i", null); // Only defined in one inherited variant

        QueryProfile inheritedVariant3=new QueryProfile("inheritedVariant3");
        inheritedVariant3.set("p1.j","inheritedVariant3-j", null); // Only defined in one inherited variant, but inherited twice

        main.addInherited(inheritedVariant1,new String[] {"x1"});
        main.addInherited(inheritedVariant3,new String[] {"x1"});
        main.addInherited(inheritedVariant2,new String[] {"x1","y2"});
        main.addInherited(inheritedVariant3,new String[] {"x1","y2"});

        Properties properties=new QueryProfileProperties(main.compile(null));

        int expectedBaseSize=4;

        // No context
        Map<String,Object> listed=properties.listProperties();
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));

        // Context x=x1
        listed=properties.listProperties(toMap(main,new String[] {"x1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));
        assertEquals("main-e-x1",listed.get("p1.e"));
        assertEquals("main-f-x1",listed.get("p1.f"));
        assertEquals("inheritedVariant1-h",listed.get("p1.h"));
        assertEquals("inheritedVariant3-j",listed.get("p1.j"));

        // Context x=x1,y=y1
        listed=properties.listProperties(toMap(main,new String[] {"x1","y1"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1.y1",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));
        assertEquals("main-e-x1",listed.get("p1.e"));
        assertEquals("main-f-x1",listed.get("p1.f"));
        assertEquals("inheritedVariant1-h",listed.get("p1.h"));
        assertEquals("inheritedVariant3-j",listed.get("p1.j"));

        // Context x=x1,y=y2
        listed=properties.listProperties(toMap(main,new String[] {"x1","y2"}));
        assertEquals(expectedBaseSize+6,listed.size());
        assertEquals("main-a-x1.y2",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));
        assertEquals("main-e-x1.y2",listed.get("p1.e"));
        assertEquals("main-f-x1",listed.get("p1.f"));
        assertEquals("main-g-x1.y2",listed.get("p1.g"));
        assertEquals("inheritedVariant2-h",listed.get("p1.h"));
        assertEquals("inheritedVariant2-i",listed.get("p1.i"));
        assertEquals("inheritedVariant3-j",listed.get("p1.j"));

        // Context x=x1,y=y3
        listed=properties.listProperties(toMap(main,new String[] {"x1","y3"}));
        assertEquals(expectedBaseSize+4,listed.size());
        assertEquals("main-a-x1",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));
        assertEquals("main-e-x1",listed.get("p1.e"));
        assertEquals("main-f-x1",listed.get("p1.f"));
        assertEquals("inheritedVariant1-h",listed.get("p1.h"));
        assertEquals("inheritedVariant3-j",listed.get("p1.j"));

        // Context x=x2,y=y1
        listed=properties.listProperties(toMap(main,new String[] {"x2","y1"}));
        assertEquals(expectedBaseSize,listed.size());
        assertEquals("main-a",listed.get("p1.a"));
        assertEquals("main-b",listed.get("p1.b"));
        assertEquals("parent1-c",listed.get("p1.c"));
        assertEquals("parent2-d",listed.get("p1.d"));
    }

    @Test
    public void testQueryProfileReferences() {
        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x1"});
        QueryProfile referencedMain=new QueryProfile("referencedMain");
        referencedMain.set("r1","mainReferenced-r1", null); // In both
        referencedMain.set("r2","mainReferenced-r2", null); // Only in this
        QueryProfile referencedVariant=new QueryProfile("referencedVariant");
        referencedVariant.set("r1","variantReferenced-r1", null); // In both
        referencedVariant.set("r3","variantReferenced-r3", null); // Only in this

        main.set("a",referencedMain, null);
        main.set("a",referencedVariant,new String[] {"x1"}, null);

        Properties properties=new QueryProfileProperties(main.compile(null));

        // No context
        Map<String,Object> listed=properties.listProperties();
        assertEquals(2,listed.size());
        assertEquals("mainReferenced-r1",listed.get("a.r1"));
        assertEquals("mainReferenced-r2",listed.get("a.r2"));

        // Context x=x1
        listed=properties.listProperties(toMap(main,new String[] {"x1"}));
        assertEquals(3,listed.size());
        assertEquals("variantReferenced-r1",listed.get("a.r1"));
        assertEquals("mainReferenced-r2",listed.get("a.r2"));
        assertEquals("variantReferenced-r3",listed.get("a.r3"));
    }

    @Test
    public void testQueryProfileReferencesWithSubstitution() {
        QueryProfile main=new QueryProfile("main");
        main.setDimensions(new String[] {"x1"});
        QueryProfile referencedMain=new QueryProfile("referencedMain");
        referencedMain.set("r1","%{prefix}mainReferenced-r1", null); // In both
        referencedMain.set("r2","%{prefix}mainReferenced-r2", null); // Only in this
        QueryProfile referencedVariant=new QueryProfile("referencedVariant");
        referencedVariant.set("r1","%{prefix}variantReferenced-r1", null); // In both
        referencedVariant.set("r3","%{prefix}variantReferenced-r3", null); // Only in this

        main.set("a",referencedMain, null);
        main.set("a",referencedVariant,new String[] {"x1"}, null);
        main.set("prefix","mainPrefix:", null);
        main.set("prefix","variantPrefix:",new String[] {"x1"}, null);

        Properties properties=new QueryProfileProperties(main.compile(null));

        // No context
        Map<String,Object> listed=properties.listProperties();
        assertEquals(3,listed.size());
        assertEquals("mainPrefix:mainReferenced-r1",listed.get("a.r1"));
        assertEquals("mainPrefix:mainReferenced-r2",listed.get("a.r2"));

        // Context x=x1
        listed=properties.listProperties(toMap(main,new String[] {"x1"}));
        assertEquals(4,listed.size());
        assertEquals("variantPrefix:variantReferenced-r1",listed.get("a.r1"));
        assertEquals("variantPrefix:mainReferenced-r2",listed.get("a.r2"));
        assertEquals("variantPrefix:variantReferenced-r3",listed.get("a.r3"));
    }

    @Test
    public void testNewsCase1() {
        QueryProfile shortcuts=new QueryProfile("shortcuts");
        shortcuts.setDimensions(new String[] {"custid_1","custid_2","custid_3","custid_4","custid_5","custid_6"});
        shortcuts.set("testout","outside", null);
        shortcuts.set("test.out","dotoutside", null);
        shortcuts.set("testin","inside",new String[] {"yahoo","ca","sc"}, null);
        shortcuts.set("test.in","dotinside",new String[] {"yahoo","ca","sc"}, null);

        QueryProfile profile=new QueryProfile("default");
        profile.setDimensions(new String[] {"custid_1","custid_2","custid_3","custid_4","custid_5","custid_6"});
        profile.addInherited(shortcuts, new String[] {"yahoo",null,"sc"});

        profile.freeze();
        Query query = new Query(HttpRequest.createTestRequest("?query=test&custid_1=yahoo&custid_2=ca&custid_3=sc", Method.GET), profile.compile(null));

        assertEquals("outside",query.properties().get("testout"));
        assertEquals("dotoutside",query.properties().get("test.out"));
        assertEquals("inside",query.properties().get("testin"));
        assertEquals("dotinside",query.properties().get("test.in"));
    }

    @Test
    public void testNewsCase2() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions("sort,resulttypes,rss,age,intl,testid".split(","));
        String[] dimensionValues=new String[] {null,null,"0"};
        test.set("discovery","sources",dimensionValues, null);
        test.set("discoverytypes","article",dimensionValues, null);
        test.set("discovery.sources.count","10",dimensionValues, null);

        CompiledQueryProfile ctest = test.compile(null);

        assertEquals("sources",ctest.get("discovery", toMap(test, dimensionValues)));
        assertEquals("article",ctest.get("discoverytypes", toMap(test, dimensionValues)));
        assertEquals("10",ctest.get("discovery.sources.count", toMap(test, dimensionValues)));

        Map<String,Object> values=ctest.listValues("",toMap(test,dimensionValues));
        assertEquals(3,values.size());
        assertEquals("sources",values.get("discovery"));
        assertEquals("article",values.get("discoverytypes"));
        assertEquals("10",values.get("discovery.sources.count"));

        Map<String,Object> sourceValues=ctest.listValues("discovery.sources",toMap(test,dimensionValues));
        assertEquals(1,sourceValues.size());
        assertEquals("10",sourceValues.get("count"));
    }

    @Test
    public void testRuntimeAssignmentInClone() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"});
        String[] x1=new String[] {"x1"};
        Map<String,String> x1m=toMap(test,x1);
        test.set("a","30",x1, null);
        test.set("a.b","20",x1, null);
        test.set("a.b.c","10",x1, null);

        // Setting in one profile works
        Query qMain = new Query(HttpRequest.createTestRequest("?query=test", Method.GET), test.compile(null));
        qMain.properties().set("a.b","50",x1m);
        assertEquals("50",qMain.properties().get("a.b",x1m));

        // Cloning
        Query qBranch=qMain.clone();

        // Setting in main still works
        qMain.properties().set("a.b","51",x1m);
        assertEquals("51",qMain.properties().get("a.b",x1m));

        // Clone is not affected by change in original
        assertEquals("50",qBranch.properties().get("a.b",x1m));

        // Setting in clone works
        qBranch.properties().set("a.b","70",x1m);
        assertEquals("70",qBranch.properties().get("a.b",x1m));

        // Setting in clone does not affect original
        assertEquals("51",qMain.properties().get("a.b",x1m));
    }

    @Test
    public void testIncompatibleDimensions() {
        QueryProfile alert = new QueryProfile("alert");

        QueryProfile backendBase = new QueryProfile("backendBase");
        backendBase.setDimensions(new String[] { "sort", "resulttypes", "rss" });
        backendBase.set("custid", "s", null);

        QueryProfile backend = new QueryProfile("backend");
        backend.setDimensions(new String[] { "sort", "offset", "resulttypes", "rss", "age", "lang", "fr", "entry" });
        backend.addInherited(backendBase);

        QueryProfile web = new QueryProfile("web");
        web.setDimensions(new String[] { "entry", "recency" });
        web.set("fr", "alerts", new String[] { "alert" }, null);

        alert.set("config.backend.vertical.news", backend, null);
        alert.set("config.backend.multimedia", web, null);
        backend.set("custid", "yahoo/alerts", new String[] { null, null, null, null, null, "en-US", null, "alert"}, null);

        CompiledQueryProfile cAlert = alert.compile(null);
        assertEquals("yahoo/alerts", cAlert.get("config.backend.vertical.news.custid", toMap("entry=alert", "intl=us", "lang=en-US")));
    }

    @Test
    public void testIncompatibleDimensionsSimplified() {
        QueryProfile alert = new QueryProfile("alert");

        QueryProfile backendBase = new QueryProfile("backendBase");
        backendBase.set("custid", "s", null);

        QueryProfile backend = new QueryProfile("backend");
        backend.setDimensions(new String[] { "sort", "lang", "fr", "entry" });
        backend.set("custid", "yahoo/alerts", new String[] { null, "en-US", null, "alert"}, null);
        backend.addInherited(backendBase);

        QueryProfile web = new QueryProfile("web");
        web.setDimensions(new String[] { "entry", "recency" });
        web.set("fr", "alerts", new String[] { "alert" }, null);

        alert.set("vertical", backend, null);
        alert.set("multimedia", web, null);

        CompiledQueryProfile cAlert = alert.compile(null);
        assertEquals("yahoo/alerts", cAlert.get("vertical.custid", toMap("entry=alert", "intl=us", "lang=en-US")));
    }

    private void assertGet(String expectedValue, String parameter, String[] dimensionValues, QueryProfile profile, CompiledQueryProfile cprofile) {
        Map<String,String> context=toMap(profile,dimensionValues);
        assertEquals("Looking up '" + parameter + "' for '" + Arrays.toString(dimensionValues) + "'",expectedValue,cprofile.get(parameter,context));
    }

    public static Map<String,String> toMap(QueryProfile profile, String[] dimensionValues) {
        Map<String,String> context=new HashMap<>();
        List<String> dimensions;
        if (profile.getVariants()!=null)
            dimensions=profile.getVariants().getDimensions();
        else
            dimensions=((BackedOverridableQueryProfile)profile).getBacking().getVariants().getDimensions();

        for (int i=0; i<dimensionValues.length; i++)
            context.put(dimensions.get(i),dimensionValues[i]); // Lookup dim. names to ease test...
        return context;
    }

    public static final Map<String, String> toMap(String... bindings) {
        Map<String, String> context = new HashMap<>();
        for (String binding : bindings) {
            String[] entry = binding.split("=");
            context.put(entry[0].trim(), entry[1].trim());
        }
        return context;
    }

}
