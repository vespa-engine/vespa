// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.time.Duration;
import java.util.Optional;
import java.util.function.IntFunction;

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

    public Fixture(Fixture.Builder builder, Optional<ClusterResources> initialResources) {
        application = builder.application;
        cluster = builder.cluster;
        capacity = builder.capacity;
        tester = new AutoscalingTester(builder.zone, builder.hostResources, builder.resourceCalculator);
        var deployCapacity = initialResources.isPresent() ? Capacity.from(initialResources.get()) : capacity;
        tester.deploy(builder.application, builder.cluster, deployCapacity);
    }

    public AutoscalingTester  tester() { return tester; }

    /** Autoscale within the deployed capacity of this. */
    public Autoscaler.Advice autoscale() {
        return autoscale(capacity);
    }

    /** Autoscale within the given capacity. */
    public Autoscaler.Advice autoscale(Capacity capacity) {
        return tester.autoscale(application, cluster, capacity);
    }

    /** Redeploy with the deployed capacity of this. */
    public void deploy() {
        deploy(capacity);
    }

    /** Redeploy with the given capacity. */
    public void deploy(Capacity capacity) {
        tester.deploy(application, cluster, capacity);
    }

    /** Returns the nodes allocated to the fixture application cluster */
    public NodeList nodes() {
        return tester.nodeRepository().nodes().list().owner(application).cluster(cluster.id());
    }

    public void deactivateRetired(Capacity capacity) {
        tester.deactivateRetired(application, cluster, capacity);
    }

    public Duration addCpuMeasurements(double cpuLoad, int measurements) {
        return tester().addCpuMeasurements((float)cpuLoad, 1.0f, measurements, application);
    }

    public void addLoadMeasurements(int measurements, IntFunction<Double> queryRate, IntFunction<Double> writeRate) {
        tester().addLoadMeasurements(application, cluster.id(), measurements, queryRate, writeRate);
    }

    public void applyCpuLoad(double cpuLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addCpuMeasurements((float)cpuLoad, 1.0f, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyMemLoad(double memLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addMemMeasurements((float)memLoad, 1.0f, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyLoad(double cpuLoad, double memoryLoad, double diskLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addMeasurements((float)cpuLoad, (float)memoryLoad, (float)diskLoad, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyLoad(double cpuLoad, double memoryLoad, double diskLoad, int generation, boolean inService, boolean stable, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        tester().addMeasurements((float)cpuLoad, (float)memoryLoad, (float)diskLoad, generation, inService, stable, measurements, application);
        tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        tester().addQueryRateMeasurements(application, cluster.id(), measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public static class Builder {

        NodeResources hostResources = new NodeResources(100, 100, 100, 1);
        Optional<ClusterResources> initialResources = Optional.of(new ClusterResources(5, 1, new NodeResources(3, 10, 100, 1)));
        Capacity capacity = Capacity.from(new ClusterResources(2, 1,
                                                               new NodeResources(1, 1, 1, 1, NodeResources.DiskSpeed.any)),
                                          new ClusterResources(20, 1,
                                                               new NodeResources(100, 1000, 1000, 1, NodeResources.DiskSpeed.any)));

        ApplicationId application = AutoscalingTester.applicationId("application1");
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("cluster1")).vespaVersion("7").build();
        Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));
        HostResourcesCalculator resourceCalculator = new AutoscalingTester.MockHostResourcesCalculator(zone, 0);

        public Fixture.Builder clusterType(ClusterSpec.Type type) {
            cluster = ClusterSpec.request(type, cluster.id()).vespaVersion("7").build();
            return this;
        }

        public Fixture.Builder hostResources(NodeResources hostResources) {
            this.hostResources = hostResources;
            return this;
        }

        public Fixture.Builder initialResources(Optional<ClusterResources> initialResources) {
            this.initialResources = initialResources;
            return this;
        }

        public Fixture.Builder capacity(Capacity capacity) {
            this.capacity = capacity;
            return this;
        }

        public Fixture.Builder resourceCalculator(HostResourcesCalculator resourceCalculator) {
            this.resourceCalculator = resourceCalculator;
            return this;
        }

        public Fixture build() {
            return new Fixture(this, initialResources);
        }

    }

}
