// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.Mirror.Entry;
import com.yahoo.jrt.slobrok.server.Slobrok;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SlobrokTest {

    private static class SpecList extends ArrayList<Mirror.Entry> {
        public SpecList add(String name, String spec) {
            add(new Mirror.Entry(name, spec));
            return this;
        }
    }

    String[]   slobroks;
    boolean    error    = false;
    Supervisor server   = new Supervisor(new Transport());
    Supervisor client   = new Supervisor(new Transport());
    Acceptor   acceptor = null;
    Mirror     mirror   = null;
    Register   register = null;
    String     mySpec   = null;
    Slobrok    slobrok;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        slobroks = new String[1];
        slobroks[0] = new Spec("localhost", slobrok.port()).toString();
        SlobrokList slobroklist = new SlobrokList();
        slobroklist.setup(slobroks);
        acceptor = server.listen(new Spec(0));
        mirror = new Mirror(client, slobroklist);
        register = new Register(server, slobroklist,
                                "localhost", acceptor.port());
        mySpec = new Spec("localhost", acceptor.port()).toString();
    }

    @After
    public void tearDown() {
        register.shutdown();
        mirror.shutdown();
        acceptor.shutdown();
        client.transport().shutdown();
        server.transport().shutdown();
        slobrok.stop();
    }

    void check(String pattern, ArrayList<Entry> result) {
        if (error) {
            err("already failed, skipping test");
            return;
        }
        Comparator<Entry> cmp = new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                return a.compareTo(b);
            }
        };
        List<Entry> expect = result;
        expect.sort(cmp);
        List<Entry> actual = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            actual = mirror.lookup(pattern);
            actual.sort(cmp);
            if (actual.equals(expect)) {
                // err("lookup successful for pattern: " + pattern);
                return;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {}
        }
        error = true;
        err("lookup failed for pattern: " + pattern);
        err("actual values:");
        if (actual.isEmpty()) {
            err("  { EMPTY }");
        }
        for (Entry e : actual) {
            err("  {" + e.getName() + ", " + e.getSpecString() + "}");
        }
        err("expected values:");
        if (expect.isEmpty()) {
            err("  { EMPTY }");
        }
        for (Entry e : expect) {
            err("  {" + e.getName() + ", " + e.getSpecString() + "}");
        }
    }

    @org.junit.Test
    public void testSlobrok() {
        String wantName = "A/x/w";
        register.registerName(wantName);
        check(wantName, new SpecList().add(wantName, mySpec));
        check("*/*", new SpecList());
        check("*/*/*", new SpecList().add(wantName, mySpec));

        assertTrue(mirror.ready());
        assertTrue(mirror.updates() > 0);

        mirror.dumpState();
        List<Entry> oneArr = mirror.lookup("*/*/*");
        mirror.dumpState();
        assertEquals(1, oneArr.size());
        Mirror.Entry one = oneArr.get(0);
        assertEquals(one, new Entry(wantName, mySpec));
        assertNotEquals(one, new Entry("B/x/w", mySpec));
        assertNotEquals(one, new Entry(wantName, "foo:99"));
        assertNotEquals(one, null);
        assertNotEquals(one, register);
        assertEquals(one.getName(), wantName);
        assertEquals(one.getSpecString(), mySpec);

        register.registerName("B/x");
        check("B/x", new SpecList().add("B/x", mySpec));
        check("*/*", new SpecList().add("B/x", mySpec));
        check("*/*/*", new SpecList().add("A/x/w", mySpec));

        register.registerName("C/x/z");
        check("C/x/z", new SpecList().add("C/x/z", mySpec));
        check("*/*", new SpecList().add("B/x", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec));

        register.registerName("D/y/z");
        check("D/y/z", new SpecList().add("D/y/z", mySpec));
        check("*/*", new SpecList().add("B/x", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec));

        register.registerName("E/y");
        check("E/y", new SpecList().add("E/y", mySpec));
        check("*/*", new SpecList()
              .add("B/x", mySpec)
              .add("E/y", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec));

        register.registerName("F/y/w");
        check("F/y/w", new SpecList().add("F/y/w", mySpec));
        check("*/*", new SpecList()
              .add("B/x", mySpec)
              .add("E/y", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec)
              .add("F/y/w", mySpec));

        check("*", new SpecList());

        check("B/*", new SpecList()
              .add("B/x", mySpec));

        check("*/y", new SpecList()
              .add("E/y", mySpec));

        check("*/x/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec));

        check("*/*/z", new SpecList()
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec));

        check("A/*/z", new SpecList());

        check("A/*/w", new SpecList()
              .add("A/x/w", mySpec));

        register.unregisterName("E/y");
        register.unregisterName("C/x/z");
        register.unregisterName("F/y/w");
        check("*/*", new SpecList()
              .add("B/x", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("D/y/z", mySpec));

        register.registerName("E/y");
        register.registerName("C/x/z");
        register.registerName("F/y/w");
        check("*/*", new SpecList()
              .add("B/x", mySpec)
              .add("E/y", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec)
              .add("F/y/w", mySpec));

        register.unregisterName("E/y");
        register.unregisterName("C/x/z");
        register.unregisterName("F/y/w");
        check("*/*", new SpecList()
              .add("B/x", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("D/y/z", mySpec));

        register.registerName("E/y");
        register.registerName("C/x/z");
        register.registerName("F/y/w");
        check("*/*", new SpecList()
              .add("B/x", mySpec)
              .add("E/y", mySpec));
        check("*/*/*", new SpecList()
              .add("A/x/w", mySpec)
              .add("C/x/z", mySpec)
              .add("D/y/z", mySpec)
              .add("F/y/w", mySpec));

        assertFalse(error);
    }

    public static void err(String msg) {
        System.err.println(msg);
    }

}
