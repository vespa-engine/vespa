// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Timer;
import com.yahoo.metrics.MetricsPresentationConfig;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author gjoranv
 */
public class StateHandlerTestBase {
    final static long SNAPSHOT_INTERVAL = TimeUnit.SECONDS.toMillis(300);
    final static long META_GENERATION = 69;

    static final String URI_BASE = "http://localhost";

    static StateMonitor monitor;
    static RequestHandlerTestDriver testDriver;

    static HealthMonitorConfig healthMonitorConfig;
    static ApplicationMetadataConfig applicationMetadataConfig;
    static MetricsPacketsHandlerConfig metricsPacketsHandlerConfig;

    final AtomicLong currentTimeMillis = new AtomicLong(0);
    Timer timer;

    MockSnapshotProvider snapshotProvider;
    ComponentRegistry<SnapshotProvider> snapshotProviderRegistry;

    @BeforeClass
    public static void setupClass() {
        healthMonitorConfig = new HealthMonitorConfig(new HealthMonitorConfig.Builder()
                                                              .initialStatus("up"));
        applicationMetadataConfig = new ApplicationMetadataConfig(new ApplicationMetadataConfig.Builder()
                                                                          .generation(META_GENERATION));
    }

    @Before
    public void setupSnapshotProvider() {
        timer = currentTimeMillis::get;
        snapshotProvider = new MockSnapshotProvider();
        snapshotProviderRegistry = new ComponentRegistry<>();
        snapshotProviderRegistry.register(new ComponentId("foo"), snapshotProvider);
        monitor = new StateMonitor(healthMonitorConfig);
    }

    String requestAsString(String requestUri) {
        return testDriver.sendRequest(requestUri).readAll();
    }

    JsonNode requestAsJson(String requestUri) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(mapper.getFactory().createParser(requestAsString(requestUri)));
    }

    void advanceToNextSnapshot() {
        currentTimeMillis.addAndGet(SNAPSHOT_INTERVAL);
    }

}
