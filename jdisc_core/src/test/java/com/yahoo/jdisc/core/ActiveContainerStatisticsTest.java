package com.yahoo.jdisc.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class ActiveContainerStatisticsTest {

    @Test
    public void counts_deactivated_activecontainers() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ActiveContainerStatistics stats = new ActiveContainerStatistics();
        MockMetric metric = new MockMetric();

        ActiveContainer containerWithoutRetainedResources = new ActiveContainer(driver.newContainerBuilder());

        stats.onActivated(containerWithoutRetainedResources);
        stats.emitMetrics(metric);
        assertEquals(0, metric.totalCount);
        assertEquals(0, metric.withRetainedReferencesCount);

        stats.onDeactivated(containerWithoutRetainedResources);
        containerWithoutRetainedResources.release();
        stats.emitMetrics(metric);
        assertEquals(1, metric.totalCount);
        assertEquals(0, metric.withRetainedReferencesCount);

        ActiveContainer containerWithRetainedResources = new ActiveContainer(driver.newContainerBuilder());

        try (ResourceReference ignoredRef = containerWithRetainedResources.refer()) {
             stats.onActivated(containerWithRetainedResources);
             stats.onDeactivated(containerWithRetainedResources);
             containerWithRetainedResources.release();
             stats.emitMetrics(metric);
             assertEquals(2, metric.totalCount);
             assertEquals(1, metric.withRetainedReferencesCount);
        }

    }

    private static class MockMetric implements Metric {
        public int totalCount;
        public int withRetainedReferencesCount;

        @Override
        public void set(String key, Number val, Context ctx) {
            switch (key) {
                case ActiveContainerStatistics.Metrics.TOTAL_DEACTIVATED_CONTAINERS:
                    totalCount = val.intValue();
                    break;
                case ActiveContainerStatistics.Metrics.DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES:
                    withRetainedReferencesCount = val.intValue();
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public void add(String key, Number val, Context ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Context createContext(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }
    }

}
