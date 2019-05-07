/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.TestUtil;
import ai.vespa.metricsproxy.metric.Metric;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Unknown
 */
public class ContainerServiceTest {

    private MockHttpServer service;
    private int csPort;

    @BeforeClass
    public static void init() {
        HttpMetricFetcher.CONNECTION_TIMEOUT = 60000; // 60 secs in unit tests
    }

    @Before
    public void setupHTTPServer() {
        csPort = 18637; // see factory/doc/port-ranges.txt
        try {
            String response = TestUtil.getContents("metrics-container-state-multi-chain.json");
            service = new MockHttpServer(csPort, response, HttpMetricFetcher.METRICS_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMultipleQueryDimensions() throws JSONException {
        int count = 0;
        VespaService service = VespaService.create("service1", "id", csPort);
        for (Metric m : service.getMetrics().getMetrics()) {
            if (m.getName().equals("queries.rate")) {
                count++;
                System.out.println("Name: " + m.getName() + " value: " + m.getValue());
                if (m.getDimensions().get(toDimensionId("chain")).equals("asvBlendingResult")) {
                    assertThat((Double)m.getValue(), is(26.4));
                } else if (m.getDimensions().get(toDimensionId("chain")).equals("blendingResult")) {
                    assertThat((Double)m.getValue(), is(0.36666666666666664));
                } else {
                    assertThat("Unknown unknown chain", false, is(true));
                }
            }
        }
        assertThat(count, is(2));
    }

    @After
    public void shutdown() {
        this.service.close();
    }
}
