// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log.event;

import java.util.logging.Logger;

import com.yahoo.log.VespaFormatter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EventTestCase {

    Count     countEvent;
    Count     floatCountEvent;
    Value     valueEvent;
    Value     floatValueEvent;
    Crash     crashEvent;
    Progress  progressEvent1;
    Progress  progressEvent2;
    Reloading reloadingEvent;
    Reloaded  reloadedEvent;
    Starting  startingEvent;
    Started   startedEvent;
    Stopping  stoppingEvent;
    Stopped   stoppedEvent;
    Collection collectionEvent;
    Unknown   unknownEvent;

    @Before
    public void setUp() {
        countEvent     = new Count("thecounter", 1234);
        floatCountEvent= new Count("thecounter", 1234.23);
        valueEvent     = new Value("thevalue", 4566);
        floatValueEvent= new Value("thevalue", 4566.23);
        crashEvent     = new Crash("appname", 1234, 11);
        progressEvent1 = new Progress("thename", 1);
        progressEvent2 = new Progress("thename", 1.0, 2.0);
        reloadingEvent = new Reloading("thefilewereloading");
        reloadedEvent  = new Reloaded("thefilewereloaded");
        startingEvent  = new Starting("startingName");
        startedEvent   = new Started("startedName");
        stoppingEvent  = new Stopping("stoppingName", "because we want to");
        stoppedEvent   = new Stopped("stoppedName", 1234, 1);
        collectionEvent=new Collection(123456, "thename");
        unknownEvent   = new Unknown();
    }

    // make sure we can make the test instances okay
    @Test
    public void testExists () {
        assertNotNull(countEvent);
        assertNotNull(floatCountEvent);
        assertNotNull(valueEvent);
        assertNotNull(floatValueEvent);
        assertNotNull(crashEvent);
        assertNotNull(progressEvent1);
        assertNotNull(progressEvent2);
        assertNotNull(reloadingEvent);
        assertNotNull(reloadedEvent);
        assertNotNull(startingEvent);
        assertNotNull(startedEvent);
        assertNotNull(stoppingEvent);
        assertNotNull(stoppedEvent);
        assertNotNull(collectionEvent);
        assertNotNull(unknownEvent);
    }

    @Test
    public void testEvents () {
        assertEquals("count/1 name=thecounter value=1234",
                     countEvent.toString());

        assertEquals("count/1 name=thecounter value=1234",
                     floatCountEvent.toString());

        assertEquals("crash/1 name=appname pid=1234 signal=11",
                     crashEvent.toString());

        assertEquals("progress/1 name=thename value=1.0 total=",
                     progressEvent1.toString());

        assertEquals("progress/1 name=thename value=1.0 total=2.0",
                     progressEvent2.toString());

        assertEquals("reloaded/1 name=thefilewereloaded",
                     reloadedEvent.toString());

        assertEquals("reloading/1 name=thefilewereloading",
                     reloadingEvent.toString());

        assertEquals("started/1 name=startedName",
                     startedEvent.toString());

        assertEquals("starting/1 name=startingName",
                     startingEvent.toString());

        assertEquals("stopping/1 name=stoppingName why=\"because we want to\"",
                     stoppingEvent.toString());

        assertEquals("collection/1 collectionId=123456 name=thename",
                     collectionEvent.toString());

        assertEquals("stopped/1 name=stoppedName pid=1234 exitcode=1",
                     stoppedEvent.toString());
    }

    /**
     * do the dirty work for testParser
     */
    private void parseEvent(Event e) throws MalformedEventException {
        assertEquals(e.toString(), Event.parse(e.toString()).toString());
    }

    @Test
    public void testParser () throws MalformedEventException {
        parseEvent(countEvent);
        parseEvent(floatCountEvent);
        parseEvent(crashEvent);
        parseEvent(progressEvent1);
        parseEvent(progressEvent2);
        parseEvent(reloadingEvent);
        parseEvent(reloadedEvent);
        parseEvent(startingEvent);
        parseEvent(startedEvent);
        parseEvent(stoppingEvent);
        parseEvent(stoppedEvent);
    }

    @Test
    public void testUnknownEvents () throws MalformedEventException {
        String s = "crappyevent/2 first=\"value one\" second=more third=4";
        Event event = Event.parse(s);
        assertEquals(s, event.toString());

        String s2 = "notinventedhere/999";
        assertEquals(s2, Event.parse(s2).toString());
    }

    /**
     * This test makes sure that the static event logging methods
     * successfully manage to find the right name on the calling
     * stack -- it should find the name of the calling class.
     */
    @Test
    public void testFindingCallingClassLogger() {
        SingleHandler sh = new SingleHandler();
        assertNull(sh.lastRecord());

        VespaFormatter formatter = new VespaFormatter();
        Logger log = Logger.getLogger(EventTestCase.class.getName());
        synchronized(log) {
            log.setUseParentHandlers(false);
            log.addHandler(sh);
            Event.starting("mintest");

            assertTrue(formatter.format(sh.lastRecord()).
                       indexOf("\t.com.yahoo.log.event.EventTestCase\tevent\tstarting/1 name=mintest") > -1);

            Event.starting("startingName");
            Event.started("startedName");
            Event.stopping("stoppingName", "whyParam");
            Event.stopped("stoppedName", 1, 2);
            Event.reloading("reloadingName");
            Event.reloaded("reloadedName");
            Event.count("countName", 1);
            Event.progress("progressName", 1, 2);
            Event.crash("crashName", 1, 2);
        }
    }

    @Test
    public void testFunnyEvent () {
        String funnyEvent = "collection/1 collectionId=1111111111 name=\"syncrows\" params=\"column=0 badrow=1 goodrow=0\"";
        try {
			Event e = Event.parse(funnyEvent);
        }
        catch (MalformedEventException e) {
            fail();
        }
    }

    @Test
    public void testFullParse () {
        try {
            Event event = Event.parse("count/1 name=\"data_searched_mb\" value=15115168.3940149993");
            assertTrue(event instanceof Count);
            assertEquals("data_searched_mb", event.getValue("name"));
            assertEquals("15115168", event.getValue("value"));
        } catch (MalformedEventException e) {
            fail("Malformed Event Exception on parsing");
        }
    }

}
