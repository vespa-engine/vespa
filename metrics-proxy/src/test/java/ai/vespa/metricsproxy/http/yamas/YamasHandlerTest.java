// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.yamas;

import ai.vespa.metricsproxy.http.HttpHandlerTestBase;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.SlimeUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YamasHandlerTest extends HttpHandlerTestBase {

    private static final String VALUES_URI = URI_BASE + YamasHandler.VALUES_PATH;
    private static final String CONSUMERS_URI = URI_BASE + YamasHandler.CONSUMERS_PATH;

    private static String valuesResponse;
    private static String consumerResponse;

    @BeforeClass
    public static void setup() {
        YamasHandler handler = new YamasHandler(Executors.newSingleThreadExecutor(),
                getMetricsManager(),
                vespaServices,
                getMetricsConsumers(),
                getApplicationDimensions(),
                getNodeDimensions());
        testDriver = new RequestHandlerTestDriver(handler);
        valuesResponse = testDriver.sendRequest(VALUES_URI).readAll();
        consumerResponse = testDriver.sendRequest(CONSUMERS_URI).readAll();
    }


    @Test
    public void response_contains_consumer_list() {
        var slime = SlimeUtils.jsonToSlime(consumerResponse.getBytes());
        var consumers = new ArrayList<>();
        slime.get().field("consumers").traverse((ArrayTraverser) (idx, object) ->
            consumers.add(object.asString())
        );
        assertEquals(List.of("default", "custom-consumer"), consumers);
    }

    @Test
    public void value_response_does_not_contain_status() {
        assertFalse(valuesResponse.contains("status_code"));
        assertFalse(valuesResponse.contains("status_msg"));
    }

}
