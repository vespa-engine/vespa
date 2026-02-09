// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.test.ManualClock;
import com.yahoo.text.Text;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Discrete event simulation test for LoadBalancer that measures group skew
 * across different load balancing policies using exponential arrival times
 * and gamma-distributed service times.
 *
 * @author arnej
 */
public class LoadBalancerSimulationTest {

    // System property names
    private static final String PROP_QUERY_RATE = "lb.sim.query.rate";          // queries per second
    private static final String PROP_GAMMA_SHAPE = "lb.sim.gamma.shape";        // shape of query-latency distribution
    private static final String PROP_QUERY_LATENCY = "lb.sim.query.latency.ms"; // mean query latency
    private static final String PROP_NUM_GROUPS = "lb.sim.num.groups";          // number of simulated groups
    private static final String PROP_TOTAL_TIME = "lb.sim.total.time.s";        // simulated seconds to run

    // Default values
    private static final double DEFAULT_QUERY_RATE = 1200.0; // per second
    private static final int DEFAULT_GAMMA_SHAPE = 2;
    private static final double DEFAULT_QUERY_LATENCY = 25.0; // milliseconds
    private static final int DEFAULT_NUM_GROUPS = 10;
    private static final double DEFAULT_TOTAL_TIME = 600.0; // seconds

    // Random instance with fixed seed for reproducibility
    private static final Random random = new Random(42L);

    /**
     * Configuration for simulation parameters, loadable from system properties.
     */
    private static class SimulationConfig {
        final double exponentialMean;
        final int gammaShape;
        final double gammaScale;
        final int numGroups;
        final Instant startTime;
        final Instant endTime;

        SimulationConfig(double exponentialMean, int gammaShape, double gammaScale,
                         int numGroups, long totalTimeMs) {
            this.exponentialMean = exponentialMean;
            this.gammaShape = gammaShape;
            this.gammaScale = gammaScale;
            this.numGroups = numGroups;
            this.startTime = Instant.EPOCH;
            this.endTime = startTime.plusMillis(totalTimeMs);
        }

        static SimulationConfig fromSystemProperties() {
            double queryRate = Double.parseDouble(
                    System.getProperty(PROP_QUERY_RATE, String.valueOf(DEFAULT_QUERY_RATE)));
            double exponentialMean = 1000.0 / queryRate; // Convert queries/sec to mean inter-arrival time in ms
            int gammaShape = Integer.parseInt(
                    System.getProperty(PROP_GAMMA_SHAPE, String.valueOf(DEFAULT_GAMMA_SHAPE)));
            double queryLatency = Double.parseDouble(
                    System.getProperty(PROP_QUERY_LATENCY, String.valueOf(DEFAULT_QUERY_LATENCY)));
            double gammaScale = queryLatency / gammaShape; // For Gamma: mean = shape * scale, so scale = mean / shape
            int numGroups = Integer.parseInt(
                    System.getProperty(PROP_NUM_GROUPS, String.valueOf(DEFAULT_NUM_GROUPS)));
            double totalTimeSeconds = Double.parseDouble(
                    System.getProperty(PROP_TOTAL_TIME, String.valueOf(DEFAULT_TOTAL_TIME)));
            long totalTimeMs = (long)(totalTimeSeconds * 1000.0); // Convert seconds to milliseconds

            return new SimulationConfig(exponentialMean, gammaShape, gammaScale, numGroups, totalTimeMs);
        }
    }

    /**
     * Collects and prints statistics about group usage during simulation.
     */
    private static class GroupStatistics {
        private final Map<Integer, Integer> takeGroupCounts;
        private final Map<Integer, Integer> maxOutstandingRequests;

        GroupStatistics(int numGroups) {
            this.takeGroupCounts = new HashMap<>();
            this.maxOutstandingRequests = new HashMap<>();
            for (int i = 0; i < numGroups; i++) {
                takeGroupCounts.put(i, 0);
                maxOutstandingRequests.put(i, 0);
            }
        }

        void recordTakeGroup(int groupId) {
            takeGroupCounts.merge(groupId, 1, Integer::sum);
        }

        void recordMaxOutstanding(int groupId, int outstanding) {
            maxOutstandingRequests.merge(groupId, outstanding, Integer::max);
        }

        void printStatistics(LoadBalancer.Policy policy) {
            System.out.println("\n=== Statistics for Policy: " + policy + " ===");

            System.out.println("\nTakeGroup calls per group:");
            takeGroupCounts.forEach((groupId, count) ->
                    System.out.println("  Group " + groupId + ": " + count));

            System.out.println("\nMaximum outstanding requests per group:");
            maxOutstandingRequests.forEach((groupId, max) ->
                    System.out.println("  Group " + groupId + ": " + max));

            System.out.println("\nSkew Metrics:");
            printSkewMetrics();
        }

        private void printSkewMetrics() {
            Collection<Integer> counts = takeGroupCounts.values();
            double mean = counts.stream().mapToInt(Integer::intValue).average().orElse(0.0);

            // Standard deviation
            double variance = counts.stream()
                    .mapToDouble(count -> Math.pow(count - mean, 2))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);

            // Max-min difference
            int max = counts.stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = counts.stream().mapToInt(Integer::intValue).min().orElse(0);
            int maxMinDiff = max - min;

            // Coefficient of variation
            double coefficientOfVariation = mean > 0 ? stdDev / mean : 0.0;

            System.out.println("  Mean: " + Text.format("%.2f", mean));
            System.out.println("  Standard Deviation: " + Text.format("%.2f", stdDev));
            System.out.println("  Max-Min Difference: " + maxMinDiff);
            System.out.println("  Coefficient of Variation: " + Text.format("%.4f", coefficientOfVariation));
        }
    }

    /**
     * Returns a uniform random variable in (0,1] (excluding 0, including 1).
     * Uses 1.0 - nextDouble() to avoid the special case of 0.0.
     */
    private static double nextUniformDouble() {
        return 1.0 - random.nextDouble();
    }

    /**
     * Generate an exponential random variable with given mean.
     * Uses inverse transform: X = -mean * ln(U) where U ~ Uniform(0,1)
     */
    private static double generateExponential(double mean) {
        return -mean * Math.log(nextUniformDouble());
    }

    /**
     * Generate a gamma random variable with given integer shape and scale.
     * For integer shape k, uses sum of k exponential random variables:
     * Gamma(k, scale) = sum of k Exponential(scale) variables
     * Formula: -scale * sum(ln(Ui)) for i=1..k where Ui ~ Uniform(0,1)
     */
    private static double generateGamma(int shape, double scale) {
        double sumOfLogs = 0.0;
        for (int i = 0; i < shape; i++) {
            sumOfLogs += Math.log(nextUniformDouble());
        }
        return -scale * sumOfLogs;
    }

    /**
     * Abstract event in the discrete event simulation.
     */
    private static abstract class Event implements Comparable<Event> {
        protected final Instant timestamp;

        Event(Instant timestamp) {
            this.timestamp = timestamp;
        }

        abstract void execute(SimulationContext context);

        @Override
        public int compareTo(Event other) {
            return this.timestamp.compareTo(other.timestamp);
        }

        Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Event representing a query arrival (calls takeGroup).
     */
    private static class ArrivalEvent extends Event {
        ArrivalEvent(Instant timestamp) {
            super(timestamp);
        }

        @Override
        void execute(SimulationContext context) {
            context.handleArrival(this);
        }
    }

    /**
     * Event representing a query completion (calls releaseGroup).
     */
    private static class CompletionEvent extends Event {
        private final Group group;
        private final Instant startTime;

        CompletionEvent(Instant timestamp, Group group, Instant startTime) {
            super(timestamp);
            this.group = group;
            this.startTime = startTime;
        }

        @Override
        void execute(SimulationContext context) {
            context.handleCompletion(this);
        }

        Group getGroup() {
            return group;
        }

        Instant getStartTime() {
            return startTime;
        }
    }

    /**
     * Simulation context that encapsulates all simulation state.
     */
    private static class SimulationContext {
        private final LoadBalancer loadBalancer;
        private final ManualClock clock;
        private final PriorityQueue<Event> eventQueue;
        private final SimulationConfig config;
        private final GroupStatistics statistics;
        private final Map<Integer, Integer> outstandingRequests;

        SimulationContext(LoadBalancer lb, ManualClock clock, SimulationConfig config, int numGroups) {
            this.loadBalancer = lb;
            this.clock = clock;
            this.eventQueue = new PriorityQueue<>();
            this.config = config;
            this.statistics = new GroupStatistics(numGroups);
            this.outstandingRequests = new HashMap<>();
            for (int i = 0; i < numGroups; i++) {
                outstandingRequests.put(i, 0);
            }
        }

        void handleArrival(ArrivalEvent event) {
            clock.setInstant(event.getTimestamp());

            Optional<Group> groupOpt = loadBalancer.takeGroup(null);
            if (groupOpt.isPresent()) {
                Group group = groupOpt.get();
                statistics.recordTakeGroup(group.id());

                // Track outstanding requests
                int newOutstanding = outstandingRequests.merge(group.id(), 1, Integer::sum);
                statistics.recordMaxOutstanding(group.id(), newOutstanding);

                // Schedule completion using gamma distribution
                double serviceTime = generateGamma(config.gammaShape, config.gammaScale);
                Instant completionTime = event.getTimestamp().plusMillis((long) serviceTime);
                eventQueue.add(new CompletionEvent(completionTime, group, event.getTimestamp()));
            }

            // Schedule next arrival using exponential distribution
            double interArrivalTime = generateExponential(config.exponentialMean);
            Instant nextArrivalTime = event.getTimestamp().plusMillis((long) interArrivalTime);

            if (nextArrivalTime.toEpochMilli() < config.endTime.toEpochMilli()) {
                eventQueue.add(new ArrivalEvent(nextArrivalTime));
            }
        }

        void handleCompletion(CompletionEvent event) {
            clock.setInstant(event.getTimestamp());

            Group group = event.getGroup();
            Duration duration = Duration.between(event.getStartTime(), event.getTimestamp());

            loadBalancer.releaseGroup(group, true, RequestDuration.of(duration));

            // Track outstanding requests
            outstandingRequests.merge(group.id(), -1, Integer::sum);
        }

        PriorityQueue<Event> getEventQueue() {
            return eventQueue;
        }

        GroupStatistics getStatistics() {
            return statistics;
        }
    }

    /**
     * Run a discrete event simulation of the load balancer.
     * Uses exponential inter-arrival times and gamma service times.
     */
    private void runSimulation(LoadBalancer.Policy policy) {
        SimulationConfig config = SimulationConfig.fromSystemProperties();

        // Create groups with dummy nodes
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < config.numGroups; i++) {
            Node node = new Node("test-cluster", i, "node-" + i, i);
            Group group = new Group(i, List.of(node)) {
                @Override
                public boolean hasSufficientCoverage() {
                    return true; // Simulate all groups as available
                }
            };
            groups.add(group);
        }

        // Create load balancer with specified policy
        LoadBalancer loadBalancer = new LoadBalancer(groups, policy);

        // Create manual clock starting at epoch
        ManualClock clock = new ManualClock(config.startTime);

        // Create simulation context
        SimulationContext context = new SimulationContext(
                loadBalancer, clock, config, config.numGroups);

        // Schedule first arrival at time 0
        context.getEventQueue().add(new ArrivalEvent(config.startTime));

        // Run event loop
        PriorityQueue<Event> eventQueue = context.getEventQueue();
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.poll();

            // Stop if we've exceeded simulation time
            if (event.getTimestamp().toEpochMilli() >= config.endTime.toEpochMilli()) {
                break;
            }

            event.execute(context);
        }

        // Print statistics
        context.getStatistics().printStatistics(policy);
    }

    @Test
    void testRoundRobinPolicy() {
        runSimulation(LoadBalancer.Policy.ROUNDROBIN);
    }

    @Test
    void testLatencyAmortizedOverRequestsPolicy() {
        runSimulation(LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_REQUESTS);
    }

    @Test
    void testLatencyAmortizedOverTimePolicy() {
        runSimulation(LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_TIME);
    }

    @Test
    void testBestOfRandom2Policy() {
        runSimulation(LoadBalancer.Policy.BEST_OF_RANDOM_2);
    }
}
