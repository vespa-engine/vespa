// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author hmusum
 */
public class JRTConfigRequestFactoryTest {
    private static final VespaVersion defaultVespaVersion = JRTConfigRequestFactory.getCompiledVespaVersion();

    @Test
    public void testCompressionType() {
        assertThat(JRTConfigRequestFactory.getCompressionType("", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", ""), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));

        assertThat(JRTConfigRequestFactory.getCompressionType("", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "LZ4"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "LZ4"), is(CompressionType.LZ4));

        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "LZ4"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "UNCOMPRESSED"), is(CompressionType.LZ4));
    }

    @Test
    public void testVespaVersion() {
        assertThat(JRTConfigRequestFactory.getVespaVersion().get(), is(defaultVespaVersion));
    }

    @Test
    public void testCreateFromSub() {
        Class<FunctionTestConfig> clazz = FunctionTestConfig.class;
        final String configId = "foo";
        TimingValues timingValues = new TimingValues();
        JRTConfigSubscription<FunctionTestConfig> sub =
                new JRTConfigSubscription<>(new ConfigKey<>(clazz, configId),
                                            new JRTConfigRequester(new JRTConnectionPool(new ConfigSourceSet("tcp/localhost:12345")), timingValues),
                                            timingValues);

        JRTClientConfigRequest request = JRTConfigRequestFactory.createFromSub(sub);
        assertThat(request.getVespaVersion().get(), is(defaultVespaVersion));
    }

    @Test
    public void testCreateFromRaw() {
        Class<FunctionTestConfig> clazz = FunctionTestConfig.class;
        final String configId = "foo";
        RawConfig config = new RawConfig(new ConfigKey<>(clazz, configId), "595f44fec1e92a71d3e9e77456ba80d1");

        JRTClientConfigRequest request = JRTConfigRequestFactory.createFromRaw(config, 1000);
        assertThat(request.getVespaVersion().get(), is(defaultVespaVersion));
    }

}
