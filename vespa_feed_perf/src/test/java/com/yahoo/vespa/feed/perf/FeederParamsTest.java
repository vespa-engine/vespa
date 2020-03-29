// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final String TESTFILE_JSON = "test.json";
    private static final String TESTFILE_VESPA = "test.vespa";
    private static final String TESTFILE_UNKNOWN = "test.xyz";

    @Test
    public void requireThatAccessorsWork() {
        FeederParams params = new FeederParams();

        PrintStream stdErr = new PrintStream(new ByteArrayOutputStream());
        params.setStdErr(stdErr);
        assertSame(stdErr, params.getStdErr());

        PrintStream stdOut = new PrintStream(new ByteArrayOutputStream());
        params.setStdOut(stdOut);
        assertSame(stdOut, params.getStdOut());

        params.setConfigId("my_config_id");
        assertEquals("my_config_id", params.getConfigId());

        assertFalse(params.isSerialTransferEnabled());
        params.setSerialTransfer();
        assertTrue(params.isSerialTransferEnabled());
    }

    @Test
    public void requireThatParamsHaveReasonableDefaults() {
        FeederParams params = new FeederParams();
        assertSame(System.in, params.getInputStreams().get(0));
        assertSame(System.err, params.getStdErr());
        assertSame(System.out, params.getStdOut());
        assertEquals(Route.parse("default"), params.getRoute());
        assertEquals("client", params.getConfigId());
        assertFalse(params.isSerialTransferEnabled());
    }

    @Test
    public void requireThatSerialTransferOptionIsParsed() throws ParseException, FileNotFoundException {
        assertTrue(new FeederParams().parseArgs("-s").isSerialTransferEnabled());
        assertTrue(new FeederParams().parseArgs("--serial").isSerialTransferEnabled());
        assertEquals(1, new FeederParams().parseArgs("-s").getMaxPending());
        assertEquals(1, new FeederParams().parseArgs("-s").getNumDispatchThreads());
    }

    @Test
    public void requireThatArgumentsAreParsedAsRoute() throws ParseException, FileNotFoundException {
        assertEquals(Route.parse("foo bar"), new FeederParams().parseArgs("-r", "foo bar").getRoute());
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
        assertEquals(17, new FeederParams().parseArgs("--numthreads", "17").getNumDispatchThreads());
    }
    @Test
    public void requireThatNumConnectionsAreParsed() throws ParseException, FileNotFoundException {
        assertEquals(1, new FeederParams().getNumConnectionsPerTarget());
        assertEquals(17, new FeederParams().parseArgs("-c 17").getNumConnectionsPerTarget());
        assertEquals(17, new FeederParams().parseArgs("--numconnections", "17").getNumConnectionsPerTarget());
    }

    @Test
    public void requireThatDumpStreamAreParsed() throws ParseException, IOException {
        assertNull(new FeederParams().getDumpStream());

        FeederParams p = new FeederParams().parseArgs("-o " + TESTFILE_JSON);
        assertNotNull(p.getDumpStream());
        assertEquals(FeederParams.DumpFormat.JSON, p.getDumpFormat());
        p.getDumpStream().close();

        p = new FeederParams().parseArgs("-o " + TESTFILE_VESPA);
        assertNotNull(p.getDumpStream());
        assertEquals(FeederParams.DumpFormat.VESPA, p.getDumpFormat());
        p.getDumpStream().close();

        p = new FeederParams().parseArgs("-o " + TESTFILE_UNKNOWN);
        assertNotNull(p.getDumpStream());
        assertEquals(FeederParams.DumpFormat.JSON, p.getDumpFormat());
        p.getDumpStream().close();

        assertTrue(new File(TESTFILE_JSON).delete());
        assertTrue(new File(TESTFILE_VESPA).delete());
        assertTrue(new File(TESTFILE_UNKNOWN).delete());
    }

    @Test
    public void requireThatInputFilesAreAggregated() throws ParseException, IOException {
        File json = new File(TESTFILE_JSON);
        File vespa = new File(TESTFILE_VESPA);
        new FileOutputStream(json).close();
        new FileOutputStream(vespa).close();
        FeederParams p = new FeederParams();
        p.parseArgs("-n", "3", TESTFILE_JSON, TESTFILE_VESPA);
        assertEquals(3, p.getNumDispatchThreads());
        assertEquals(2, p.getInputStreams().size());
        assertTrue(p.getInputStreams().get(0) instanceof BufferedInputStream);
        assertTrue(p.getInputStreams().get(1) instanceof BufferedInputStream);
        json.delete();
        vespa.delete();
    }

}
