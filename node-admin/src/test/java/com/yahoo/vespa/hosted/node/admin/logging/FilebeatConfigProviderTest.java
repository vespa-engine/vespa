package com.yahoo.vespa.hosted.node.admin.logging;

import com.google.common.collect.ImmutableList;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.util.Environment;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.io.IOException;
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
    public void it_replaces_all_fields_correctly() throws IOException {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment());

        Optional<String> config = filebeatConfigProvider.getConfig(getNodeSpec(tenant, application, instance));

        assertTrue(config.isPresent());
        String configString = config.get();
        assertThat(configString, not(containsString("%%")));
    }

    @Test
    public void it_does_not_generate_config_when_no_logstash_nodes() throws IOException {
        Environment env = new Environment.Builder()
                .environment(environment)
                .region(region)
                .build();

        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(env);
        Optional<String> config = filebeatConfigProvider.getConfig(getNodeSpec(tenant, application, instance));
        assertFalse(config.isPresent());
    }

    @Test
    public void it_does_not_generate_config_for_nodes_wihout_owner() throws IOException {
        FilebeatConfigProvider filebeatConfigProvider = new FilebeatConfigProvider(getEnvironment());
        ContainerNodeSpec nodeSpec = new ContainerNodeSpec.Builder()
                .nodeFlavor("flavor")
                .nodeState(Node.State.active)
                .nodeType("type")
                .hostname("hostname")
                .build();
        Optional<String> config = filebeatConfigProvider.getConfig(nodeSpec);
        assertFalse(config.isPresent());
    }

    @Test
    public void it_generates_correct_index_source() throws IOException {
        assertThat(getConfigString(), containsString("index_source: \"hosted-instance_vespa_music_us-north-1_prod_default\""));
    }

    @Test
    public void it_sets_logstash_nodes_properly() throws IOException {
        assertThat(getConfigString(), containsString("hosts: [logstash1,logstash2]"));
    }

    @Test
    public void it_generates_correct_spool_size() throws IOException {
        // 2 nodes, 3 workers, 2048 buffer size -> 12288
        assertThat(getConfigString(), containsString("spool_size: 12288"));
    }

    private String getConfigString() throws IOException {
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
                .build();
    }

}
