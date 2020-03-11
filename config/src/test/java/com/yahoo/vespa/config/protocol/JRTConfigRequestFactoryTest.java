// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.foo.FunctionTestConfig;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author hmusum
 */
public class JRTConfigRequestFactoryTest {
    private static VespaVersion defaultVespaVersion = JRTConfigRequestFactory.getCompiledVespaVersion();

    @Test
    public void testGetProtocolVersion() {
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "", ""), is("3"));

        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "", ""), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "1", ""), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "", "1"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "1", ""), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "", "1"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "1", "1"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "1", "1"), is("1"));

        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "", ""), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "2", ""), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "", "2"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "2", ""), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "", "2"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "2", "2"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "2", "2"), is("2"));

        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "2", ""), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "", "2"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "1", "2"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "1", ""), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "", "1"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("", "2", "1"), is("2"));

        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "2", "2"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "1", "2"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("1", "2", "1"), is("1"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "1", "1"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "1", "2"), is("2"));
        assertThat(JRTConfigRequestFactory.getProtocolVersion("2", "2", "1"), is("2"));
    }

    @Test
    public void testCompressionType() {
        assertThat(JRTConfigRequestFactory.getCompressionType("", "", ""), is(CompressionType.LZ4));

        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "", ""), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "UNCOMPRESSED", ""), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "UNCOMPRESSED", ""), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "UNCOMPRESSED", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "UNCOMPRESSED", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));

        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "LZ4", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "", "LZ4"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "LZ4", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "", "LZ4"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "LZ4", "LZ4"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "LZ4", "LZ4"), is(CompressionType.LZ4));

        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "LZ4", ""), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "", "LZ4"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "UNCOMPRESSED", "LZ4"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "UNCOMPRESSED", ""), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "", "UNCOMPRESSED"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("", "LZ4", "UNCOMPRESSED"), is(CompressionType.LZ4));

        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "LZ4", "LZ4"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "UNCOMPRESSED", "LZ4"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("UNCOMPRESSED", "LZ4", "UNCOMPRESSED"), is(CompressionType.UNCOMPRESSED));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "UNCOMPRESSED", "UNCOMPRESSED"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "UNCOMPRESSED", "LZ4"), is(CompressionType.LZ4));
        assertThat(JRTConfigRequestFactory.getCompressionType("LZ4", "LZ4", "UNCOMPRESSED"), is(CompressionType.LZ4));
    }

    @Test
    public void testVespaVersion() {
        assertThat(JRTConfigRequestFactory.getVespaVersion().get(), is(defaultVespaVersion));
    }

    @Test
    public void testCreateFromSub() {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        Class<FunctionTestConfig> clazz = FunctionTestConfig.class;
        final String configId = "foo";
        JRTConfigSubscription<FunctionTestConfig> sub = new JRTConfigSubscription<>(
                new ConfigKey<>(clazz, configId), subscriber, new ConfigSet(), new TimingValues());

        // Default vespa version
        JRTClientConfigRequest request = JRTConfigRequestFactory.createFromSub(sub);
        assertThat(request.getProtocolVersion(), is(3L));
        assertThat(request.getVespaVersion().get(), is(defaultVespaVersion));
    }

    @Test
    public void testCreateFromRaw() {
        Class<FunctionTestConfig> clazz = FunctionTestConfig.class;
        final String configId = "foo";
        RawConfig config = new RawConfig(new ConfigKey<>(clazz, configId), "595f44fec1e92a71d3e9e77456ba80d1");

        // Default vespa version
        JRTClientConfigRequest request = JRTConfigRequestFactory.createFromRaw(config, 1000);
        assertThat(request.getProtocolVersion(), is(3L));
        assertThat(request.getVespaVersion().get(), is(defaultVespaVersion));
    }

}
