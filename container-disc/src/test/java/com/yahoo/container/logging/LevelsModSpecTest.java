// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arnej
 */
public class LevelsModSpecTest {

    @Test
    public void hasCorrectDefault() {
        String wanted = "fatal=on,error=on,warning=on,info=on,event=on,config=on,debug=off,spam=off";
        var l = new LevelsModSpec();
        assertEquals(wanted, l.toLogctlModSpec());
    }

    @Test
    public void canTurnInfoOff() {
        String wanted = "fatal=on,error=on,warning=on,info=off,event=on,config=on,debug=off,spam=off";
        var l = new LevelsModSpec();
        l.setLevels("-info");
        assertEquals(wanted, l.toLogctlModSpec());
    }

    @Test
    public void canTurnDebugOn() {
        String wanted = "fatal=on,error=on,warning=on,info=on,event=on,config=on,debug=on,spam=off";
        var l = new LevelsModSpec();
        l.setLevels("+debug");
        assertEquals(wanted, l.toLogctlModSpec());
    }

    @Test
    public void canSpeficyOnlySome() {
        String wanted = "fatal=off,error=off,warning=on,info=off,event=off,config=off,debug=on,spam=off";
        var l = new LevelsModSpec();
        l.setLevels("warning debug");
        assertEquals(wanted, l.toLogctlModSpec());
        l = new LevelsModSpec();
        l.setLevels("warning,debug");
        assertEquals(wanted, l.toLogctlModSpec());
        l = new LevelsModSpec();
        l.setLevels("warning, debug");
        assertEquals(wanted, l.toLogctlModSpec());
    }

    @Test
    public void canSpeficyAllMinusSome() {
        String wanted ="fatal=on,error=on,warning=on,info=off,event=on,config=on,debug=on,spam=off";
        var l = new LevelsModSpec();
        l.setLevels("all -info -spam");
        assertEquals(wanted, l.toLogctlModSpec());
        l = new LevelsModSpec();
        l.setLevels("all,-info,-spam");
        assertEquals(wanted, l.toLogctlModSpec());
        l = new LevelsModSpec();
        l.setLevels("all, -info, -spam");
        assertEquals(wanted, l.toLogctlModSpec());
    }

}
