// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import ai.vespa.metricsproxy.TestUtil;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class TelegrafTest {

    @Test
    public void test_writing_correct_telegraf_plugin_config() {
        TelegrafConfig telegrafConfig = new TelegrafConfig.Builder()
                .cloudWatch(
                        new TelegrafConfig.CloudWatch.Builder()
                                .accessKeyName("accessKey1")
                                .namespace("namespace1")
                                .secretKeyName("secretKey1")
                                .region("us-east-1")
                                .consumer("consumer1")
                )
                .cloudWatch(
                        new TelegrafConfig.CloudWatch.Builder()
                                .namespace("namespace2")
                                .profile("awsprofile")
                                .region("us-east-2")
                                .consumer("consumer2")
                )
                .intervalSeconds(300)
                .isHostedVespa(true)
                .build();
        StringWriter stringWriter = new StringWriter();
        String logFilePath = "/path/to/logs/telegraf/telegraf.log";
        Telegraf.writeConfig(telegrafConfig, stringWriter, logFilePath);
        String expectedConfig = TestUtil.getFileContents( "telegraf-config-with-two-cloudwatch-plugins.txt");
        assertEquals(expectedConfig, stringWriter.toString());
    }

}
