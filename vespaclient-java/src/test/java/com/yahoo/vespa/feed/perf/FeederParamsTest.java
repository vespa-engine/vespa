// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.messagebus.routing.Route;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class FeederParamsTest {
    private static final String TESTFILE_JSON = "test.json";
    private static final String TESTFILE_VESPA = "test.vespa";
    private static final String TESTFILE_UNKNOWN = "test.xyz";
    private static final double EPSILON = 0.000000000001;


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
        assertEquals(16, new FeederParams().parseArgs("-c 16").getNumConnectionsPerTarget());
        assertEquals(17, new FeederParams().parseArgs("--numconnections", "17").getNumConnectionsPerTarget());
    }

    @Test
    public void requireThatTimeoutIsParsed() throws ParseException, FileNotFoundException {
        assertEquals(180.0, new FeederParams().getTimeout(), EPSILON);
        assertEquals(16.7, new FeederParams().parseArgs("-t 16.7").getTimeout(), EPSILON);
        assertEquals(1700.9, new FeederParams().parseArgs("--timeout", "1700.9").getTimeout(), EPSILON);
    }

    @Test
    public void requireThatNumMessagesToSendAreParsed() throws ParseException, FileNotFoundException {
        assertEquals(Long.MAX_VALUE, new FeederParams().getNumMessagesToSend());
        assertEquals(18, new FeederParams().parseArgs("-l 18").getNumMessagesToSend());
        assertEquals(19, new FeederParams().parseArgs("--nummessages", "19").getNumMessagesToSend());
    }

    @Test
    public void requireThatWindowSizeIncrementIsParsed() throws ParseException, FileNotFoundException {
        assertEquals(20, new FeederParams().getWindowIncrementSize());
        assertEquals(17, new FeederParams().parseArgs("--window_incrementsize", "17").getWindowIncrementSize());
    }

    @Test
    public void requireThatWindowSizeDecrementFactorIsParsed() throws ParseException, FileNotFoundException {
        assertEquals(1.2, new FeederParams().getWindowDecrementFactor(), EPSILON);
        assertEquals(1.3, new FeederParams().parseArgs("--window_decrementfactor", "1.3").getWindowDecrementFactor(), EPSILON);
    }

    @Test
    public void requireThatWindowResizeRateIsParsed() throws ParseException, FileNotFoundException {
        assertEquals(3.0, new FeederParams().getWindowResizeRate(), EPSILON);
        assertEquals(5.5, new FeederParams().parseArgs("--window_resizerate", "5.5").getWindowResizeRate(), EPSILON);
    }

    @Test
    public void requireThatWindowBackOffIsParsed() throws ParseException, FileNotFoundException {
        assertEquals(0.95, new FeederParams().getWindowSizeBackOff(), EPSILON);
        assertEquals(0.97, new FeederParams().parseArgs("--window_backoff", "0.97").getWindowSizeBackOff(), EPSILON);
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
