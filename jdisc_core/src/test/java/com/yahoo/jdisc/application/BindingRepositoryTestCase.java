// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.NonWorkingRequestHandler;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BindingRepositoryTestCase {

    @Test
    void requireThatRepositoryCanBeActivated() {
        BindingRepository<Object> bindings = new BindingRepository<>();
        bindings.bind("http://host/path", new Object());

        BindingSet<Object> set = bindings.activate();
        assertNotNull(set);
        Iterator<Map.Entry<UriPattern, Object>> it = set.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatActivationIsSnapshotOfRepository() {
        BindingRepository<Object> bindings = new BindingRepository<>();
        bindings.bind("http://host/path", new Object());

        BindingSet<Object> set = bindings.activate();
        assertNotNull(set);
        bindings.clear();

        Iterator<Map.Entry<UriPattern, Object>> it = set.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatObjectsCanBeBound() {
        BindingRepository<Object> bindings = new BindingRepository<>();
        Object foo = new Object();
        Object bar = new Object();
        bindings.bind("http://host/foo", foo);
        bindings.bind("http://host/bar", bar);

        Iterator<Map.Entry<UriPattern, Object>> it = bindings.activate().iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        Map.Entry<UriPattern, Object> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatPatternCannotBeStolen() {
        final String pattern = "http://host/path";
        final RequestHandler originallyBoundHandler = new NonWorkingRequestHandler();

        BindingRepository<Object> bindings = new BindingRepository<>();
        bindings.bind(pattern, originallyBoundHandler);
        bindings.bind(pattern, new PatternStealingRequestHandler());

        BindingSet<?> bindingSet = bindings.activate();
        assertEquals(originallyBoundHandler, bindingSet.resolve(URI.create(pattern)));
    }

    @Test
    void requireThatBindAllMethodWorks() {
        Object foo = new Object();
        Object bar = new Object();
        Object baz = new Object();

        Map<String, Object> toAdd = new HashMap<>();
        toAdd.put("http://host/foo", foo);
        toAdd.put("http://host/bar", bar);

        BindingRepository<Object> addTo = new BindingRepository<>();
        addTo.bind("http://host/baz", baz);
        addTo.bindAll(toAdd);

        Iterator<Map.Entry<UriPattern, Object>> it = addTo.activate().iterator();
        Map.Entry<UriPattern, Object> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/baz"), entry.getKey());
        assertSame(baz, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatPutAllMethodWorks() {
        Object foo = new Object();
        Object bar = new Object();
        Object baz = new Object();

        BindingRepository<Object> toAdd = new BindingRepository<>();
        toAdd.bind("http://host/foo", foo);
        toAdd.bind("http://host/bar", bar);

        BindingRepository<Object> addTo = new BindingRepository<>();
        addTo.bind("http://host/baz", baz);
        addTo.putAll(toAdd);

        Iterator<Map.Entry<UriPattern, Object>> it = addTo.activate().iterator();
        assertTrue(it.hasNext());
        Map.Entry<UriPattern, Object> entry = it.next();
        assertNotNull(entry);
        assertEquals(new UriPattern("http://host/foo"), entry.getKey());
        assertSame(foo, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/baz"), entry.getKey());
        assertSame(baz, entry.getValue());
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(new UriPattern("http://host/bar"), entry.getKey());
        assertSame(bar, entry.getValue());
        assertFalse(it.hasNext());
    }

    @Test
    void requireThatPutNullThrowsException() {
        try {
            new BindingRepository<>().put(null, new Object());
            fail();
        } catch (NullPointerException e) {

        }
        try {
            new BindingRepository<>().put(new UriPattern("http://host/foo"), null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    static class PatternStealingRequestHandler extends NoopSharedResource implements RequestHandler {
        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler handler) { }

    }
}
