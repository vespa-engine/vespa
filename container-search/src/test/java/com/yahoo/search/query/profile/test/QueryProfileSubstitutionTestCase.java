// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.processing.request.Properties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;

/**
 * @author bratseth
 */
public class QueryProfileSubstitutionTestCase extends junit.framework.TestCase {

    public void testSingleSubstitution() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","Hello %{world}!", (QueryProfileRegistry)null);
        p.set("world", "world", (QueryProfileRegistry)null);
        assertEquals("Hello world!",p.compile(null).get("message"));

        QueryProfile p2=new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("world", "universe", (QueryProfileRegistry)null);
        assertEquals("Hello universe!",p2.compile(null).get("message"));
    }

    public void testMultipleSubstitutions() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","%{greeting} %{entity}%{exclamation}", (QueryProfileRegistry)null);
        p.set("greeting","Hola", (QueryProfileRegistry)null);
        p.set("entity","local group", (QueryProfileRegistry)null);
        p.set("exclamation","?", (QueryProfileRegistry)null);
        assertEquals("Hola local group?",p.compile(null).get("message"));

        QueryProfile p2=new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("entity","milky way", (QueryProfileRegistry)null);
        assertEquals("Hola milky way?",p2.compile(null).get("message"));
    }

    public void testUnclosedSubstitution1() {
        try {
            QueryProfile p=new QueryProfile("test");
            p.set("message1","%{greeting} %{entity}%{exclamation", (QueryProfileRegistry)null);
            fail("Should have produced an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'message1' to '%{greeting} %{entity}%{exclamation': Unterminated value substitution '%{exclamation'",
                         Exceptions.toMessageString(e));
        }
    }

    public void testUnclosedSubstitution2() {
        try {
            QueryProfile p=new QueryProfile("test");
            p.set("message1","%{greeting} %{entity%{exclamation}", (QueryProfileRegistry)null);
            fail("Should have produced an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'message1' to '%{greeting} %{entity%{exclamation}': Unterminated value substitution '%{entity%{exclamation}'",
                         Exceptions.toMessageString(e));
        }
    }

    public void testNullSubstitution() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","%{greeting} %{entity}%{exclamation}", (QueryProfileRegistry)null);
        p.set("greeting","Hola", (QueryProfileRegistry)null);
        assertEquals("Hola ", p.compile(null).get("message"));

        QueryProfile p2=new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("greeting","Hola", (QueryProfileRegistry)null);
        p2.set("exclamation", "?", (QueryProfileRegistry)null);
        assertEquals("Hola ?",p2.compile(null).get("message"));
    }

    public void testNoOverridingOfPropertiesSetAtRuntime() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","Hello %{world}!", (QueryProfileRegistry)null);
        p.set("world","world", (QueryProfileRegistry)null);
        p.freeze();

        Properties runtime=new QueryProfileProperties(p.compile(null));
        runtime.set("runtimeMessage","Hello %{world}!");
        assertEquals("Hello world!", runtime.get("message"));
        assertEquals("Hello %{world}!",runtime.get("runtimeMessage"));
    }

    public void testButPropertiesSetAtRuntimeAreUsedInSubstitutions() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","Hello %{world}!", (QueryProfileRegistry)null);
        p.set("world","world", (QueryProfileRegistry)null);

        Properties runtime=new QueryProfileProperties(p.compile(null));
        runtime.set("world","Earth");
        assertEquals("Hello Earth!",runtime.get("message"));
    }

    public void testInspection() {
        QueryProfile p=new QueryProfile("test");
        p.set("message", "%{greeting} %{entity}%{exclamation}", (QueryProfileRegistry)null);
        assertEquals("message","%{greeting} %{entity}%{exclamation}",
                     p.declaredContent().entrySet().iterator().next().getValue().toString());
    }

    public void testVariants() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","Hello %{world}!", (QueryProfileRegistry)null);
        p.set("world","world", (QueryProfileRegistry)null);
        p.setDimensions(new String[] {"x"});
        p.set("message","Halo %{world}!",new String[] {"x1"}, null);
        p.set("world","Europe",new String[] {"x2"}, null);

        CompiledQueryProfile cp = p.compile(null);
        assertEquals("Hello world!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x?")));
        assertEquals("Halo world!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x1")));
        assertEquals("Hello Europe!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x2")));
    }

    public void testRecursion() {
        QueryProfile p=new QueryProfile("test");
        p.set("message","Hello %{world}!", (QueryProfileRegistry)null);
        p.set("world","sol planet number %{number}", (QueryProfileRegistry)null);
        p.set("number",3, (QueryProfileRegistry)null);
        assertEquals("Hello sol planet number 3!",p.compile(null).get("message"));
    }

}
