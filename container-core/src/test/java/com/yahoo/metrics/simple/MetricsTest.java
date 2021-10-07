// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.metrics.ManagerConfig;
import com.yahoo.metrics.simple.jdisc.JdiscMetricsFactory;
import com.yahoo.metrics.simple.jdisc.SimpleMetricConsumer;

/**
 * Functional test for simple metric implementation.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class MetricsTest extends UnitTestSetup {
    SimpleMetricConsumer metricApi;

    @Before
    public void setUp() throws Exception {
        super.init();
        metricApi  = (SimpleMetricConsumer) new JdiscMetricsFactory(metricManager.get()).newInstance();
    }

    @After
    public void tearDown() throws Exception {
        super.fini();
    }

    @Test
    public final void smokeTest() throws InterruptedException {
        final String metricName = "testMetric";
        metricApi.set(metricName, Double.valueOf(1.0d), null);
        updater.gotData.await(10, TimeUnit.SECONDS);
        Bucket s = getUpdatedSnapshot();
        Collection<Entry<Point, UntypedMetric>> values = s.getValuesForMetric(metricName);
        assertEquals(1, values.size());
        Entry<Point, UntypedMetric> value = values.iterator().next();
        assertEquals(Point.emptyPoint(), value.getKey());
        assertEquals(1.0d, value.getValue().getLast(), 0.0d); // using number exactly expressible as doubles
        assertEquals(1L, value.getValue().getCount());
    }

    @Test
    public final void testRedefinition() {
        MetricReceiver r = metricManager.get();
        final String metricName = "gah";
        r.addMetricDefinition(metricName, new MetricSettings.Builder().build());
        r.addMetricDefinition(metricName, new MetricSettings.Builder().histogram(true).build());
        assertTrue(r.getMetricDefinition(metricName).isHistogram());
    }

}
