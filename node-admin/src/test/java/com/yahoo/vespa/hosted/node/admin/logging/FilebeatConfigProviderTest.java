// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.logging;

import com.google.common.collect.ImmutableList;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

/**
 * @author mortent
 */
public class FilebeatConfigProviderTest {


    private static final String tenant = "vespa";
    private static final String application = "music";
    private static final String instance = "default";
    private static final String environment = "prod";
    private static final String region = "us-north-1";
    private static final List<String> logstashNodes = ImmutableList.of("logstash1", "logstash2");

    @Test
    public void it_replaces_all_fields_correctly() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment());

        Optional<String> config = filebeatConfigProvider.getConfig(getNodeSpec(tenant, application, instance));

        assertTrue(config.isPresent());
        String configString = config.get();
        assertThat(configString, not(containsString("%%")));
    }

    @Test
    public void it_does_not_generate_config_when_no_logstash_nodes() {
        Environment env = new Environment.Builder()
                .environment(environment)
                .region(region)
                .build();

        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(env);
        Optional<String> config = filebeatConfigProvider.getConfig(getNodeSpec(tenant, application, instance));
        assertFalse(config.isPresent());
    }

    @Test
    public void it_does_not_generate_config_for_nodes_wihout_owner() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment());
        ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .nodeFlavor("flavor")
                .nodeState(Node.State.active)
                .nodeType("type")
                .hostname("hostname")
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();
        Optional<String> config = filebeatConfigProvider.getConfig(nodeSpec);
        assertFalse(config.isPresent());
    }

    @Test
    public void it_generates_correct_index_source() {
        assertThat(getConfigString(), containsString("index_source: \"hosted-instance_vespa_music_us-north-1_prod_default\""));
    }

    @Test
    public void it_sets_logstash_nodes_properly() {
        assertThat(getConfigString(), containsString("hosts: [\"logstash1\",\"logstash2\"]"));
    }

    @Test
    public void it_does_not_add_double_quotes() {
        Environment environment = new Environment.Builder()
                .environment(FilebeatConfigProviderTest.environment)
                .region(region)
                .logstashNodes(ImmutableList.of("unquoted", "\"quoted\""))
                .build();
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(environment);
        Optional<String> config = filebeatConfigProvider.getConfig(getNodeSpec(tenant, application, instance));
        assertThat(config.get(), containsString("hosts: [\"unquoted\",\"quoted\"]"));
    }

    @Test
    public void it_generates_correct_spool_size() {
        // 2 nodes, 3 workers, 2048 buffer size -> 12288
        assertThat(getConfigString(), containsString("spool_size: 12288"));
    }

    private String getConfigString() {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment());
        ContainerNodeSpec nodeSpec = getNodeSpec(tenant, application, instance);
        return filebeatConfigProvider.getConfig(nodeSpec).orElseThrow(() -> new RuntimeException("Failed to get filebeat config"));
    }

    private Environment getEnvironment() {
        return new Environment.Builder()
                .environment(environment)
                .region(region)
                .logstashNodes(logstashNodes)
                .build();
    }

    private ContainerNodeSpec getNodeSpec(String tenant, String application, String instance) {
        ContainerNodeSpec.Owner owner = new ContainerNodeSpec.Owner(tenant, application, instance);
        return new ContainerNodeSpec.Builder()
                .owner(owner)
                .nodeFlavor("flavor")
                .nodeState(Node.State.active)
                .nodeType("type")
                .hostname("hostname")
                .minCpuCores(1)
                .minMainMemoryAvailableGb(1)
                .minDiskAvailableGb(1)
                .build();
    }

}
