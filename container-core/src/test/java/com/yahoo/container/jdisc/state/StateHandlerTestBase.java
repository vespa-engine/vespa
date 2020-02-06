// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.metrics.MetricsPresentationConfig;
import org.junit.After;
import org.junit.Before;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Simon Thoresen Hult
 * @author gjoranv
 */
public class StateHandlerTestBase {
    final static long SNAPSHOT_INTERVAL = TimeUnit.SECONDS.toMillis(300);
    final static long META_GENERATION = 69;
    static final String APPLICATION_NAME = "state-handler-test-base";
    TestDriver driver;
    StateMonitor monitor;
    Metric metric;
    final AtomicLong currentTimeMillis = new AtomicLong(0);

    @Before
    public void startTestDriver() {
        Timer timer = this.currentTimeMillis::get;
        this.driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Timer.class).toInstance(timer);
            }
        });
        ContainerBuilder builder = driver.newContainerBuilder();
        HealthMonitorConfig healthMonitorConfig =
                new HealthMonitorConfig(
                        new HealthMonitorConfig.Builder()
                                .snapshot_interval(TimeUnit.MILLISECONDS.toSeconds(SNAPSHOT_INTERVAL))
                                .initialStatus("up"));
        ThreadFactory threadFactory = ignored -> mock(Thread.class);
        this.monitor = new StateMonitor(healthMonitorConfig, timer, threadFactory);
        builder.guiceModules().install(new AbstractModule() {

            @Override
            protected void configure() {
                bind(StateMonitor.class).toInstance(monitor);
                bind(MetricConsumer.class).toProvider(MetricConsumerProviders.wrap(monitor));
                bind(ApplicationMetadataConfig.class).toInstance(new ApplicationMetadataConfig(
                        new ApplicationMetadataConfig.Builder().generation(META_GENERATION)));
                bind(MetricsPresentationConfig.class)
                        .toInstance(new MetricsPresentationConfig(new MetricsPresentationConfig.Builder()));
                bind(MetricsPacketsHandlerConfig.class).toInstance(new MetricsPacketsHandlerConfig(
                        new MetricsPacketsHandlerConfig.Builder().application(APPLICATION_NAME)));
            }
        });
        builder.serverBindings().bind("http://*/*", builder.getInstance(StateHandler.class));
        builder.serverBindings().bind("http://*/metrics-packets", builder.getInstance(MetricsPacketsHandler.class));
        driver.activateContainer(builder);
        metric = builder.getInstance(Metric.class);
    }

    @After
    public void stopTestDriver() {
        assertTrue(driver.close());
    }

    String requestAsString(String requestUri) throws Exception {
        final BufferedContentChannel content = new BufferedContentChannel();
        Response response = driver.dispatchRequest(requestUri, new ResponseHandler() {

            @Override
            public ContentChannel handleResponse(Response response) {
                return content;
            }
        }).get(60, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(Response.Status.OK, response.getStatus());
        StringBuilder str = new StringBuilder();
        Reader in = new InputStreamReader(content.toStream(), StandardCharsets.UTF_8);
        for (int c; (c = in.read()) != -1; ) {
            str.append((char)c);
        }
        return str.toString();
    }

    JsonNode requestAsJson(String requestUri) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(mapper.getFactory().createParser(requestAsString(requestUri)));
    }

    void incrementCurrentTimeAndAssertSnapshot(long val) {
        currentTimeMillis.addAndGet(val);
        assertTrue("Expected a new snapshot to be generated", monitor.checkTime());
    }

}
