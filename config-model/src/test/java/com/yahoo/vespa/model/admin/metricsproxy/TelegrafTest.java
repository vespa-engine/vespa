// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.telegraf.Telegraf;
import ai.vespa.metricsproxy.telegraf.TelegrafConfig;
import ai.vespa.metricsproxy.telegraf.TelegrafRegistry;
import com.yahoo.component.ComponentId;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.self_hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 */
public class TelegrafTest {

    @Test
    void telegraf_components_are_set_up_when_cloudwatch_is_configured() {
        String services = servicesWithCloudwatch();
        VespaModel hostedModel = getModel(services, hosted);

        var clusterComponents = hostedModel.getAdmin().getMetricsProxyCluster().getComponentsMap();
        assertTrue(clusterComponents.containsKey(ComponentId.fromString(Telegraf.class.getName())));
        assertTrue(clusterComponents.containsKey(ComponentId.fromString(TelegrafRegistry.class.getName())));
    }

    @Test
    void telegraf_components_are_not_set_up_when_no_external_systems_are_added_in_services() {
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
        assertFalse(clusterComponents.containsKey(ComponentId.fromString(Telegraf.class.getName())));
        assertFalse(clusterComponents.containsKey(ComponentId.fromString(TelegrafRegistry.class.getName())));
    }

    @Test
    void telegraf_config_is_generated_for_cloudwatch_in_services() {
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
        assertEquals("default", cloudWatch0.profile());
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
    void multiple_cloudwatches_are_allowed_for_the_same_consumer() {
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
        assertEquals("default", cloudWatch0.profile());

        var cloudWatch1 = config.cloudWatch(1);
        assertEquals("cloudwatch-consumer", cloudWatch1.consumer());
        assertEquals("us-east-1", cloudWatch1.region());
        assertEquals("namespace-2", cloudWatch1.namespace());
        assertEquals("", cloudWatch1.accessKeyName());
        assertEquals("", cloudWatch1.secretKeyName());
        assertEquals("profile-2", cloudWatch1.profile());
    }

    @Test
    void profile_named_default_is_used_when_no_profile_is_given_in_shared_credentials() {
        String services = String.join("\n",
                "<services>",
                "    <admin version='2.0'>",
                "        <adminserver hostalias='node1'/>",
                "        <metrics>",
                "            <consumer id='cloudwatch-consumer'>",
                "                <metric id='my-metric'/>",
                "                <cloudwatch region='us-east-1' namespace='foo' >",
                "                    <shared-credentials file='/path/to/file' />",
                "                </cloudwatch>",
                "            </consumer>",
                "        </metrics>",
                "    </admin>",
                "</services>"
        );
        VespaModel model = getModel(services, self_hosted);
        TelegrafConfig config = model.getConfig(TelegrafConfig.class, CLUSTER_CONFIG_ID);
        assertEquals("default", config.cloudWatch(0).profile());
    }

}
