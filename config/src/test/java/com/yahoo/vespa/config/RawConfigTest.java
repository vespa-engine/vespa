// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.Utf8String;
import com.yahoo.vespa.config.protocol.*;
import com.yahoo.vespa.config.protocol.VespaVersion;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum

 */
public class RawConfigTest {

    private static final ConfigKey<?> key = new ConfigKey<>("foo", "id", "bar");
    private static List<String> defContent = Arrays.asList("version=1", "anInt int");
    private static final String defMd5 = ConfigUtils.getDefMd5FromRequest("", defContent);
    private static final String configMd5 = "012345";
    private static Payload payload = Payload.from(new Utf8String("anInt 1"), CompressionInfo.uncompressed());
    private static long generation = 1L;

    @Test
    public void basic() {
        RawConfig config = new RawConfig(key, defMd5);
        assertEquals(config.getKey(), key);
        assertEquals(defMd5, config.getDefMd5());
        assertEquals("foo", config.getName());
        assertEquals("bar", config.getDefNamespace());
        assertEquals("id", config.getConfigId());

        assertFalse(config.isError());

        // Copy constructor
        RawConfig copiedConfig = new RawConfig(config);
        assertEquals(config, copiedConfig);

        assertEquals("bar.foo," + defMd5 + ",id,,0,null", config.toString());
        assertEquals(Optional.empty(), config.getVespaVersion());
    }

    @Test
    public void testEquals() {
        RawConfig config = new RawConfig(key, defMd5);

        assertThat(config, is(new RawConfig(key, defMd5)));
        assertThat(config, is(not(new RawConfig(key, "a")))); // different def md5
        assertThat(config.hashCode(), is(new RawConfig(key, defMd5).hashCode()));
        assertThat(config.hashCode(), is(not(new RawConfig(key, "a").hashCode()))); // different def md5

        // different generation
        config = new RawConfig(key, defMd5, payload, configMd5, generation, defContent, Optional.empty());
        RawConfig config2 = new RawConfig(key, defMd5, payload, configMd5, 2L, defContent, Optional.empty());
        assertThat(config, is(not(config2)));
        assertThat(config.hashCode(), is(not(config2.hashCode())));

        // different config md5 and with vespa version
        final VespaVersion vespaVersion = VespaVersion.fromString("5.37.38");
        RawConfig config3 = new RawConfig(key, defMd5, payload, "9999", generation, defContent, Optional.of(vespaVersion));
        assertThat(config, is(not(config3)));
        assertThat(config.hashCode(), is(not(config3.hashCode())));
        // Check that vespa version is set correctly
        assertThat(config3.getVespaVersion().get().toString(), is(vespaVersion.toString()));
        assertThat(config.getVespaVersion(), is(not(config3.getVespaVersion())));

        // null config
        assertFalse(config.equals(null));

        // different type of object
        assertFalse(config.equals(key));

        // errors
        RawConfig errorConfig1 = new RawConfig(key, defMd5, payload, configMd5, generation, 1, defContent, Optional.empty());
        assertThat(errorConfig1, is(errorConfig1));
        assertThat(config, is(not(errorConfig1)));
        assertThat(config.hashCode(), is(not(errorConfig1.hashCode())));
        assertThat(errorConfig1, is(errorConfig1));
        RawConfig errorConfig2 = new RawConfig(key, defMd5, payload, configMd5, generation, 2, defContent, Optional.empty());
        assertThat(errorConfig1, is(not(errorConfig2)));
        assertThat(errorConfig1.hashCode(), is(not(errorConfig2.hashCode())));
    }

    @Test
    public void payload() {
        RawConfig config = new RawConfig(key, defMd5, payload, configMd5, generation, defContent, Optional.empty());
        assertThat(config.getPayload(), is(payload));
        assertThat(config.getConfigMd5(), is(configMd5));
        assertThat(config.getGeneration(), is(generation));
        assertThat(config.getDefContent(), is(defContent));
    }

    @Test
    public void require_correct_defmd5() {
        final String defMd5ForEmptyDefContent = "d41d8cd98f00b204e9800998ecf8427e";

        RawConfig config = new RawConfig(key, null, payload, configMd5, generation, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, "", payload, configMd5, generation, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, defMd5, payload, configMd5, generation, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, null, payload, configMd5, generation, null, Optional.empty());
        assertNull(config.getDefMd5());
        config = new RawConfig(key, null, payload, configMd5, generation, Arrays.asList(""), Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5ForEmptyDefContent));
        config = new RawConfig(key, "", payload, configMd5, generation, null, Optional.empty());
        assertThat(config.getDefMd5(), is(""));
        config = new RawConfig(key, "", payload, configMd5, generation, Arrays.asList(""), Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5ForEmptyDefContent));
    }

}
