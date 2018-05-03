// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.yahoo.container.StatisticsConfig;
import static com.yahoo.container.StatisticsConfig.Values.Operations;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yahoo.statistics.Value.Parameters;
import org.junit.Test;

/**
 * Check correct statistics are generated for basic values.
 *
 * @author Steinar Knutsen
 */
public class ValueTestCase {

    private static final double delta = 0.0000000001;

    private static final String NALLE = "nalle";
    private static final double SECOND = 43.0d;
    private static final double FIRST = 42.0d;

    private static class TrivialCallback implements Callback {

        @Override
        public void run(Handle h, boolean firstRun) {
            Value v = (Value) h;
            if (firstRun) {
                v.put(FIRST);
            } else {
                v.put(SECOND);
            }
        }

    }

    @Test
    public void testMean() {
        Value v = new Value("thingie", Statistics.nullImplementation, new Parameters().setLogMean(true));
        v.put(1.0);
        v.put(2.0);
        v.put(4.0);
        v.put(-1.0);
        assertTrue("Mean should be 1.5", 1.5 == v.getMean());
        ValueProxy vp = v.getProxyAndReset();
        assertTrue("Proxy mean should be 1.5", 1.5 == vp.getMean());
        assertTrue("Value should have been reset.", 0.0d == v.getMean());
    }

    @Test
    public void testMin() {
        Value v = new Value("thingie", Statistics.nullImplementation, new Parameters().setLogMin(true));
        v.put(2.0);
        assertTrue("Min should be 2.0", 2.0 == v.getMin());
        v.put(1.0);
        assertTrue("Min should be 1.0", 1.0 == v.getMin());
        v.put(-1.0);
        v.put(4.0);
        assertTrue("Min should be -1.0", -1.0 == v.getMin());
    }

    @Test
    public void testMax() {
        Value v = new Value("thingie", Statistics.nullImplementation, new Parameters().setLogMax(true));
        v.put(-1.0);
        assertTrue("Max should be -1.0", -1.0 == v.getMax());
        v.put(1.0);
        v.put(2.0);
        assertTrue("Max should be 2.0", 2.0 == v.getMax());
        v.put(4.0);
        v.put(-1.0);
        assertTrue("Max should be 4.0", 4.0 == v.getMax());
    }

    @Test
    public void testHistogram() {
        Value v = new Value("thingie", Statistics.nullImplementation, new Parameters()
                .setLogHistogram(true).setHistogramId(HistogramType.REGULAR)
                .setLimits(new Limits(new double[] { 0.0, 1.0, 2.0 })));
        v.put(-1.0);
        v.put(0.0);
        v.put(1.0);
        v.put(2.0);
        v.put(3.0);
        assertTrue(v.toString().endsWith(
                " thingie (1) < 0.0 (1) < 1.0 (1) < 2.0 (2)"));
    }

    @Test
    public void testCallback() {
        Logger logger = Logger.getLogger(Value.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        Value v = new Value("thingie", Statistics.nullImplementation, new Parameters()
                .setLogRaw(true).setCallback(new TrivialCallback()));
        v.run();
        assertEquals(FIRST, v.get(), delta);
        v.run();
        assertEquals(SECOND, v.get(), delta);
        v.run();
        assertEquals(SECOND, v.get(), delta);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testParameter() {
        Value.Parameters p = new Value.Parameters().setLogInsertions(true)
                .setNameExtension(true).setAppendChar('_');
        Value.Parameters p2 = new Value.Parameters().setLogSum(true);
        assertNull(p2.appendChar);
        assertNull(p.logSum);
        p2.merge(p);
        assertEquals(Character.valueOf('_'), p2.appendChar);
        assertNull(p2.logMax);
        assertEquals(Boolean.TRUE, p2.logSum);
    }

    private class CheckHistogram extends Handler {
        volatile boolean gotRecord = false;
        volatile boolean gotWarning = false;
        final String histogram;
        final String representation;

        public CheckHistogram(String histogram, String representation) {
            this.histogram = histogram;
            this.representation = representation;
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getParameters() == null) {
                assertEquals(Value.HISTOGRAM_TYPE_WARNING + " '" + NALLE + "'", record.getMessage());
                gotWarning = true;
                return;
            }
            if (!(record.getParameters()[0] instanceof com.yahoo.log.event.Histogram)) {
                return;
            }
            com.yahoo.log.event.Histogram msg = (com.yahoo.log.event.Histogram) record.getParameters()[0];
            assertEquals(NALLE, msg.getValue("name"));
            assertEquals(histogram, msg.getValue("counts"));
            assertEquals(representation, msg.getValue("representation"));
            gotRecord = true;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    @Test
    public void testParameterFromConfig() {
        Logger logger = Logger.getLogger(Value.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        CheckHistogram h = new CheckHistogram("(0) < 0.0 (0) < 1.0 (0) < 2.0 (1)", "REGULAR");
        logger.addHandler(h);
        List<Operations.Arguments.Builder> histogram = Arrays.asList(new Operations.Arguments.Builder[] {
                new Operations.Arguments.Builder().key("limits").value("0, 1, 2")});
        List<Operations.Builder> ops = Arrays.asList(new Operations.Builder[] {
                new Operations.Builder().name(Operations.Name.Enum.MEAN),
                new Operations.Builder().name(Operations.Name.Enum.MIN),
                new Operations.Builder().name(Operations.Name.Enum.MAX),
                new Operations.Builder().name(Operations.Name.Enum.RAW),
                new Operations.Builder().name(Operations.Name.Enum.INSERTIONS),
                new Operations.Builder().name(Operations.Name.Enum.REGULAR).arguments(histogram),
                new Operations.Builder().name(Operations.Name.Enum.SUM) });
        StatisticsConfig c = new StatisticsConfig(
                new StatisticsConfig.Builder()
                        .values(new StatisticsConfig.Values.Builder().name(
                                NALLE).operations(ops)));
        MockStatistics m = new MockStatistics();
        m.config = c;
        Value v = Value.buildValue(NALLE, m, null);
        final double x = 79.0d;
        v.put(x);
        assertEquals(x, v.getMean(), delta);
        v.run();
        assertEquals(true, h.gotRecord);
        logger.removeHandler(h);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testReverseHistogram() {
        Logger logger = Logger.getLogger(Value.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        CheckHistogram h = new CheckHistogram("(0) < 0.0 (2) < 1.0 (2) < 2.0 (0)", "REGULAR");
        logger.addHandler(h);
        List<Operations.Arguments.Builder> histogram = Arrays.asList(new Operations.Arguments.Builder[] {
                new Operations.Arguments.Builder().key("limits").value("0, 1, 2")});
        List<Operations.Builder> ops = Arrays.asList(new Operations.Builder[] {
                new Operations.Builder().name(Operations.Name.Enum.REVERSE_CUMULATIVE).arguments(histogram) });
        StatisticsConfig c = new StatisticsConfig(
                new StatisticsConfig.Builder()
                        .values(new StatisticsConfig.Values.Builder().name(
                                NALLE).operations(ops)));
        MockStatistics m = new MockStatistics();
        m.config = c;
        Value v = Value.buildValue(NALLE, m, null);
        assertEquals(HistogramType.REGULAR.toString(), v.histogramId.toString());
        v.put(.5d);
        v.put(.5d);
        v.put(1.5d);
        v.put(1.5d);
        v.run();
        assertEquals(true, h.gotRecord);
        assertEquals(true, h.gotWarning);
        logger.removeHandler(h);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testCumulativeHistogram() {
        Logger logger = Logger.getLogger(Value.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        CheckHistogram h = new CheckHistogram("(0) < 0.0 (2) < 1.0 (2) < 2.0 (0)", "REGULAR");
        logger.addHandler(h);
        List<Operations.Arguments.Builder> histogram = Arrays.asList(new Operations.Arguments.Builder[] {
                new Operations.Arguments.Builder().key("limits").value("0, 1, 2")});
        List<Operations.Builder> ops = Arrays.asList(new Operations.Builder[] {
                new Operations.Builder().name(Operations.Name.Enum.CUMULATIVE).arguments(histogram) });
        StatisticsConfig c = new StatisticsConfig(
                new StatisticsConfig.Builder()
                        .values(new StatisticsConfig.Values.Builder().name(
                                NALLE).operations(ops)));
        MockStatistics m = new MockStatistics();
        m.config = c;
        Value v = Value.buildValue(NALLE, m, null);
        assertEquals(HistogramType.REGULAR.toString(), v.histogramId.toString());
        v.put(.5d);
        v.put(.5d);
        v.put(1.5d);
        v.put(1.5d);
        v.run();
        assertEquals(true, h.gotRecord);
        assertEquals(true, h.gotWarning);
        logger.removeHandler(h);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testObjectContracts() {
        final String valueName = "test";
        Value v = new Value(valueName, Statistics.nullImplementation, Value.defaultParameters());
        Value v2 = new Value(valueName, Statistics.nullImplementation, Value.defaultParameters());
        v2.put(1.0);
        assertEquals(v, v2);
        assertEquals(v.hashCode(), v2.hashCode());
        v2 = new Value("nalle", Statistics.nullImplementation, Value.defaultParameters());
        assertFalse("Different names should lead to different hashcodes",
                v.hashCode() == v2.hashCode());
        assertFalse("Different names should lead to equals() return false",
                v.equals(v2));
        String image = v.toString();
        String prefix = "com.yahoo.statistics.Value";
        assertEquals(prefix, image.substring(0, prefix.length()));
        assertEquals(valueName, image.substring(image.length() - valueName.length()));
    }

    public class MockStatistics implements Statistics {
        public StatisticsConfig config = null;
        public int registerCount = 0;

        @Override
        public void register(Handle h) {
            registerCount += 1;
        }

        @Override
        public void remove(String name) {
        }

        @Override
        public StatisticsConfig getConfig() {
            return config;
        }

        @Override
        public int purge() {
            return 0;
        }
    }

}
