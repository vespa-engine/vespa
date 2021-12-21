// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.Utf8String;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.protocol.VespaVersion;
import com.yahoo.vespa.config.util.ConfigUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * @author hmusum

 */
public class RawConfigTest {

    private static final ConfigKey<?> key = new ConfigKey<>("foo", "id", "bar");
    private static final List<String> defContent = Arrays.asList("version=1", "anInt int");
    private static final String defMd5 = ConfigUtils.getDefMd5FromRequest("", defContent);
    private static final PayloadChecksums payloadChecksums = PayloadChecksums.from("012345", "");
    private static final Payload payload = Payload.from(new Utf8String("anInt 1"), CompressionInfo.uncompressed());
    private static final long generation = 1L;

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

        assertEquals("bar.foo," + defMd5 + ",id,MD5:,XXHASH64:,0,null", config.toString());
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
        config = new RawConfig(key, defMd5, payload, payloadChecksums, generation, false, defContent, Optional.empty());
        RawConfig config2 = new RawConfig(key, defMd5, payload, payloadChecksums, 2L, false, defContent, Optional.empty());
        assertThat(config, is(not(config2)));
        assertThat(config.hashCode(), is(not(config2.hashCode())));

        // different config md5 and with vespa version
        final VespaVersion vespaVersion = VespaVersion.fromString("5.37.38");
        RawConfig config3 = new RawConfig(key, defMd5, payload, PayloadChecksums.from("9999", ""), generation, false, defContent, Optional.of(vespaVersion));
        assertThat(config, is(not(config3)));
        assertThat(config.hashCode(), is(not(config3.hashCode())));
        // Check that vespa version is set correctly
        assertThat(config3.getVespaVersion().get().toString(), is(vespaVersion.toString()));
        assertThat(config.getVespaVersion(), is(not(config3.getVespaVersion())));

        // null config
        assertNotEquals(null, config);

        // different type of object
        assertNotEquals(config, key);

        // errors
        RawConfig errorConfig1 = new RawConfig(key, defMd5, payload, payloadChecksums, generation, false, 1, defContent, Optional.empty());
        assertThat(errorConfig1, is(errorConfig1));
        assertThat(config, is(not(errorConfig1)));
        assertThat(config.hashCode(), is(not(errorConfig1.hashCode())));
        assertThat(errorConfig1, is(errorConfig1));
        RawConfig errorConfig2 = new RawConfig(key, defMd5, payload, payloadChecksums, generation, false, 2, defContent, Optional.empty());
        assertThat(errorConfig1, is(not(errorConfig2)));
        assertThat(errorConfig1.hashCode(), is(not(errorConfig2.hashCode())));
    }

    @Test
    public void payload() {
        RawConfig config = new RawConfig(key, defMd5, payload, payloadChecksums, generation, false, defContent, Optional.empty());
        assertEquals(config.getPayload(), payload);
        assertEquals(config.getPayloadChecksums().getForType(XXHASH64), payloadChecksums.getForType(XXHASH64));
        assertEquals(config.getPayloadChecksums().getForType(MD5), payloadChecksums.getForType(MD5));
        assertEquals(config.getGeneration(), generation);
        assertEquals(config.getDefContent(), defContent);
    }

    @Test
    public void require_correct_defmd5() {
        final String defMd5ForEmptyDefContent = "d41d8cd98f00b204e9800998ecf8427e";

        RawConfig config = new RawConfig(key, null, payload, payloadChecksums, generation, false, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, "", payload, payloadChecksums, generation, false, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, defMd5, payload, payloadChecksums, generation, false, defContent, Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5));
        config = new RawConfig(key, null, payload, payloadChecksums, generation, false, null, Optional.empty());
        assertNull(config.getDefMd5());
        config = new RawConfig(key, null, payload, payloadChecksums, generation, false, List.of(""), Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5ForEmptyDefContent));
        config = new RawConfig(key, "", payload, payloadChecksums, generation, false, null, Optional.empty());
        assertThat(config.getDefMd5(), is(""));
        config = new RawConfig(key, "", payload, payloadChecksums, generation, false, List.of(""), Optional.empty());
        assertThat(config.getDefMd5(), is(defMd5ForEmptyDefContent));
    }

}
