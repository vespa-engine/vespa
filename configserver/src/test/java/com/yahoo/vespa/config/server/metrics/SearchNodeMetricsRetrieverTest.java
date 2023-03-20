// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.server.application.Application;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SearchNodeMetricsRetrieverTest {

    @Test
    public void getMetrics()  {
        SearchNodeMetricsRetrieverTest.MockModel mockModel = new MockModel(mockHosts());
        MockSearchNodeMetricsRetriever mockMetricsRetriever = new MockSearchNodeMetricsRetriever();
        Application application = new Application(mockModel, null, 0,
                                                  null, null, ApplicationId.fromSerializedForm("tenant:app:instance"));

        SearchNodeMetricsRetriever clusterMetricsRetriever = new SearchNodeMetricsRetriever(mockMetricsRetriever);
        clusterMetricsRetriever.getMetrics(application);

        assertEquals(1, mockMetricsRetriever.hosts.size()); // Verify that logserver was ignored
    }

    private Collection<HostInfo> mockHosts() {

        HostInfo hostInfo1 = new HostInfo("host1",
                List.of(new ServiceInfo("content", "searchnode", null, null, "", "host1"))
        );
        HostInfo hostInfo2 = new HostInfo("host2",
                List.of(new ServiceInfo("default", "container", null, null, "", "host2"))
        );
        HostInfo hostInfo3 = new HostInfo("host3",
                List.of(new ServiceInfo("default", "logserver",  null, null, "", "host3"))
        );

        return List.of(hostInfo1, hostInfo2, hostInfo3);
    }

    static class MockSearchNodeMetricsRetriever extends ClusterSearchNodeMetricsRetriever {

        Collection<URI> hosts = new ArrayList<>();

        @Override
        public Map<String, SearchNodeMetricsAggregator> requestMetricsGroupedByCluster(Collection<URI> hosts) {
            this.hosts = hosts;

            return Map.of(
                    ("content_cluster_id"),
                    new SearchNodeMetricsAggregator()
            );
        }
    }

    static class MockModel implements Model {

        final Collection<HostInfo> hosts;

        MockModel(Collection<HostInfo> hosts) {
            this.hosts = hosts;
        }

        @Override
        public ConfigInstance.Builder getConfigInstance(ConfigKey<?> configKey, ConfigDefinition targetDef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<ConfigKey<?>> allConfigsProduced() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<HostInfo> getHosts() {
            return hosts;
        }

        @Override
        public Set<String> allConfigIds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<FileReference> fileReferences() { return new HashSet<>(); }

        @Override
        public AllocatedHosts allocatedHosts() {
            throw new UnsupportedOperationException();
        }
    }
}
