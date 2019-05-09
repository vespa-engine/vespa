package com.yahoo.vespa.model.admin.metricsproxy;

import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.VespaModelTester;
import org.junit.Test;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class MetricsProxyContainerClusterTest {

    @Test
    public void one_metrics_proxy_container_is_added_to_every_node() {
        var numberOfHosts = 4;
        var tester = new VespaModelTester();
        tester.enableMetricsProxyContainer(true);
        tester.addHosts(4);

        VespaModel model = tester.createModel(servicesXml(), true);
        assertThat(model.getRoot().getHostSystem().getHosts().size(), is(numberOfHosts));

        for (var host : model.getHostSystem().getHosts()) {
            assertThat(host.getService(METRICS_PROXY_CONTAINER.serviceName), notNullValue());
        }

    }

    private String servicesXml() {
        return String.join("\n", "<?xml version='1.0' encoding='utf-8' ?>",
                           "<services>",
                           "  <container version='1.0' id='foo'>",
                           "    <nodes count='2'/>",
                           "  </container>",
                           "  <content id='my-content' version='1.0'>",
                           "    <documents />",
                           "    <nodes count='2'/>",
                           "  </content>",
                           "</services>");
    }
}
