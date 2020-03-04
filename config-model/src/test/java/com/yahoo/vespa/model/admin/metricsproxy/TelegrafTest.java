package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.telegraf.Telegraf;
import ai.vespa.metricsproxy.telegraf.TelegrafConfig;
import ai.vespa.metricsproxy.telegraf.TelegrafRegistry;
import com.yahoo.component.ComponentId;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class TelegrafTest {

    @Test
    public void telegraf_components_are_set_up_when_cloudwatch_is_configured() {
        String services = servicesWithCloudwatch();
        VespaModel hostedModel = getModel(services, hosted);

        var clusterComponents = hostedModel.getAdmin().getMetricsProxyCluster().getComponentsMap();
        assertThat(clusterComponents.keySet(), hasItem(ComponentId.fromString(Telegraf.class.getName())));
        assertThat(clusterComponents.keySet(), hasItem(ComponentId.fromString(TelegrafRegistry.class.getName())));
    }

    @Test
    public void telegraf_components_are_not_set_up_when_no_external_systems_are_added_in_services() {
        String services = String.join("\n",
                                      "<services>",
                                      "    <admin version='2.0'>",
                                      "        <adminserver hostalias='node1'/>",
                                      "        <metrics>",
                                      "            <consumer id='foo' />",
                                      "        </metrics>",
                                      "    </admin>",
                                      "</services>");
        VespaModel hostedModel = getModel(services, hosted);

        var clusterComponents = hostedModel.getAdmin().getMetricsProxyCluster().getComponentsMap();
        assertThat(clusterComponents.keySet(), not(hasItem(ComponentId.fromString(Telegraf.class.getName()))));
        assertThat(clusterComponents.keySet(), not(hasItem(ComponentId.fromString(TelegrafRegistry.class.getName()))));
    }

    @Test
    public void telegraf_config_is_generated_for_cloudwatch_in_services() {
        String services = servicesWithCloudwatch();
        VespaModel hostedModel = getModel(services, hosted);
        TelegrafConfig config = hostedModel.getConfig(TelegrafConfig.class, CLUSTER_CONFIG_ID);
        assertTrue(config.isHostedVespa());

        var cloudWatch0 = config.cloudWatch(0);
        assertEquals("cloudwatch-consumer", cloudWatch0.consumer());
        assertEquals("us-east-1", cloudWatch0.region());
        assertEquals("my-namespace", cloudWatch0.namespace());
        assertEquals("my-access-key", cloudWatch0.accessKeyName());
        assertEquals("my-secret-key", cloudWatch0.secretKeyName());
        assertEquals("", cloudWatch0.profile());
    }

    private String servicesWithCloudwatch() {
        return String.join("\n",
                           "<services>",
                           "    <admin version='2.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "        <metrics>",
                           "            <consumer id='cloudwatch-consumer'>",
                           "                <metric id='my-metric'/>",
                           "                <cloudwatch region='us-east-1' namespace='my-namespace' >",
                           "                    <credentials access-key-name='my-access-key' ",
                           "                                 secret-key-name='my-secret-key' />",
                           "                </cloudwatch>",
                           "            </consumer>",
                           "        </metrics>",
                           "    </admin>",
                           "</services>"
        );
    }

    @Test
    public void multiple_cloudwatches_are_allowed_for_the_same_consumer() {
        String services = String.join("\n",
                                      "<services>",
                                      "    <admin version='2.0'>",
                                      "        <adminserver hostalias='node1'/>",
                                      "        <metrics>",
                                      "            <consumer id='cloudwatch-consumer'>",
                                      "                <metric id='my-metric'/>",
                                      "                <cloudwatch region='us-east-1' namespace='namespace-1' >",
                                      "                    <credentials access-key-name='access-key-1' ",
                                      "                                 secret-key-name='secret-key-1' />",
                                      "                </cloudwatch>",
                                      "                <cloudwatch region='us-east-1' namespace='namespace-2' >",
                                      "                    <shared-credentials profile='profile-2' />",
                                      "                </cloudwatch>",
                                      "            </consumer>",
                                      "        </metrics>",
                                      "    </admin>",
                                      "</services>"
        );
        VespaModel hostedModel = getModel(services, hosted);
        TelegrafConfig config = hostedModel.getConfig(TelegrafConfig.class, CLUSTER_CONFIG_ID);

        var cloudWatch0 = config.cloudWatch(0);
        assertEquals("cloudwatch-consumer", cloudWatch0.consumer());
        assertEquals("us-east-1", cloudWatch0.region());
        assertEquals("namespace-1", cloudWatch0.namespace());
        assertEquals("access-key-1", cloudWatch0.accessKeyName());
        assertEquals("secret-key-1", cloudWatch0.secretKeyName());
        assertEquals("", cloudWatch0.profile());

        var cloudWatch1 = config.cloudWatch(1);
        assertEquals("cloudwatch-consumer", cloudWatch1.consumer());
        assertEquals("us-east-1", cloudWatch1.region());
        assertEquals("namespace-2", cloudWatch1.namespace());
        assertEquals("", cloudWatch1.accessKeyName());
        assertEquals("", cloudWatch1.secretKeyName());
        assertEquals("profile-2", cloudWatch1.profile());
    }

}
