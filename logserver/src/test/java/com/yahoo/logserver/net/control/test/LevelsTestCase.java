// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net.control.test;

import com.yahoo.logserver.net.control.Levels;
import com.yahoo.logserver.net.control.State;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Bjorn Borud
 */
public class LevelsTestCase {
    /**
     * Make sure the parsing works
     */
    @Test
    public void testParser() {
        Levels levels = Levels.parse("fatal=forward,error=forward,warning=noforward");
        assertNotNull(levels);
        assertSame(State.FORWARD, levels.getLevelState("fatal"));
        assertSame(State.FORWARD, levels.getLevelState("error"));
        assertSame(State.NOFORWARD, levels.getLevelState("warning"));
    }

    /**
     * Ensure that the Levels are in a known default state.  A Levels
     * object that was newly created is in a defined default state
     * which has been deemed to be "reasonable".  In general, it will
     * specify forwarding all log levels except "debug" and it will
     * specify "spam" to be turned off.
     */
    @Test
    public void testDefaultLevels() {
        Levels levels = new Levels();
        assertSame(State.FORWARD, levels.getLevelState("event"));
        assertSame(State.FORWARD, levels.getLevelState("fatal"));
        assertSame(State.FORWARD, levels.getLevelState("error"));
        assertSame(State.FORWARD, levels.getLevelState("warning"));
        assertSame(State.FORWARD, levels.getLevelState("info"));
        assertSame(State.FORWARD, levels.getLevelState("config"));
        assertSame(State.NOFORWARD, levels.getLevelState("debug"));
        assertSame(State.NOFORWARD, levels.getLevelState("spam"));
    }

    /**
     * This test also documents/verifies the default behavior
     * of the Levels class.
     */
    @Test
    public void testToString() {
        String in = "fatal=forward,error=forward,warning=noforward";
        String out = "event=forward,fatal=forward,error=forward,warning=noforward,info=forward,config=forward,debug=noforward,spam=noforward";
        Levels levels = Levels.parse(in);
        assertEquals(out, levels.toString());

    }

    /**
     * Clone testing
     */
    @Test
    public void testClone() {
        Levels l1 = Levels.parse("error=noforward");
        assertEquals(l1.toString(), l1.clone().toString());
        assertSame(State.NOFORWARD, l1.getLevelState("error"));
        assertSame(State.NOFORWARD, ((Levels) l1.clone()).getLevelState("error"));
        assertSame(l1.getLevelState("error"),
                   ((Levels) l1.clone()).getLevelState("error"));
    }

    /**
     * test parser
     */
    @Test
    public void testUpdateLevels() {
        Levels l1 = Levels.parse("error=noforward");
        assertSame(State.NOFORWARD, l1.getLevelState("error"));

        // should be unaffected
        assertSame(State.FORWARD, l1.getLevelState("warning"));

        // update and check that the update worked
        l1.updateLevels("error=noforward");
        assertSame(State.NOFORWARD, l1.getLevelState("error"));

        // should be unaffected
        assertSame(State.FORWARD, l1.getLevelState("warning"));
    }
}
