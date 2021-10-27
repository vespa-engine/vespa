// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.metrics;

import ai.vespa.metricsproxy.metric.model.json.GenericJsonModel;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import org.junit.Before;
import org.junit.BeforeClass;

import java.util.concurrent.Executors;

import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.V1_PATH;
import static ai.vespa.metricsproxy.http.metrics.MetricsV1Handler.VALUES_PATH;

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
                                                        getMetricsConsumers());
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

}
