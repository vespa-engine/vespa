// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class FeederParamsTest {
    static final String TESTFILE = "test.json";

    @Test
    public void requireThatAccessorsWork() {
        FeederParams params = new FeederParams();

        InputStream stdIn = new ByteArrayInputStream(new byte[1]);
        params.setStdIn(stdIn);
        assertSame(stdIn, params.getStdIn());

        PrintStream stdErr = new PrintStream(new ByteArrayOutputStream());
        params.setStdErr(stdErr);
        assertSame(stdErr, params.getStdErr());

        PrintStream stdOut = new PrintStream(new ByteArrayOutputStream());
        params.setStdOut(stdOut);
        assertSame(stdOut, params.getStdOut());

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
    public void requireThatSerialTransferOptionIsParsed() throws ParseException, FileNotFoundException {
        assertTrue(new FeederParams().parseArgs("-s").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("foo", "-s").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("-s", "foo").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("--serial").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("foo", "--serial").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("--serial", "foo").isSerialTransferEnabled());
    }

    @Test
    public void requireThatArgumentsAreParsedAsRoute() throws ParseException, FileNotFoundException {
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("-r foo bar").getRoute());
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("--route","foo bar").getRoute());
    }

    @Test
    public void requireThatRouteIsAnOptionalArgument() throws ParseException, FileNotFoundException {
        assertEquals(Route.parse("default"), new FeederParams().parseArgs().getRoute());
        assertEquals(Route.parse("default"), new FeederParams().parseArgs("-s").getRoute());
    }

    @Test
    public void requireThatNumThreadsAreParsed() throws ParseException, FileNotFoundException {
        assertEquals(1, new FeederParams().getNumDispatchThreads());
        assertEquals(17, new FeederParams().parseArgs("-n 17").getNumDispatchThreads());
    }

    @Test
    public void requireThatDumpStreamAreParsed() throws ParseException, IOException {
        assertNull(new FeederParams().getDumpStream());
        OutputStream dumpStream = new FeederParams().parseArgs("-o " + TESTFILE).getDumpStream();
        assertNotNull(dumpStream);
        dumpStream.close();
        assertTrue(new File(TESTFILE).delete());
    }

}
