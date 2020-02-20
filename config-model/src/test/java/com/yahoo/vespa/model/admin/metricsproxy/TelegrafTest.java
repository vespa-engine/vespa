package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.telegraf.TelegrafConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;

import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.CLUSTER_CONFIG_ID;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.TestMode.hosted;
import static com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyModelTester.getModel;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class TelegrafTest {

    @Test
    public void telegraf_config_is_generated_for_cloudwatch_in_services() {
        String services = String.join("\n",
                                      "<services>",
                                      "    <admin version='2.0'>",
                                      "        <adminserver hostalias='node1'/>",
                                      "        <metrics>",
                                      "            <consumer id='cloudwatch-consumer'>",
                                      "                <metric id='my-metric'/>",
                                      "                <cloudwatch region='us-east-1' namespace='my-namespace' >",
                                      "                    <access-key-name>my-access-key</access-key-name>",
                                      "                    <secret-key-name>my-secret-key</secret-key-name>",
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
        assertEquals("my-namespace", cloudWatch0.namespace());
        assertEquals("my-access-key", cloudWatch0.accessKeyName());
        assertEquals("my-secret-key", cloudWatch0.secretKeyName());
        assertEquals("", cloudWatch0.profile());
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
                                      "                    <access-key-name>access-key-1</access-key-name>",
                                      "                    <secret-key-name>secret-key-1</secret-key-name>",
                                      "                </cloudwatch>",
                                      "                <cloudwatch region='us-east-1' namespace='namespace-2' >",
                                      "                    <profile>profile-2</profile>",
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
