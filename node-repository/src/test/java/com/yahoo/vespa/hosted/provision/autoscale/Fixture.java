// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;

import java.time.Duration;

/**
 * Fixture for autoscaling tests.
 *
 * @author bratseth
 */
public class Fixture {

    final AutoscalingTester tester;
    final ApplicationId application;
    final ClusterSpec cluster;
    final Capacity capacity;

    public Fixture(Fixture.Builder builder) {
        application = builder.application;
        cluster = builder.cluster;
        capacity = Capacity.from(builder.min, builder.max);
        tester = new AutoscalingTester(builder.hostResources);
        tester.deploy(builder.application, builder.cluster, 5, 1, builder.nodeResources);
    }

    public AutoscalingTester  tester() { return tester; }

    public Autoscaler.Advice autoscale() {
        return tester.autoscale(application, cluster, capacity);
    }

    public void deploy(ClusterResources resources) {
        tester.deploy(application, cluster, resources);
    }

    public void deactivateRetired(ClusterResources resources) {
        tester.deactivateRetired(application, cluster, resources);
    }

    public void applyLoad(double cpuLoad, double memoryLoad, double diskLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addMeasurements((float)cpuLoad, (float)memoryLoad, (float)diskLoad, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyCpuLoad(double cpuLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addCpuMeasurements((float)cpuLoad, 1.0f, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public static class Builder {

        NodeResources hostResources = new NodeResources(100, 100, 100, 1);
        NodeResources nodeResources = new NodeResources(3, 10, 100, 1);
        ClusterResources min = new ClusterResources(2, 1,
                                                    new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any));
        ClusterResources max = new ClusterResources(20, 1,
                                                    new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any));

        ApplicationId application = AutoscalingTester.applicationId("application1");
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("cluster1")).vespaVersion("7").build();

        public Fixture.Builder clusterType(ClusterSpec.Type type) {
            cluster = ClusterSpec.request(type, cluster.id()).vespaVersion("7").build();
            return this;
        }

        public Fixture build() {
            return new Fixture(this);
        }

    }

}
