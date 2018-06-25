// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.test.Bootstrap1Config;
import com.yahoo.config.test.Bootstrap2Config;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.di.ConfigRetriever.BootstrapConfigs;
import com.yahoo.container.di.ConfigRetriever.ComponentsConfigs;
import com.yahoo.container.di.ConfigRetriever.ConfigSnapshot;
import com.yahoo.vespa.config.ConfigKey;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 *
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ConfigRetrieverTest {

    private DirConfigSource dirConfigSource = null;

    @Before
    public void setup() {
        dirConfigSource = new DirConfigSource("ConfigRetrieverTest-");
    }

    @After
    public void cleanup() {
        dirConfigSource.cleanup();
    }

    @Test
    public void require_that_bootstrap_configs_come_first() {
        writeConfigs();
        ConfigRetriever retriever = createConfigRetriever();
        ConfigSnapshot bootstrapConfigs = retriever.getConfigs(Collections.emptySet(), 0, false);

        assertThat(bootstrapConfigs, Matchers.instanceOf(BootstrapConfigs.class));
    }

    @Test
    @SuppressWarnings("unused")
    public void require_that_components_comes_after_bootstrap() {
        writeConfigs();
        ConfigRetriever retriever = createConfigRetriever();
        ConfigSnapshot bootstrapConfigs = retriever.getConfigs(Collections.emptySet(), 0, false);

        ConfigKey<? extends ConfigInstance> testConfigKey = new ConfigKey<>(TestConfig.class, dirConfigSource.configId());
        ConfigSnapshot componentsConfigs = retriever.getConfigs(Collections.singleton(testConfigKey), 0);

        if (componentsConfigs instanceof ComponentsConfigs) {
            assertThat(componentsConfigs.size(), is(3));
        } else {
            fail("ComponentsConfigs has unexpected type: " + componentsConfigs);
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void require_no_reconfig_when_restart_on_redeploy() {
        // TODO
        writeConfigs();
        ConfigRetriever retriever = createConfigRetriever();
        ConfigSnapshot bootstrapConfigs = retriever.getConfigs(Collections.emptySet(), 0, false);

        ConfigKey<? extends ConfigInstance> testConfigKey = new ConfigKey<>(TestConfig.class, dirConfigSource.configId());
        Optional<ConfigSnapshot> componentsConfigs = retriever.getConfigsOnce(Collections.singleton(testConfigKey), 0, true);

        if (componentsConfigs.isPresent()) {
            fail("Expected no configs");
        }
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Ignore
    @SuppressWarnings("unused")
    public void require_exception_upon_modified_components_keys_without_bootstrap() {
        expectedException.expect(IllegalArgumentException.class);

        writeConfigs();
        ConfigRetriever retriever = createConfigRetriever();
        ConfigKey<? extends ConfigInstance> testConfigKey = new ConfigKey<>(TestConfig.class, dirConfigSource.configId());
        ConfigSnapshot bootstrapConfigs = retriever.getConfigs(Collections.emptySet(), 0, false);
        ConfigSnapshot componentsConfigs = retriever.getConfigs(Collections.singleton(testConfigKey), 0, false);
        Set<ConfigKey<? extends ConfigInstance>> keys = new HashSet<>();
        keys.add(testConfigKey);
        keys.add(new ConfigKey<>(TestConfig.class, ""));
        retriever.getConfigs(keys, 0);
    }

    @Test
    public void require_that_empty_components_keys_after_bootstrap_returns_components_configs() {
        writeConfigs();
        ConfigRetriever retriever = createConfigRetriever();
        assertThat(retriever.getConfigs(Collections.emptySet(), 0), instanceOf(BootstrapConfigs.class));
        assertThat(retriever.getConfigs(Collections.emptySet(), 0), instanceOf(ComponentsConfigs.class));
    }

    public void writeConfigs() {
        writeConfig("bootstrap1", "dummy \"ignored\"");
        writeConfig("bootstrap2", "dummy \"ignored\"");
        writeConfig("test", "stringVal \"ignored\"");
    }

    private ConfigRetriever createConfigRetriever() {
        String configId = dirConfigSource.configId();
        CloudSubscriberFactory subscriber = new CloudSubscriberFactory(dirConfigSource.configSource());
        Set<ConfigKey<? extends ConfigInstance>> keys = new HashSet<>();
        keys.add(new ConfigKey<>(Bootstrap1Config.class, configId));
        keys.add(new ConfigKey<>(Bootstrap2Config.class, configId));
        return new ConfigRetriever(keys, keySet -> subscriber.getSubscriber(keySet));
    }

    private void writeConfig(String name, String contents) {
        dirConfigSource.writeConfig(name, contents);
    }
}
