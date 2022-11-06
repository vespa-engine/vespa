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
    private final Duration samplingInterval = Duration.ofSeconds(150L);

    public Loader(Fixture fixture) {
        this.fixture = fixture;
    }

    /** Assign measured zero traffic in the same way as the system will. */
    public Duration zeroTraffic(int measurements, int prodRegions) {
        try (var lock = fixture.tester().nodeRepository().applications().lock(fixture.applicationId())) {
            var statusWithZeroLoad = fixture.application().status()
                                            .withCurrentReadShare(0)
                                            // the line below from TrafficShareUpdater
                                            .withMaxReadShare(prodRegions < 2 ? 1.0 : 1.0 / ( prodRegions - 1.0));
            fixture.tester().nodeRepository().applications().put(fixture.application().with(statusWithZeroLoad), lock);
        }
        return addQueryRateMeasurements(measurements, (n) -> 0.0);
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
        var idealLoad = fixture.clusterModel().idealLoad();
        NodeList nodes = fixture.nodes();
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        Load load = new Load(value, idealLoad.memory(), idealLoad.disk()).multiply(oneExtraNodeFactor);
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(samplingInterval);
            for (Node node : nodes) {
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
            fixture.tester().clock().advance(samplingInterval);
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

    public void applyCpuLoad(double cpuLoad, int measurements) {
        addCpuMeasurements((float)cpuLoad, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyMemLoad(double memLoad, int measurements) {
        addMemMeasurements(memLoad, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    /**
     * Adds measurements with the given resource value and ideal values for the other resources,
     * scaled to take one node redundancy into account.
     * (I.e we adjust to measure a bit lower load than "naively" wanted to offset for the autoscaler
     * wanting to see the ideal load with one node missing.)
     */
    public void addMemMeasurements(double value, int count) {
        var idealLoad = fixture.clusterModel().idealLoad();
        NodeList nodes = fixture.nodes();
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        Load load = new Load(idealLoad.cpu(), value, idealLoad.disk()).multiply(oneExtraNodeFactor);
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(samplingInterval);
            for (Node node : nodes) {
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

    public Duration addMeasurements(Load load, int count)  {
        return addMeasurements(load, 0, true, true, count);
    }

    public Duration addMeasurements(Load load, int generation, boolean inService, boolean stable, int count) {
        Instant initialTime = fixture.tester().clock().instant();
        for (int i = 0; i < count; i++) {
            fixture.tester().clock().advance(samplingInterval);
            for (Node node : fixture.nodes()) {
                fixture.tester().nodeMetricsDb().addNodeMetrics(List.of(new Pair<>(node.hostname(),
                                                                        new NodeMetricSnapshot(fixture.tester().clock().instant(),
                                                                                               load,
                                                                                               generation,
                                                                                               inService,
                                                                                               stable,
                                                                                               0.0))));
            }
        }
        return Duration.between(initialTime, fixture.tester().clock().instant());
    }

    public void applyLoad(Load load, int measurements) {
        addMeasurements(load, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public void applyLoad(Load load, int generation, boolean inService, boolean stable, int measurements) {
        addMeasurements(load, generation, inService, stable, measurements);
        fixture.tester().clock().advance(samplingInterval.negated().multipliedBy(measurements));
        addQueryRateMeasurements(measurements, t -> t == 0 ? 20.0 : 10.0); // Query traffic only
    }

    public Duration addQueryRateMeasurements(int measurements, IntFunction<Double> queryRate) {
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
