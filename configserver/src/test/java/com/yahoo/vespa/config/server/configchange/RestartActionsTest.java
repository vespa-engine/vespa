// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ServiceInfo;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static com.yahoo.vespa.config.server.configchange.Utils.*;

/**
 * @author geirst
 */
public class RestartActionsTest {

    private String toString(RestartActions.Entry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(entry.getClusterType() + "." + entry.getClusterName() + "." + entry.getServiceType() + ":");
        builder.append(entry.getServices().stream().
                map(ServiceInfo::getServiceName).
                sorted().
                collect(Collectors.joining(",", "[", "]")));
        builder.append(entry.getMessages().stream().
                collect(Collectors.joining(",", "[", "]")));
        return builder.toString();
    }

    @Test
    public void actions_with_multiple_reasons() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG_2, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).build();
        List<RestartActions.Entry> entries = actions.getRestartActions().getEntries();
        assertThat(entries.size(), is(1));
        assertThat(toString(entries.get(0)), equalTo("search.foo.searchnode:[baz][change,other change]"));
    }

    @Test
    public void actions_with_same_service_type() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME_2).build();
        List<RestartActions.Entry> entries = actions.getRestartActions().getEntries();
        assertThat(entries.size(), is(1));
        assertThat(toString(entries.get(0)), equalTo("search.foo.searchnode:[baz,qux][change]"));
    }

    @Test
    public void actions_with_multiple_service_types() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE_2, SERVICE_NAME).build();
        List<RestartActions.Entry> entries = actions.getRestartActions().getEntries();
        assertThat(entries.size(), is(2));
        assertThat(toString(entries.get(0)), equalTo("search.foo.distributor:[baz][change]"));
        assertThat(toString(entries.get(1)), equalTo("search.foo.searchnode:[baz][change]"));
    }

    @Test
    public void actions_with_multiple_clusters_of_same_type() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER_2, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).build();
        List<RestartActions.Entry> entries = actions.getRestartActions().getEntries();
        assertThat(entries.size(), is(2));
        assertThat(toString(entries.get(0)), equalTo("search.bar.searchnode:[baz][change]"));
        assertThat(toString(entries.get(1)), equalTo("search.foo.searchnode:[baz][change]"));
    }

    @Test
    public void actions_with_multiple_clusters_of_different_type() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder().
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME).
                restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE_2, SERVICE_TYPE, SERVICE_NAME).build();
        List<RestartActions.Entry> entries = actions.getRestartActions().getEntries();
        assertThat(entries.size(), is(2));
        assertThat(toString(entries.get(0)), equalTo("content.foo.searchnode:[baz][change]"));
        assertThat(toString(entries.get(1)), equalTo("search.foo.searchnode:[baz][change]"));
    }

    @Test
    public void use_for_internal_restart_test() {
        ConfigChangeActions actions = new ConfigChangeActionsBuilder()
                .restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE, SERVICE_TYPE, SERVICE_NAME)
                .restart(CHANGE_MSG, CLUSTER, CLUSTER_TYPE_2, SERVICE_TYPE, SERVICE_NAME, true).build();

        assertEquals(Set.of(CLUSTER_TYPE, CLUSTER_TYPE_2),
                actions.getRestartActions().getEntries().stream().map(RestartActions.Entry::getClusterType).collect(Collectors.toSet()));
        assertEquals(Set.of(CLUSTER_TYPE, CLUSTER_TYPE_2),
                actions.getRestartActions().useForInternalRestart(false).getEntries().stream().map(RestartActions.Entry::getClusterType).collect(Collectors.toSet()));
        assertEquals(Set.of(CLUSTER_TYPE),
                actions.getRestartActions().useForInternalRestart(true).getEntries().stream().map(RestartActions.Entry::getClusterType).collect(Collectors.toSet()));
    }
}
