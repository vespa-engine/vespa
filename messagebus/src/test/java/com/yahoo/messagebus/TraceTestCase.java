// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class TraceTestCase {

    @Test
    public void testEncodeDecode() {
        assertEquals("()", TraceNode.decode("").encode());
        assertEquals("()", TraceNode.decode("[xyz").encode());
        assertEquals("([xyz][])", TraceNode.decode("[xyz][]").encode());
        assertEquals("[xyz]", TraceNode.decode("[xyz]").encode());
        assertEquals("()", TraceNode.decode("{()").encode());
        assertEquals("({()}{})", TraceNode.decode("{()}{}").encode());
        assertEquals("{()}", TraceNode.decode("{()}").encode());
        assertEquals("()", TraceNode.decode("({}").encode());
        assertEquals("(({})())", TraceNode.decode("({})()").encode());
        assertEquals("([])", TraceNode.decode("([])").encode());

        assertTrue(TraceNode.decode("").isEmpty());
        assertTrue(!TraceNode.decode("([note])").isEmpty());

        String str =
                "([[17/Jun/2009:09:02:30 +0200\\] Message (type 1) received at 'dst' for session 'session'.]" +
                "[[17/Jun/2009:09:02:30 +0200\\] [APP_TRANSIENT_ERROR @ localhost\\]: err1]" +
                "[[17/Jun/2009:09:02:30 +0200\\] Sending reply (version 4.2) from 'dst'.])";
        System.out.println(TraceNode.decode(str).toString());
        assertEquals(str, TraceNode.decode(str).encode());

        str = "([Note 0][Note 1]{[Note 2]}{([Note 3])({[Note 4]})})";
        TraceNode t = TraceNode.decode(str);
        assertEquals(str, t.encode());

        assertTrue(t.isRoot());
        assertTrue(t.isStrict());
        assertTrue(!t.isLeaf());
        assertEquals(4, t.getNumChildren());

        {
            TraceNode c = t.getChild(0);
            assertTrue(c.isLeaf());
            assertEquals("Note 0", c.getNote());
        }
        {
            TraceNode c = t.getChild(1);
            assertTrue(c.isLeaf());
            assertEquals("Note 1", c.getNote());
        }
        {
            TraceNode c = t.getChild(2);
            assertTrue(!c.isLeaf());
            assertTrue(!c.isStrict());
            assertEquals(1, c.getNumChildren());
            {
                TraceNode d = c.getChild(0);
                assertTrue(d.isLeaf());
                assertEquals("Note 2", d.getNote());
            }
        }
        {
            TraceNode c = t.getChild(3);
            assertTrue(!c.isStrict());
            assertEquals(2, c.getNumChildren());
            {
                TraceNode d = c.getChild(0);
                assertTrue(d.isStrict());
                assertTrue(!d.isLeaf());
                assertEquals(1, d.getNumChildren());
                {
                    TraceNode e = d.getChild(0);
                    assertTrue(e.isLeaf());
                    assertEquals("Note 3", e.getNote());
                }
            }
            {
                TraceNode d = c.getChild(1);
                assertTrue(d.isStrict());
                assertEquals(1, d.getNumChildren());
                {
                    TraceNode e = d.getChild(0);
                    assertTrue(!e.isStrict());
                    assertEquals(1, e.getNumChildren());
                    {
                        TraceNode f = e.getChild(0);
                        assertTrue(f.isLeaf());
                        assertEquals("Note 4", f.getNote());
                    }
                }
            }
        }
    }

    @Test
    public void testReservedChars() {
        TraceNode t = new TraceNode();
        t.addChild("abc(){}[]\\xyz");
        assertEquals("abc(){}[]\\xyz", t.getChild(0).getNote());
        assertEquals("([abc(){}[\\]\\\\xyz])", t.encode());
        {
            // test swap/clear/empty here
            TraceNode t2 = new TraceNode();
            assertTrue(t2.isEmpty());
            t2.swap(t);
            assertTrue(!t2.isEmpty());
            assertEquals("abc(){}[]\\xyz", t2.getChild(0).getNote());
            assertEquals("([abc(){}[\\]\\\\xyz])", t2.encode());
            t2.clear();
            assertTrue(t2.isEmpty());
        }
    }

    @Test
    public void testAdd() {
        TraceNode t1 = TraceNode.decode("([x])");
        TraceNode t2 = TraceNode.decode("([y])");
        TraceNode t3 = TraceNode.decode("([z])");

        t1.addChild(t2);
        assertEquals("([x]([y]))", t1.encode());
        assertTrue(t1.getChild(1).isStrict());
        t1.addChild("txt");
        assertTrue(t1.getChild(2).isLeaf());
        assertEquals("([x]([y])[txt])", t1.encode());
        t3.addChild(t1);
        assertEquals("([z]([x]([y])[txt]))", t3.encode());

        // crazy but possible (everything is by value)
        t2.addChild(t2).addChild(t2);
        assertEquals("([y]([y])([y]([y])))", t2.encode());
    }

    @Test
    public void testStrict() {
        assertEquals("{}", TraceNode.decode("()").setStrict(false).encode());
        assertEquals("{[x]}", TraceNode.decode("([x])").setStrict(false).encode());
        assertEquals("{[x][y]}", TraceNode.decode("([x][y])").setStrict(false).encode());
    }

    @Test
    public void testTraceLevel() {
        Trace t = new Trace();
        t.setLevel(4);
        assertEquals(4, t.getLevel());
        t.trace(9, "no");
        assertEquals(0, t.getRoot().getNumChildren());
        t.trace(8, "no");
        assertEquals(0, t.getRoot().getNumChildren());
        t.trace(7, "no");
        assertEquals(0, t.getRoot().getNumChildren());
        t.trace(6, "no");
        assertEquals(0, t.getRoot().getNumChildren());
        t.trace(5, "no");
        assertEquals(0, t.getRoot().getNumChildren());
        t.trace(4, "yes");
        assertEquals(1, t.getRoot().getNumChildren());
        t.trace(3, "yes");
        assertEquals(2, t.getRoot().getNumChildren());
        t.trace(2, "yes");
        assertEquals(3, t.getRoot().getNumChildren());
        t.trace(1, "yes");
        assertEquals(4, t.getRoot().getNumChildren());
        t.trace(0, "yes");
        assertEquals(5, t.getRoot().getNumChildren());
    }

    @Test
    public void testCompact() {
        assertEquals("()", TraceNode.decode("()").compact().encode());
        assertEquals("()", TraceNode.decode("(())").compact().encode());
        assertEquals("()", TraceNode.decode("(()())").compact().encode());
        assertEquals("()", TraceNode.decode("({})").compact().encode());
        assertEquals("()", TraceNode.decode("({}{})").compact().encode());
        assertEquals("()", TraceNode.decode("({{}{}})").compact().encode());

        assertEquals("([x])", TraceNode.decode("([x])").compact().encode());
        assertEquals("([x])", TraceNode.decode("(([x]))").compact().encode());
        assertEquals("([x][y])", TraceNode.decode("(([x])([y]))").compact().encode());
        assertEquals("([x])", TraceNode.decode("({[x]})").compact().encode());
        assertEquals("([x][y])", TraceNode.decode("({[x]}{[y]})").compact().encode());
        assertEquals("({[x][y]})", TraceNode.decode("({{[x]}{[y]}})").compact().encode());

        assertEquals("([a][b][c][d])", TraceNode.decode("(([a][b])([c][d]))").compact().encode());
        assertEquals("({[a][b]}{[c][d]})", TraceNode.decode("({[a][b]}{[c][d]})").compact().encode());
        assertEquals("({[a][b][c][d]})", TraceNode.decode("({{[a][b]}{[c][d]}})").compact().encode());
        assertEquals("({([a][b])([c][d])})", TraceNode.decode("({([a][b])([c][d])})").compact().encode());

        assertEquals("({{}{(({()}({}){()(){}}){})}})", TraceNode.decode("({{}{(({()}({}){()(){}}){})}})").encode());
        assertEquals("()", TraceNode.decode("({{}{(({()}({}){()(){}}){})}})").compact().encode());
        assertEquals("([x])", TraceNode.decode("({{}{([x]({()}({}){()(){}}){})}})").compact().encode());
        assertEquals("([x])", TraceNode.decode("({{}{(({()}({[x]}){()(){}}){})}})").compact().encode());
        assertEquals("([x])", TraceNode.decode("({{}{(({()}({}){()(){}})[x]{})}})").compact().encode());

        assertEquals("({[a][b][c][d][e][f]})", TraceNode.decode("({({[a][b]})({[c][d]})({[e][f]})})").compact().encode());
    }

    @Test
    public void testSort() {
        assertEquals("([b][a][c])", TraceNode.decode("([b][a][c])").sort().encode());
        assertEquals("({[a][b][c]})", TraceNode.decode("({[b][a][c]})").sort().encode());
        assertEquals("(([c][a])([b]))", TraceNode.decode("(([c][a])([b]))").sort().encode());
        assertEquals("({[b]([c][a])})", TraceNode.decode("({([c][a])[b]})").sort().encode());
        assertEquals("({[a][c]}[b])", TraceNode.decode("({[c][a]}[b])").sort().encode());
        assertEquals("({([b]){[a][c]}})", TraceNode.decode("({{[c][a]}([b])})").sort().encode());
    }

    @Test
    public void testNormalize() {
        TraceNode t1 = TraceNode.decode("({([a][b]{[x][y]([p][q])})([c][d])([e][f])})");
        TraceNode t2 = TraceNode.decode("({([a][b]{[y][x]([p][q])})([c][d])([e][f])})");
        TraceNode t3 = TraceNode.decode("({([a][b]{[y]([p][q])[x]})([c][d])([e][f])})");
        TraceNode t4 = TraceNode.decode("({([e][f])([a][b]{[y]([p][q])[x]})([c][d])})");
        TraceNode t5 = TraceNode.decode("({([e][f])([c][d])([a][b]{([p][q])[y][x]})})");

        TraceNode tx = TraceNode.decode("({([b][a]{[x][y]([p][q])})([c][d])([e][f])})");
        TraceNode ty = TraceNode.decode("({([a][b]{[x][y]([p][q])})([d][c])([e][f])})");
        TraceNode tz = TraceNode.decode("({([a][b]{[x][y]([q][p])})([c][d])([e][f])})");

        assertEquals("({([a][b]{[x][y]([p][q])})([c][d])([e][f])})", t1.compact().encode());

        assertTrue(!t1.compact().encode().equals(t2.compact().encode()));
        assertTrue(!t1.compact().encode().equals(t3.compact().encode()));
        assertTrue(!t1.compact().encode().equals(t4.compact().encode()));
        assertTrue(!t1.compact().encode().equals(t5.compact().encode()));
        assertTrue(!t1.compact().encode().equals(tx.compact().encode()));
        assertTrue(!t1.compact().encode().equals(ty.compact().encode()));
        assertTrue(!t1.compact().encode().equals(tz.compact().encode()));

        System.out.println("1: " + t1.normalize().encode());
        System.out.println("2: " + t2.normalize().encode());
        System.out.println("3: " + t3.normalize().encode());
        System.out.println("4: " + t4.normalize().encode());
        System.out.println("5: " + t5.normalize().encode());
        System.out.println("x: " + tx.normalize().encode());
        System.out.println("y: " + ty.normalize().encode());
        System.out.println("z: " + tz.normalize().encode());
        assertTrue(t1.normalize().encode().equals(t2.normalize().encode()));
        assertTrue(t1.normalize().encode().equals(t3.normalize().encode()));
        assertTrue(t1.normalize().encode().equals(t4.normalize().encode()));
        assertTrue(t1.normalize().encode().equals(t5.normalize().encode()));
        assertTrue(!t1.normalize().encode().equals(tx.normalize().encode()));
        assertTrue(!t1.normalize().encode().equals(ty.normalize().encode()));
        assertTrue(!t1.normalize().encode().equals(tz.normalize().encode()));

        assertEquals("({([c][d])([e][f])([a][b]{[x][y]([p][q])})})", t1.normalize().encode());
    }

    @Test
    public void testTraceDump() {
        {
            Trace big = new Trace();
            TraceNode b1 = new TraceNode();
            TraceNode b2 = new TraceNode();
            for (int i = 0; i < 100; ++i) {
                b2.addChild("test");
            }
            for (int i = 0; i < 10; ++i) {
                b1.addChild(b2);
            }
            for (int i = 0; i < 10; ++i) {
                big.getRoot().addChild(b1);
            }
            String normal = big.toString();
            String full = big.getRoot().toString();
            assertTrue(normal.length() > 30000);
            assertTrue(normal.length() < 32000);
            assertTrue(full.length() > 50000);
            assertEquals(normal.substring(0, 30000), full.substring(0, 30000));
        }
        {
            TraceNode s1 = new TraceNode();
            TraceNode s2 = new TraceNode();
            s2.addChild("test");
            s2.addChild("test");
            s1.addChild(s2);
            s1.addChild(s2);
            assertEquals("...\n", s1.toString(0));
            assertEquals("<trace>\n...\n", s1.toString(1));
            assertEquals("<trace>\n"      + // 8    8
                         "    <trace>\n"  + // 12  20
                         "        test\n" + // 13  33
                         "...\n", s1.toString(33));
            assertEquals("<trace>\n"  +     // 8   8
                         "    test\n" +     // 9  17
                         "    test\n" +     // 9  26
                         "...\n", s2.toString(26));
            assertEquals("<trace>\n"  +     // 8   8
                         "    test\n" +     // 9  17
                         "    test\n" +     // 9  26
                         "</trace>\n", s2.toString(27));
            assertEquals(s2.toString(27), s2.toString());
        }
    }

}
