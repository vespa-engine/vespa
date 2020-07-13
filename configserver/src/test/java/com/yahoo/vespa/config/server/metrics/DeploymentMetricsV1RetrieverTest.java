// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
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

/**
 * @author olaa
 */
public class DeploymentMetricsV1RetrieverTest {

    @Test
    public void getMetrics()  {
        MockModel mockModel = new MockModel(mockHosts());
        MockDeploymentMetricsV1Retriever mockMetricsRetriever = new MockDeploymentMetricsV1Retriever();
        Application application = new Application(mockModel, null, 0, false,
        null, null, ApplicationId.fromSerializedForm("tenant:app:instance"));

        DeploymentMetricsV1Retriever clusterMetricsRetriever = new DeploymentMetricsV1Retriever(mockMetricsRetriever);
        clusterMetricsRetriever.getMetrics(application);

        assertEquals(2, mockMetricsRetriever.hosts.size()); // Verify that logserver was ignored
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

    class MockDeploymentMetricsV1Retriever extends ClusterDeploymentMetricsV1Retriever {

        Collection<URI> hosts = new ArrayList<>();

        @Override
        public Map<ClusterInfo, MetricsAggregator> requestMetricsGroupedByCluster(Collection<URI> hosts) {
            this.hosts = hosts;

            return Map.of(
                    new ClusterInfo("content_cluster_id", "content"),
                    new MetricsAggregator().addDocumentCount(1000),
                    new ClusterInfo("container_cluster_id", "container"),
                    new MetricsAggregator().addContainerLatency(123, 5)
            );
        }
    }

    class MockModel implements Model {

        Collection<HostInfo> hosts;

        MockModel(Collection<HostInfo> hosts) {
            this.hosts = hosts;
        }

        @Override
        public ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef) {
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
        public void distributeFiles(FileDistribution fileDistribution) {
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
