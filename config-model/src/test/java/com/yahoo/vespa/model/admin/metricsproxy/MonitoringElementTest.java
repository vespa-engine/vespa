// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.core.MonitoringConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author gjoranv
 */
public class MonitoringElementTest {

    @Test
    void monitoring_element_is_disallowed_for_hosted_vespa() {
        String services = servicesWithMonitoringElement();
        try {
            getModel(services, hosted);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The 'monitoring' element cannot be used on hosted Vespa.", e.getMessage());
        }
    }

    @Test
    void monitoring_element_is_allowed_for_hosted_infrastructure_apps() {
        String services = String.join("\n",
                "<services application-type='hosted-infrastructure'>",
                "    <admin version='4.0'>",
                "        <monitoring interval='300' systemname='my-system' />",
                "    </admin>",
                "</services>"
        );
        VespaModel model = getModel(services, hosted);
        assertMonitoringConfig(model);
    }

    @Test
    void monitoring_element_is_allowed_for_self_hosted_vespa() {
        String services = servicesWithMonitoringElement();
        VespaModel model = getModel(services, self_hosted);
        assertMonitoringConfig(model);
    }

    private void assertMonitoringConfig(VespaModel model) {
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
