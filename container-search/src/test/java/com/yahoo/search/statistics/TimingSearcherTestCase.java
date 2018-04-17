// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.Ping;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.statistics.TimingSearcher.Parameters;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimingSearcherTestCase {

    public static class MockValue extends Value {
        public int putCount = 0;

        public MockValue() {
            super("mock", Statistics.nullImplementation, new Value.Parameters());
        }

        @Override
        public void put(double x) {
            putCount += 1;
        }
    }

    @Test
    public void testMeasurementSearchPath() {
        Parameters p = new Parameters("timingtest", TimeTracker.Activity.SEARCH);
        TimingSearcher ts = new TimingSearcher(new ComponentId("lblblbl"), p, Statistics.nullImplementation);
        MockValue v = new MockValue();
        ts.setMeasurements(v);
        Execution exec = new Execution(ts, Execution.Context.createContextStub());
        Result r = exec.search(new Query("/?query=a"));
        Hit f = new Hit("blblbl");
        f.setFillable();
        r.hits().add(f);
        exec.fill(r, "whatever");
        exec.fill(r, "lalala");
        exec.ping(new Ping());
        exec.ping(new Ping());
        exec.ping(new Ping());
        assertEquals(1, v.putCount);
    }

    @Test
    public void testMeasurementFillPath() {
        Parameters p = new Parameters("timingtest", TimeTracker.Activity.FILL);
        TimingSearcher ts = new TimingSearcher(new ComponentId("lblblbl"), p, Statistics.nullImplementation);
        MockValue v = new MockValue();
        ts.setMeasurements(v);
        Execution exec = new Execution(ts, Execution.Context.createContextStub());
        Result r = exec.search(new Query("/?query=a"));
        Hit f = new Hit("blblbl");
        f.setFillable();
        r.hits().add(f);
        exec.fill(r, "whatever");
        exec.fill(r, "lalala");
        exec.ping(new Ping());
        exec.ping(new Ping());
        exec.ping(new Ping());
        assertEquals(2, v.putCount);
    }

    @Test
    public void testMeasurementPingPath() {
        Parameters p = new Parameters("timingtest", TimeTracker.Activity.PING);
        TimingSearcher ts = new TimingSearcher(new ComponentId("lblblbl"), p, Statistics.nullImplementation);
        MockValue v = new MockValue();
        ts.setMeasurements(v);
        Execution exec = new Execution(ts, Execution.Context.createContextStub());
        Result r = exec.search(new Query("/?query=a"));
        Hit f = new Hit("blblbl");
        f.setFillable();
        r.hits().add(f);
        exec.fill(r, "whatever");
        exec.fill(r, "lalala");
        exec.ping(new Ping());
        exec.ping(new Ping());
        exec.ping(new Ping());
        assertEquals(3, v.putCount);
    }

}
