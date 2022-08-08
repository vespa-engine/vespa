// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A helper for applying load to an application represented by a fixture,
 *
 * @author bratseth
 */
public class Loader {

    private final Fixture fixture;

    public Loader(Fixture fixture) {
        this.fixture = fixture;
    }

    /**
     * Adds measurements with the given resource value and ideal values for the other resources,
     * scaled to take one node redundancy into account.
     * (I.e we adjust to measure a bit lower load than "naively" wanted to offset for the autoscaler
     * wanting to see the ideal load with one node missing.)
     *
     * @param count the number of measurements
     */
    public Duration addCpuMeasurements(double value, int count) {
        var idealLoad = fixture.clusterModel().idealLoad(); // TODO: Use this
        NodeList nodes = fixture.nodes();
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(Duration.ofSeconds(150));
            for (Node node : nodes) {
                Load load = new Load(value,
                                     ClusterModel.idealMemoryLoad,
                                     ClusterModel.idealContentDiskLoad).multiply(oneExtraNodeFactor);
                fixture.tester().nodeMetricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(),
                                                                         new NodeMetricSnapshot(fixture.tester().clock().instant(),
                                                                                                load,
                                                                                                0,
                                                                                                true,
                                                                                                true,
                                                                                                0.0))));
            }
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    public Duration addLoadMeasurements(int measurements, IntFunction<Double> queryRate, IntFunction<Double> writeRate) {
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < measurements; i++) {
            fixture.tester().nodeMetricsDb().addClusterMetrics(fixture.applicationId(),
                                                               Map.of(fixture.clusterId(), new ClusterMetricSnapshot(fixture.tester().clock().instant(),
                                                                                                                     queryRate.apply(i),
                                                                                                                     writeRate.apply(i))));
            fixture.tester().clock().advance(Duration.ofMinutes(5));
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

    public void applyCpuLoad(double cpuLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        addCpuMeasurements((float)cpuLoad, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyMemLoad(double memLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addMemMeasurements
        addMemMeasurements(memLoad, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    /**
     * Adds measurements with the given resource value and ideal values for the other resources,
     * scaled to take one node redundancy into account.
     * (I.e we adjust to measure a bit lower load than "naively" wanted to offset for the autoscaler
     * wanting to see the ideal load with one node missing.)
     */
    public void addMemMeasurements(double value, int count) {
        var idealLoad = fixture.clusterModel().idealLoad(); // TODO: Use this
        NodeList nodes = fixture.nodes();
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                Load load = new Load(0.2,
                                     value,
                                     ClusterModel.idealContentDiskLoad).multiply(oneExtraNodeFactor);
                fixture.tester().nodeMetricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(),
                                                                        new NodeMetricSnapshot(fixture.tester().clock().instant(),
                                                                                               load,
                                                                                               0,
                                                                                               true,
                                                                                               true,
                                                                                               0.0))));
            }
        }
    }

    public Duration addMeasurements(double cpu, double memory, double disk, int count)  {
        return addMeasurements(cpu, memory, disk, 0, true, true, count);
    }

    public Duration addMeasurements(double cpu, double memory, double disk, int generation, boolean inService, boolean stable,
                                    int count) {
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(Duration.ofMinutes(1));
            for (Node node : fixture.nodes()) {
                fixture.tester().nodeMetricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(),
                                                                        new NodeMetricSnapshot(fixture.tester().clock().instant(),
                                                                                               new Load(cpu, memory, disk),
                                                                                               generation,
                                                                                               inService,
                                                                                               stable,
                                                                                               0.0))));
            }
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

    public void applyLoad(double cpuLoad, double memoryLoad, double diskLoad, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        addMeasurements(cpuLoad, memoryLoad, diskLoad, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyLoad(double cpuLoad, double memoryLoad, double diskLoad, int generation, boolean inService, boolean stable, int measurements) {
        Duration samplingInterval = Duration.ofSeconds(150L); // in addCpuMeasurements
        addMeasurements(cpuLoad, memoryLoad, diskLoad, generation, inService, stable, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, samplingInterval, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public Duration addQueryRateMeasurements(int measurements, Duration samplingInterval, IntFunction<Double> queryRate) {
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < measurements; i++) {
            fixture.tester().nodeMetricsDb().addClusterMetrics(fixture.applicationId(),
                                                     Map.of(fixture.clusterId(), new ClusterMetricSnapshot(fixture.tester().clock().instant(),
                                                                                                   queryRate.apply(i),
                                                                                                   0.0)));
            fixture.tester().clock().advance(samplingInterval);
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

}
