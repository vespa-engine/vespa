// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.jdisc.http.HttpRequest.Method;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class CloningTestCase {

    @Test
    public void testCloningWithVariants() {
        QueryProfile test = new QueryProfile("test");
        test.setDimensions(new String[] {"x"} );
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q&x=x1", Method.GET), test.compile(null));
        q1.properties().set("a","a1");
        Query q2 = q1.clone();
        q2.properties().set("a","a2");
        assertEquals("a1",q1.properties().get("a"));
        assertEquals("a2",q2.properties().get("a"));
    }

    @Test
    public void testShallowCloning() {
        QueryProfile test = new QueryProfile("test");
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));
        q1.properties().set("a",new MutableString("a1"));
        Query q2 = q1.clone();
        ((MutableString)q2.properties().get("a")).set("a2");
        assertEquals("a2",q1.properties().get("a").toString());
        assertEquals("a2",q2.properties().get("a").toString());
    }

    @Test
    public void testShallowCloningWithVariants() {
        QueryProfile test = new QueryProfile("test");
        test.setDimensions(new String[] {"x"} );
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q&x=x1", Method.GET), test.compile(null));
        q1.properties().set("a",new MutableString("a1"));
        Query q2 = q1.clone();
        ((MutableString)q2.properties().get("a")).set("a2");
        assertEquals("a2",q1.properties().get("a").toString());
        assertEquals("a2",q2.properties().get("a").toString());
    }

    @Test
    public void testDeepCloning() {
        QueryProfile test=new QueryProfile("test");
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));
        q1.properties().set("a",new CloneableMutableString("a1"));
        Query q2=q1.clone();
        ((MutableString)q2.properties().get("a")).set("a2");
        assertEquals("a1",q1.properties().get("a").toString());
        assertEquals("a2",q2.properties().get("a").toString());
    }

    @Test
    public void testDeepCloningWithVariants() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"} );
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q&x=x1", Method.GET), test.compile(null));
        q1.properties().set("a",new CloneableMutableString("a1"));
        Query q2=q1.clone();
        ((MutableString)q2.properties().get("a")).set("a2");
        assertEquals("a1",q1.properties().get("a").toString());
        assertEquals("a2",q2.properties().get("a").toString());
    }

    @Test
    public void testReAssignment() {
        QueryProfile test=new QueryProfile("test");
        test.setDimensions(new String[] {"x"} );
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q&x=x1", Method.GET), test.compile(null));
        q1.properties().set("a","a1");
        q1.properties().set("a","a2");
        assertEquals("a2",q1.properties().get("a"));
    }

    @Test
    public void testThreeLevelsOfCloning() {
        QueryProfile test = new QueryProfile("test");
        test.set("a", "config-a", (QueryProfileRegistry)null);
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));

        q1.properties().set("a","q1-a");
        Query q2=q1.clone();
        q2.properties().set("a","q2-a");
        Query q31=q2.clone();
        q31.properties().set("a","q31-a");
        Query q32=q2.clone();
        q32.properties().set("a","q32-a");

        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
        q2.properties().set("a","q2-a-2");
        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a-2",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
    }

    @Test
    public void testThreeLevelsOfCloningReverseSetOrder() {
        QueryProfile test = new QueryProfile("test");
        test.set("a", "config-a", (QueryProfileRegistry)null);
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));

        Query q2=q1.clone();
        Query q31=q2.clone();
        Query q32=q2.clone();
        q32.properties().set("a","q32-a");
        q31.properties().set("a","q31-a");
        q2.properties().set("a","q2-a");
        q1.properties().set("a","q1-a");

        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
        q2.properties().set("a","q2-a-2");
        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a-2",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
    }

    @Test
    public void testThreeLevelsOfCloningMiddleFirstSetOrder1() {
        QueryProfile test = new QueryProfile("test");
        test.set("a", "config-a", (QueryProfileRegistry)null);
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));

        Query q2=q1.clone();
        Query q31=q2.clone();
        Query q32=q2.clone();
        q2.properties().set("a","q2-a");
        q32.properties().set("a","q32-a");
        q31.properties().set("a","q31-a");
        q1.properties().set("a","q1-a");

        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
        q2.properties().set("a","q2-a-2");
        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a-2",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("q32-a",q32.properties().get("a").toString());
    }

    @Test
    public void testThreeLevelsOfCloningMiddleFirstSetOrder2() {
        QueryProfile test = new QueryProfile("test");
        test.set("a", "config-a", (QueryProfileRegistry)null);
        test.freeze();
        Query q1 = new Query(HttpRequest.createTestRequest("?query=q", Method.GET), test.compile(null));

        Query q2=q1.clone();
        Query q31=q2.clone();
        Query q32=q2.clone();
        q2.properties().set("a","q2-a");
        q31.properties().set("a","q31-a");
        q1.properties().set("a","q1-a");

        assertEquals("q1-a",q1.properties().get("a").toString());
        assertEquals("q2-a",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("config-a",q32.properties().get("a").toString());
        q1.properties().set("a","q1-a-2");
        assertEquals("q1-a-2",q1.properties().get("a").toString());
        assertEquals("q2-a",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("config-a",q32.properties().get("a").toString());
        q2.properties().set("a","q2-a-2");
        assertEquals("q1-a-2",q1.properties().get("a").toString());
        assertEquals("q2-a-2",q2.properties().get("a").toString());
        assertEquals("q31-a",q31.properties().get("a").toString());
        assertEquals("config-a",q32.properties().get("a").toString());
    }

    public static class MutableString {

        private String string;

        public MutableString(String string) {
            this.string=string;
        }

        public void set(String string) { this.string=string; }

        @Override
        public String toString() { return string; }

        @Override
        public int hashCode() { return string.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (other==this) return true;
            if ( ! (other instanceof MutableString)) return false;
            return ((MutableString)other).string.equals(string);
        }

    }

    public static class CloneableMutableString extends MutableString implements Cloneable {

        public CloneableMutableString(String string) {
            super(string);
        }

        @Override
        public CloneableMutableString clone() {
            try {
                return (CloneableMutableString)super.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
