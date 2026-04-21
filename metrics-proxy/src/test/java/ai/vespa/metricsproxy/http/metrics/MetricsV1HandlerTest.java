// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import ai.vespa.metricsproxy.metric.model.json.GenericService;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.V1_PATH;
import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.VALUES_PATH;
import static ai.vespa.metricsproxy.metric.model.json.JacksonUtil.objectMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class MetricsV1HandlerTest extends MetricsHandlerTestBase<GenericJsonModel> {

    private static final String V1_URI = URI_BASE + V1_PATH;
    private static final String VALUES_URI = URI_BASE + VALUES_PATH;


    @BeforeClass
    public static void setup() {
        rootUri = V1_URI;
        valuesUri = VALUES_URI;
        var handler = new MetricsV1Handler(Executors.newSingleThreadExecutor(),
                                                        getMetricsManager(),
                                                        vespaServices,
                                                        getMetricsConsumers(),
                                                        getApplicationDimensions(),
                                                        getNodeDimensions());
        testDriver = new RequestHandlerTestDriver(handler);
    }

    @Before
    public void initModelClass() {
        modelClass = GenericJsonModel.class;
    }

    @Override
    GenericJsonModel getGenericJsonModel(GenericJsonModel genericJsonModel) {
        return genericJsonModel;
    }

    @Test
    public void response_contains_host_life_alive_metric() throws IOException {
        String response = testDriver.sendRequest(VALUES_URI).readAll();
        GenericJsonModel jsonModel = objectMapper().readValue(response, GenericJsonModel.class);

        GenericService hostLife = getService(jsonModel, "host_life");
        assertNotNull(hostLife);
        assertEquals(1, hostLife.metrics.size());
        assertEquals(1L, hostLife.metrics.get(0).values.get("alive").longValue());
    }

}
