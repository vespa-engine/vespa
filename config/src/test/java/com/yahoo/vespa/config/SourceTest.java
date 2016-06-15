// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.protocol.Trace;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 * @since 5.1
 */
public class SourceTest {

    @Test
    public void testSourceInterface() {
        MockSourceConfig config = new MockSourceConfig(new ConfigKey<>(SimpletypesConfig.class, "foobio"));
        assertThat(config.getKey().getConfigId(), is("foobio"));
        config.setConfig(JRTClientConfigRequestV3.createWithParams(config.getKey(), DefContent.fromList(config.getDefContent()), "host", config.getDefMd5(), config.getGeneration(), 1000, Trace.createNew(), CompressionType.LZ4, Optional.empty()));
        MockSource src = new MockSource(config);
        assertThat(src.getState(), is(Source.State.NEW));
        src.open();
        assertTrue(src.opened);
        assertTrue(src.getconfigged);
        assertThat(src.getState(), is(Source.State.OPEN_PENDING));
        src.open();
        assertThat(src.getState(), is(Source.State.OPEN_PENDING));
        src.getconfigged = false;
        src.getConfig();
        assertTrue(src.getconfigged);
        assertThat(src.getState(), is(Source.State.OPEN_PENDING));
        assertTrue(config.setConfigCalled);
        assertTrue(src.openTimestamp > 0);
        config.notifyInitMonitor();
        config.setGeneration(4);
        src.cancel();
        assertTrue(src.canceled);
        assertThat(src.getState(), is(Source.State.CANCEL_REQUESTED));
        src.setState(Source.State.CANCELLED);
        try {
            src.open();
            fail("Expected exception");
        } catch (ConfigurationRuntimeException e) {
        }
        src.getconfigged = false;
        src.getConfig();
        assertFalse(src.getconfigged);
        src.canceled = false;
        src.cancel();
        assertFalse(src.canceled);
    }

    public static class MockSource extends Source {
        boolean opened, getconfigged, canceled = false;

        public MockSource(SourceConfig sourceConfig) {
            super(sourceConfig);
        }

        @Override
        public void myOpen() {
            opened = true;
        }

        @Override
        protected void myGetConfig() {
            getconfigged = true;
        }

        @Override
        public void myCancel() {
            canceled = true;
        }
    }

    private static class MockSourceConfig implements SourceConfig {

        boolean notifyCalled = false;
        ConfigKey<?> key = null;
        boolean setConfigCalled = false;
        long generation = -1;
        Payload payload;

        public MockSourceConfig(ConfigKey<?> key) {
            this.key = key;
        }

        @Override
        public void notifyInitMonitor() {
            notifyCalled = true;
        }

        @Override
        public void setConfig(com.yahoo.vespa.config.protocol.JRTClientConfigRequest req) {
            key = req.getConfigKey();
            setConfigCalled = true;
        }

        @Override
        public void setGeneration(long generation) {
            this.generation = generation;
        }

        @Override
        public String getDefName() {
            return key.getName();
        }

        @Override
        public String getDefNamespace() {
            return key.getNamespace();
        }

        @Override
        public String getDefVersion() {
            return "";
        }

        @Override
        public List<String> getDefContent() {
            return Arrays.asList("foo");
        }

        @Override
        public String getDefMd5() {
            return key.getMd5();
        }

        @Override
        public String getConfigId() {
            return key.getConfigId();
        }

        @Override
        public ConfigKey<?> getKey() {
            return key;
        }

        @Override
        public String getConfigMd5() {
            return "bar";
        }

        @Override
        public long getGeneration() {
            return 0;
        }

        @Override
        public RawConfig getConfig() {
            return  new RawConfig(getKey(), getDefMd5(), payload, getConfigMd5(), generation, getDefContent(), Optional.empty());
        }
    }
}
