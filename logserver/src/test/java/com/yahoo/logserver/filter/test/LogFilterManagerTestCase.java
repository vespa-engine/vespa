// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter.test;

import com.yahoo.logserver.filter.LogFilter;
import com.yahoo.logserver.filter.LogFilterManager;
import com.yahoo.logserver.filter.LevelFilter;
import com.yahoo.logserver.filter.MetricsFilter;
import com.yahoo.logserver.filter.NoMetricsFilter;
import com.yahoo.logserver.filter.NullFilter;
import com.yahoo.logserver.filter.MuteFilter;

import org.junit.*;

import static org.junit.Assert.*;

public class LogFilterManagerTestCase {

    @Test
    public void testSystemFilters() {
        LogFilter f;

        f = LogFilterManager.getLogFilter("system.allevents");
        assertNotNull(f);
        assertTrue(f instanceof LevelFilter);

        f = LogFilterManager.getLogFilter("system.metricsevents");
        assertNotNull(f);
        assertTrue(f instanceof MetricsFilter);

        f = LogFilterManager.getLogFilter("system.nometricsevents");
        assertNotNull(f);
        assertTrue(f instanceof NoMetricsFilter);


        f = LogFilterManager.getLogFilter("system.all");
        assertNotNull(f);
        assertTrue(f instanceof NullFilter);

        f = LogFilterManager.getLogFilter("system.mute");
        assertNotNull(f);
        assertTrue(f instanceof MuteFilter);
        assertTrue(f == MuteFilter.getInstance());
    }
}
