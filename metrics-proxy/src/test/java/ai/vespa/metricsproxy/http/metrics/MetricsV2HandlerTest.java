// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.metric.model.json.GenericApplicationModel;
import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.http.metrics.MetricsV2Handler.V2_PATH;
import static ai.vespa.metricsproxy.http.metrics.MetricsV2Handler.VALUES_PATH;

/**
 * @author gjoranv
 */
@SuppressWarnings("UnstableApiUsage")
public class MetricsV2HandlerTest extends MetricsHandlerTestBase<GenericApplicationModel> {

    private static final String V2_URI = URI_BASE + V2_PATH;
    private static final String VALUES_URI = URI_BASE + VALUES_PATH;


    @BeforeClass
    public static void setup() {
        rootUri = V2_URI;
        valuesUri = VALUES_URI;
        var handler = new MetricsV2Handler(Executors.newSingleThreadExecutor(),
                                           getMetricsManager(),
                                           vespaServices,
                                           getMetricsConsumers(),
                                           nodeInfoConfig());
        testDriver = new RequestHandlerTestDriver(handler);
    }

    @Before
    public void initModelClass() {
        modelClass = GenericApplicationModel.class;
    }

    @Override
    GenericJsonModel getGenericJsonModel(GenericApplicationModel genericApplicationModel) {
        return genericApplicationModel.nodes.get(0);
    }

    private static NodeInfoConfig nodeInfoConfig() {
        return new NodeInfoConfig.Builder()
                .role("my-role")
                .hostname("my-hostname")
                .build();
    }
}
