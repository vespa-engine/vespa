// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.processing.request.Properties;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileProperties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class QueryProfileSubstitutionTestCase {

    @Test
    void testSubstitutionOnly() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "%{world}", null);
        p.set("world", "world", null);
        assertEquals("world", p.compile(null).get("message"));
    }

    @Test
    void testSingleSubstitution() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{world}!", null);
        p.set("world", "world", null);
        assertEquals("Hello world!", p.compile(null).get("message"));

        QueryProfile p2 = new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("world", "universe", null);
        assertEquals("Hello universe!", p2.compile(null).get("message"));
    }

    @Test
    void testRelativeSubstitution() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{.world}!", null);
        p.set("world", "world", null);
        assertEquals("Hello world!", p.compile(null).get("message"));
    }

    @Test
    void testRelativeSubstitutionNotFound() {
        try {
            QueryProfile p = new QueryProfile("test");
            p.set("message", "Hello %{.world}!", null);
            assertEquals("Hello world!", p.compile(null).get("message"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Invalid query profile 'test': Could not resolve local substitution 'world' in variant []",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testMultipleSubstitutions() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "%{greeting} %{entity}%{exclamation}", null);
        p.set("greeting", "Hola", null);
        p.set("entity", "local group", null);
        p.set("exclamation", "?", null);
        assertEquals("Hola local group?", p.compile(null).get("message"));

        QueryProfile p2 = new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("entity", "milky way", null);
        assertEquals("Hola milky way?", p2.compile(null).get("message"));
    }

    @Test
    void testUnclosedSubstitution1() {
        try {
            QueryProfile p = new QueryProfile("test");
            p.set("message1", "%{greeting} %{entity}%{exclamation", null);
            fail("Should have produced an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'message1' to '%{greeting} %{entity}%{exclamation': Unterminated value substitution '%{exclamation'",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testUnclosedSubstitution2() {
        try {
            QueryProfile p = new QueryProfile("test");
            p.set("message1", "%{greeting} %{entity%{exclamation}", null);
            fail("Should have produced an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Could not set 'message1' to '%{greeting} %{entity%{exclamation}': Unterminated value substitution '%{entity%{exclamation}'",
                    Exceptions.toMessageString(e));
        }
    }

    @Test
    void testNullSubstitution() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "%{greeting} %{entity}%{exclamation}", null);
        p.set("greeting", "Hola", null);
        assertEquals("Hola ", p.compile(null).get("message"));

        QueryProfile p2 = new QueryProfile("test2");
        p2.addInherited(p);
        p2.set("greeting", "Hola", null);
        p2.set("exclamation", "?", null);
        assertEquals("Hola ?", p2.compile(null).get("message"));
    }

    @Test
    void testNoOverridingOfPropertiesSetAtRuntime() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{world}!", null);
        p.set("world", "world", null);
        p.freeze();

        Properties runtime = new QueryProfileProperties(p.compile(null));
        runtime.set("runtimeMessage", "Hello %{world}!");
        assertEquals("Hello world!", runtime.get("message"));
        assertEquals("Hello %{world}!", runtime.get("runtimeMessage"));
    }

    @Test
    void testButPropertiesSetAtRuntimeAreUsedInSubstitutions() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{world}!", null);
        p.set("world", "world", null);

        Properties runtime = new QueryProfileProperties(p.compile(null));
        runtime.set("world", "Earth");
        assertEquals("Hello Earth!", runtime.get("message"));
    }

    @Test
    void testInspection() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "%{greeting} %{entity}%{exclamation}", null);
        assertEquals("%{greeting} %{entity}%{exclamation}",
                p.declaredContent().entrySet().iterator().next().getValue().toString(),
                "message");
    }

    @Test
    void testVariants() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{world}!", null);
        p.set("world", "world", null);
        p.setDimensions(new String[]{"x"});
        p.set("message", "Halo %{world}!", new String[]{"x1"}, null);
        p.set("world", "Europe", new String[]{"x2"}, null);

        CompiledQueryProfile cp = p.compile(null);
        assertEquals("Hello world!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x?")));
        assertEquals("Halo world!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x1")));
        assertEquals("Hello Europe!", cp.get("message", QueryProfileVariantsTestCase.toMap("x=x2")));
    }

    @Test
    void testRecursion() {
        QueryProfile p = new QueryProfile("test");
        p.set("message", "Hello %{world}!", null);
        p.set("world", "sol planet number %{number}", null);
        p.set("number", 3, null);
        assertEquals("Hello sol planet number 3!", p.compile(null).get("message"));
    }

}
