// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.ParseException;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class FeederParamsTest {

    @Test
    public void requireThatAccessorsWork() {
        FeederParams params = new FeederParams();

        InputStream stdIn = Mockito.mock(InputStream.class);
        params.setStdIn(stdIn);
        assertSame(stdIn, params.getStdIn());

        PrintStream stdErr = Mockito.mock(PrintStream.class);
        params.setStdErr(stdErr);
        assertSame(stdErr, params.getStdErr());

        PrintStream stdOut = Mockito.mock(PrintStream.class);
        params.setStdOut(stdOut);
        assertSame(stdOut, params.getStdOut());

        Route route = Route.parse("my_route");
        params.setRoute(route);
        assertEquals(route, params.getRoute());
        assertNotSame(route, params.getRoute());

        params.setConfigId("my_config_id");
        assertEquals("my_config_id", params.getConfigId());

        params.setSerialTransfer(false);
        assertFalse(params.isSerialTransferEnabled());
        params.setSerialTransfer(true);
        assertTrue(params.isSerialTransferEnabled());
    }

    @Test
    public void requireThatParamsHaveReasonableDefaults() {
        FeederParams params = new FeederParams();
        assertSame(System.in, params.getStdIn());
        assertSame(System.err, params.getStdErr());
        assertSame(System.out, params.getStdOut());
        assertEquals(Route.parse("default"), params.getRoute());
        assertEquals("client", params.getConfigId());
        assertFalse(params.isSerialTransferEnabled());
    }

    @Test
    public void requireThatSerialTransferOptionIsParsed() throws ParseException {
        assertTrue(new FeederParams().parseArgs("-s").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("foo", "-s").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("-s", "foo").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("--serial").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("foo", "--serial").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("--serial", "foo").isSerialTransferEnabled());
    }

    @Test
    public void requireThatArgumentsAreParsedAsRoute() throws ParseException {
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("foo", "bar").getRoute());
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("-s", "foo", "bar").getRoute());
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("foo", "-s", "bar").getRoute());
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("foo", "bar", "-s").getRoute());
    }

    @Test
    public void requireThatRouteIsAnOptionalArgument() throws ParseException {
        assertEquals(Route.parse("default"), new FeederParams().parseArgs().getRoute());
        assertEquals(Route.parse("default"), new FeederParams().parseArgs("-s").getRoute());
    }

}
