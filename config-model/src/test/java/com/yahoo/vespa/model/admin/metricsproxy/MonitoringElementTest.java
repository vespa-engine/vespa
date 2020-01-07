package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.MonitoringConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class MonitoringElementTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void monitoring_element_is_disallowed_for_hosted_vespa() {
        String services = servicesWithMonitoringElement();
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The 'monitoring' element cannot be used");
        getModel(services, hosted);
    }

    @Test
    public void monitoring_element_is_allowed_for_self_hosted_vespa() {
        String services = servicesWithMonitoringElement();
        VespaModel model = getModel(services, self_hosted);

        var builder = new MonitoringConfig.Builder();
        model.getConfig(builder, CLUSTER_CONFIG_ID);
        MonitoringConfig config = builder.build();

        assertEquals(5, config.intervalMinutes());
        assertEquals("my-system", config.systemName());
    }

    private String servicesWithMonitoringElement() {
        return String.join("\n",
                           "<services>",
                           "    <admin version='4.0'>",
                           "        <monitoring interval='300' systemname='my-system' />",
                           "    </admin>",
                           "</services>"
        );
    }

}
