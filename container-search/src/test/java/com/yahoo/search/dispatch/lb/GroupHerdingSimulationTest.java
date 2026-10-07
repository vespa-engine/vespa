// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Discrete event simulation testing the hypothesis that brief stalls of single content nodes make all containers
 * move (almost) all traffic away from the stalled node's group at the same time, after which the traffic share
 * relaxes back over tens of seconds: sudden swings to ~90% of the traffic on one group, relaxing back to 50/50
 * over ~40 s, about once every two minutes.
 *
 * Why stalls, and why load balancing oscillating on its own does not explain such swings, is described
 * in {@link #BACKGROUND}, which is also printed before the scenario reports.
 *
 * Model:
 * <ul>
 *   <li>Several containers, each with its own {@link LoadBalancer}, dispatch to the same two groups.</li>
 *   <li>A group consists of nodes, each with a fixed number of match threads and a FIFO queue.
 *       A query is sent to all nodes of the selected group. The cost of a query is correlated across
 *       the nodes of the group (an expensive query is expensive everywhere).</li>
 *   <li>A request still in a node queue when the query times out is dropped, like proton does.</li>
 *   <li>A node stall pauses all match threads of one node for a while, delaying everything queued on that node.
 *       Requests that are already executing when the stall starts are not delayed (a simplification).</li>
 *   <li>The query result is reported to the load balancer like InterleavedSearchInvoker does:
 *       all nodes answered: success with the time of the slowest node;
 *       some nodes answered: success (degraded coverage) with the full timeout as duration;
 *       no nodes answered: failure, which is not used for latency tracking.</li>
 * </ul>
 *
 * Each scenario prints a report of how evenly queries were split between the two groups, both at 5 s resolution
 * and per minute as the metrics would show it, next to the values of the swing pattern it tries to reproduce.
 * Parameters can be overridden with system properties, see {@link Config}.
 *
 * @author boeker
 */
public class GroupHerdingSimulationTest {

    /** How the load balancer learns about queries where some, but not all, nodes answered before the timeout. */
    enum PartialResultSample {
        /** As in the current code: success, with the full timeout as duration */
        TIMEOUT,
        /** What-if: success, with the time of the slowest node that answered */
        ANSWERED
    }

    // The swing pattern the simulation tries to reproduce, for comparison
    private static final double TARGET_SHARE_STDDEV_60S = 0.13;
    private static final double TARGET_FRACTION_SLIGHTLY_UNEVEN_60S = 0.6;
    private static final double TARGET_FRACTION_UNEVEN_60S = 0.3;
    private static final double TARGET_SHARE_STDDEV_5S = 0.2;
    private static final double TARGET_FRACTION_UNEVEN_5S = 0.4;
    private static final double TARGET_SECONDS_BETWEEN_SWINGS = 120;
    private static final double TARGET_MEDIAN_PEAK_SHARE = 0.9;
    private static final double TARGET_MEDIAN_RELAXATION_SECONDS = 40;

    // One hour of the swing pattern: the share of queries to group 0 per minute as the metrics show it,
    // and per 5 s for the first 10 minutes.
    private static final String TARGET_MINUTE_SHARES = """
            0.48 0.27 0.42 0.65 0.63 0.52 0.64 0.60 0.41 0.22 0.29 0.34 0.67 0.65 0.65 0.59 0.51 0.50 0.50 0.50
            0.50 0.51 0.51 0.50 0.50 0.51 0.55 0.67 0.56 0.67 0.63 0.70 0.60 0.65 0.60 0.66 0.50 0.31 0.45 0.46
            0.44 0.40 0.51 0.65 0.61 0.41 0.47 0.50 0.54 0.59 0.48 0.40 0.54 0.70 0.47 0.40 0.54 0.62 0.44 0.38
            """;
    private static final String TARGET_DETAIL_SHARES_5S = """
            0.21 0.05 0.03 0.04 0.07 0.11 0.16 0.18 0.22 0.23 0.24 0.29
            0.36 0.43 0.45 0.44 0.43 0.45 0.47 0.48 0.48 0.47 0.43 0.30
            0.14 0.09 0.26 0.54 0.75 0.85 0.88 0.91 0.92 0.87 0.78 0.78
            0.82 0.84 0.82 0.74 0.66 0.61 0.58 0.55 0.52 0.51 0.51 0.53
            0.55 0.56 0.54 0.52 0.50 0.50 0.49 0.47 0.47 0.48 0.50 0.52
            0.54 0.55 0.54 0.52 0.50 0.51 0.60 0.70 0.80 0.88 0.86 0.85
            0.83 0.81 0.75 0.67 0.61 0.58 0.54 0.50 0.49 0.48 0.48 0.51
            0.54 0.54 0.53 0.52 0.52 0.51 0.50 0.49 0.50 0.49 0.44 0.28
            0.11 0.04 0.05 0.05 0.01 0.00 0.04 0.14 0.20 0.24 0.27 0.33
            0.40 0.44 0.46 0.50 0.52 0.49 0.30 0.10 0.13 0.18 0.20 0.16
            """;

    private static final int DETAIL_MINUTES = 10;
    private static final String LEVELS = "▁▂▃▄▅▆▇█";

    private static final String BACKGROUND = """
            BACKGROUND: WHY THIS SIMULATION TESTS NODE STALLS

            The swings to explain
              The share of queries sent to each of the two groups of a content cluster can swing far away from 50/50
              in the per-minute metrics for hours, while it is otherwise a steady 50/50.

            First hypothesis: the load balancing oscillates on its own
              The containers use ADAPTIVE load balancing: each container picks a group at random, weighted by the
              inverse of that group's recent average latency. The idea was a feedback loop: the group that gets more
              traffic gets slower, so all containers move traffic away from it, so the other group gets slower, etc.

            Why that did not explain it
              Simulating exactly that, without any disturbance, gives only short jitter of a few seconds that averages
              out within a minute (scenario 2 below). Two things prevent a minute-long oscillation:
              - The latency average forgets quickly: it is updated per request, over the last ~1000 requests, which
                is a few seconds of traffic per container.
              - Each container only learns from its own queries, so the containers react independently and
                their random fluctuations cancel out.

            What the swings look like at a finer resolution
              Because every content node reports its query count for a 60 s window ending at its own offset, the
              nodes together sample the traffic of each group many times per minute, so the traffic share can be
              reconstructed from these windows at 5 s resolution. At that resolution, the swings are not
              oscillations: about every two minutes, ~90% of the traffic suddenly jumps to one group within seconds,
              and then relaxes back to 50/50 over ~40 s.

            Why stalls
              For all containers to jump at the same moment, something must make one group look slow to all of
              them at once. A stall of a single node does that, since every query to a group waits for all of its
              nodes: all queries sent to the group during the stall become slow. The ~40 s relaxation matches how
              long it takes before the starved group has received enough new requests to replace its bad latency
              average. Match queues of single nodes that spike to well over a timeout's worth of work are a sign of
              such stalls.

            The scenarios below test this: scenario 1 adds stalls to a lightly loaded cluster, scenarios 2-5
            check that the swings need both the stalls and latency-based load balancing, scenario 6 checks
            whether a lower query timeout would mitigate them, and scenario 7 whether averaging latency over time
            instead of over requests would. Each scenario is compared to the values of the swing pattern shown below.
            """;

    private static final int RESOLUTION_SECONDS = 5;
    private static final double SLIGHTLY_UNEVEN_THRESHOLD = 0.08; // |share - 0.5| above this is "slightly uneven"
    private static final double UNEVEN_THRESHOLD = 0.15;          // |share - 0.5| above this is "uneven"
    private static final double SWING_THRESHOLD = 0.3;            // |share - 0.5| above this is a swing
    private static final double BALANCED_THRESHOLD = 0.1;         // |share - 0.5| below this is "back to balanced"

    private static class Config {

        // Defaults describe a lightly loaded cluster: mostly cheap queries, some ranked queries of a few ms,
        // a few queries per minute above 100 ms, and a 500 ms timeout.
        double totalQps = Double.parseDouble(System.getProperty("lb.herd.qps", "5000"));
        int containers = Integer.parseInt(System.getProperty("lb.herd.containers", "10"));
        int nodesPerGroup = Integer.parseInt(System.getProperty("lb.herd.nodes.per.group", "10"));
        int threadsPerNode = Integer.parseInt(System.getProperty("lb.herd.threads.per.node", "8"));
        double timeoutMs = Double.parseDouble(System.getProperty("lb.herd.timeout.ms", "500"));
        double networkMs = Double.parseDouble(System.getProperty("lb.herd.network.ms", "1"));

        // Query mix: mostly cheap queries, some ranked queries, and rare heavy queries
        double cheapMeanMs = Double.parseDouble(System.getProperty("lb.herd.cheap.ms", "0.1"));
        double rankedFraction = Double.parseDouble(System.getProperty("lb.herd.ranked.fraction", "0.20"));
        double rankedMeanMs = Double.parseDouble(System.getProperty("lb.herd.ranked.ms", "2.5"));
        double heavyFraction = Double.parseDouble(System.getProperty("lb.herd.heavy.fraction", "0.00005"));
        double heavyMinMs = Double.parseDouble(System.getProperty("lb.herd.heavy.min.ms", "100"));
        double heavyMaxMs = Double.parseDouble(System.getProperty("lb.herd.heavy.max.ms", "500"));

        // Node stalls: a random node in the cluster pauses for a while
        double stallsPerMinute = 0; // set by withStalls()
        double stallMinMs = Double.parseDouble(System.getProperty("lb.herd.stall.min.ms", "200"));
        double stallMaxMs = Double.parseDouble(System.getProperty("lb.herd.stall.max.ms", "800"));

        // By default, measure for one hour: long enough for dozens of swings, short enough to run quickly
        double warmupSeconds = Double.parseDouble(System.getProperty("lb.herd.warmup.time.s", "180"));
        double measuredSeconds = Double.parseDouble(System.getProperty("lb.herd.measured.time.s", "3600"));
        long seed = Long.parseLong(System.getProperty("lb.herd.seed", "42"));

        LoadBalancer.Policy policy = LoadBalancer.Policy.ADAPTIVE;
        PartialResultSample partialResultSample = PartialResultSample.TIMEOUT;

        Config withStalls() {
            stallsPerMinute = Double.parseDouble(System.getProperty("lb.herd.stalls.per.minute", "1"));
            return this;
        }

        double totalSeconds() { return warmupSeconds + measuredSeconds; }

        double meanServiceMs() {
            double heavyMeanMs = (heavyMinMs + heavyMaxMs) / 2;
            return (1 - rankedFraction - heavyFraction) * cheapMeanMs + rankedFraction * rankedMeanMs + heavyFraction * heavyMeanMs;
        }

        /** Utilization of a node's match threads if the load is split evenly between the two groups. */
        double balancedUtilization() {
            return (totalQps / 2) * meanServiceMs() / 1000 / threadsPerNode;
        }

    }

    /** A content node with a fixed number of match threads serving a FIFO queue. */
    private static class SimNode {

        private final double[] threadFreeAtMs;

        SimNode(int threads) { threadFreeAtMs = new double[threads]; }

        /**
         * Enqueues a request and returns the time at which it is answered,
         * or NaN if it expires in the queue and is dropped.
         */
        double serve(double arrivalMs, double serviceMs, double deadlineMs) {
            int thread = 0;
            for (int i = 1; i < threadFreeAtMs.length; i++)
                if (threadFreeAtMs[i] < threadFreeAtMs[thread]) thread = i;
            double startMs = Math.max(arrivalMs, threadFreeAtMs[thread]);
            if (startMs >= deadlineMs) return Double.NaN; // Expired in queue, dropped without doing any work
            threadFreeAtMs[thread] = startMs + serviceMs;
            return startMs + serviceMs;
        }

        /** Pauses all threads: no queued work starts before the stall is over, and queued work is pushed back. */
        void stall(double startMs, double durationMs) {
            for (int i = 0; i < threadFreeAtMs.length; i++)
                threadFreeAtMs[i] = Math.max(threadFreeAtMs[i], startMs) + durationMs;
        }

    }

    private record Release(double atMs, LoadBalancer loadBalancer, Group group, boolean success,
                           RequestDuration duration) implements Comparable<Release> {

        @Override
        public int compareTo(Release other) { return Double.compare(atMs, other.atMs); }

    }

    /** Statistics for one second of simulated time. */
    private static class SecondStats {

        final long[] queries = new long[2];
        long degraded = 0;
        long failed = 0;
        int stalls = 0;

    }

    private static class Simulation {

        private final Config config;
        private final Random random;
        private final List<Group> groups = new ArrayList<>();
        private final List<List<SimNode>> nodes = new ArrayList<>();
        private final List<LoadBalancer> loadBalancers = new ArrayList<>();
        private final PriorityQueue<Release> releases = new PriorityQueue<>();
        private final SecondStats[] seconds;

        Simulation(Config config) {
            this.config = config;
            this.random = new Random(config.seed);
            for (int g = 0; g < 2; g++) {
                List<Node> groupNodes = new ArrayList<>();
                List<SimNode> simNodes = new ArrayList<>();
                for (int n = 0; n < config.nodesPerGroup; n++) {
                    int key = g * config.nodesPerGroup + n;
                    groupNodes.add(new Node("test-cluster", key, "node-" + key, g, true));
                    simNodes.add(new SimNode(config.threadsPerNode));
                }
                groups.add(new Group(g, groupNodes) {
                    @Override
                    public boolean hasSufficientCoverage() { return true; }
                });
                nodes.add(simNodes);
            }
            for (int c = 0; c < config.containers; c++)
                loadBalancers.add(new LoadBalancer(groups, config.policy, "default", config.seed + 1 + c));
            seconds = new SecondStats[(int) Math.ceil(config.totalSeconds())];
            for (int i = 0; i < seconds.length; i++)
                seconds[i] = new SecondStats();
        }

        SecondStats[] run() {
            double endMs = config.totalSeconds() * 1000;
            double meanInterArrivalMs = 1000 / config.totalQps;
            double meanInterStallMs = config.stallsPerMinute > 0 ? 60_000 / config.stallsPerMinute : Double.POSITIVE_INFINITY;
            double nowMs = exponential(meanInterArrivalMs);
            double nextStallMs = exponential(meanInterStallMs);
            while (nowMs < endMs) {
                while ( ! releases.isEmpty() && releases.peek().atMs() <= nowMs)
                    release(releases.poll());
                while (nextStallMs <= nowMs) {
                    stall(nextStallMs);
                    nextStallMs += exponential(meanInterStallMs);
                }
                arrive(nowMs);
                nowMs += exponential(meanInterArrivalMs);
            }
            while ( ! releases.isEmpty())
                release(releases.poll());
            return seconds;
        }

        private SecondStats secondAt(double ms) {
            return seconds[(int) (ms / 1000)];
        }

        private void stall(double startMs) {
            SimNode node = nodes.get(random.nextInt(2)).get(random.nextInt(config.nodesPerGroup));
            node.stall(startMs, config.stallMinMs + random.nextDouble() * (config.stallMaxMs - config.stallMinMs));
            secondAt(startMs).stalls++;
        }

        private void arrive(double arrivalMs) {
            LoadBalancer loadBalancer = loadBalancers.get(random.nextInt(loadBalancers.size()));
            Group group = loadBalancer.takeAnyGroupNotIn(Set.of()).orElseThrow();
            SecondStats stats = secondAt(arrivalMs);
            stats.queries[group.id()]++;

            double deadlineMs = arrivalMs + config.timeoutMs;
            double queryCostMs = queryCostMs();
            double slowestAnsweredMs = arrivalMs;
            int answered = 0;
            for (SimNode node : nodes.get(group.id())) {
                double serviceMs = queryCostMs * gamma(4, 0.25); // Per-node variation around the query cost
                double doneMs = node.serve(arrivalMs + config.networkMs / 2, serviceMs, deadlineMs);
                if (Double.isNaN(doneMs)) continue;
                double replyMs = doneMs + config.networkMs / 2;
                if (replyMs > deadlineMs) continue;
                answered++;
                slowestAnsweredMs = Math.max(slowestAnsweredMs, replyMs);
            }

            boolean success;
            double durationMs;
            if (answered == config.nodesPerGroup) {
                success = true;
                durationMs = slowestAnsweredMs - arrivalMs;
            } else if (answered > 0) {
                stats.degraded++;
                success = true;
                durationMs = config.partialResultSample == PartialResultSample.TIMEOUT ? config.timeoutMs
                                                                                       : slowestAnsweredMs - arrivalMs;
            } else {
                stats.failed++;
                success = false;
                durationMs = config.timeoutMs;
            }
            Instant start = Instant.EPOCH.plusNanos((long) (arrivalMs * 1_000_000));
            Duration duration = Duration.ofNanos((long) (durationMs * 1_000_000));
            double releaseAtMs = (answered == config.nodesPerGroup) ? slowestAnsweredMs : deadlineMs;
            releases.add(new Release(releaseAtMs, loadBalancer, group, success, RequestDuration.of(start, duration)));
        }

        private void release(Release release) {
            release.loadBalancer().releaseGroup(release.group(), release.success(), release.duration());
        }

        private double queryCostMs() {
            double u = random.nextDouble();
            if (u < config.heavyFraction)
                return config.heavyMinMs + random.nextDouble() * (config.heavyMaxMs - config.heavyMinMs);
            if (u < config.heavyFraction + config.rankedFraction)
                return gamma(2, config.rankedMeanMs / 2);
            return exponential(config.cheapMeanMs);
        }

        private double exponential(double mean) {
            return -mean * Math.log(1.0 - random.nextDouble());
        }

        private double gamma(int shape, double scale) {
            double sum = 0;
            for (int i = 0; i < shape; i++)
                sum += exponential(scale);
            return sum;
        }

    }

    /** A swing: the share of one group rises from balanced to beyond SWING_THRESHOLD, then falls back. */
    private record Swing(double peakShare, double riseSeconds, double relaxationSeconds) { }

    /**
     * The share of queries sent to group 0 over a period, per 5 s and per minute as the metrics show it.
     * Minute m covers the 5 s values m * 12 to m * 12 + 11, and its metric value is the one shown at the end of it.
     */
    private static class ShareSeries {

        private final List<String> minuteLabels;
        private final double[] minuteShares;
        private final double[] shares5s;
        private final int[] stalls5s; // number of node stalls starting in each 5 s interval, or null if unknown

        ShareSeries(List<String> minuteLabels, double[] minuteShares, double[] shares5s, int[] stalls5s) {
            this.minuteLabels = minuteLabels;
            this.minuteShares = minuteShares;
            this.shares5s = shares5s;
            this.stalls5s = stalls5s;
        }

        static ShareSeries fromSimulation(SecondStats[] seconds, int first) {
            int minutes = (seconds.length - first) / 60;
            int perMinute = 60 / RESOLUTION_SECONDS;
            List<String> labels = new ArrayList<>();
            double[] minuteShares = new double[minutes];
            double[] shares5s = new double[minutes * perMinute];
            int[] stalls5s = new int[minutes * perMinute];
            for (int m = 0; m < minutes; m++) {
                labels.add(Text.format("%d:%02d", m / 60, m % 60));
                minuteShares[m] = metricShare(seconds, first + (m + 1) * 60);
            }
            for (int i = 0; i < shares5s.length; i++) {
                long q0 = 0, total = 0;
                for (int s = first + i * RESOLUTION_SECONDS; s < first + (i + 1) * RESOLUTION_SECONDS; s++) {
                    q0 += seconds[s].queries[0];
                    total += seconds[s].queries[0] + seconds[s].queries[1];
                    stalls5s[i] += seconds[s].stalls;
                }
                shares5s[i] = total == 0 ? 0.5 : (double) q0 / total;
            }
            return new ShareSeries(labels, minuteShares, shares5s, stalls5s);
        }

        /**
         * The share of queries to group 0 as the per-minute metrics show it at the given second.
         * The value comes from three steps which each add a delay of 0-60 s:
         * each content node counts queries in a 60 s window, the window ends at a node-specific offset during the
         * minute before the node is scraped, and the per-minute value is the last sample before the minute ends.
         * Summed over many nodes, this weights the last 180 s by a bell-shaped (quadratic B-spline) curve which
         * peaks 90 s before the value is shown.
         */
        private static double metricShare(SecondStats[] seconds, int end) {
            double q0 = 0, total = 0;
            for (int age = 0; age < 180; age++) {
                int s = end - 1 - age;
                if (s < 0) break;
                double x = (age + 0.5) / 60;
                double weight = x < 1 ? x * x / 2 : x < 2 ? (-2 * x * x + 6 * x - 3) / 2 : (3 - x) * (3 - x) / 2;
                q0 += weight * seconds[s].queries[0];
                total += weight * (seconds[s].queries[0] + seconds[s].queries[1]);
            }
            return total == 0 ? 0.5 : q0 / total;
        }

        /** Returns the swing pattern to reproduce, from TARGET_MINUTE_SHARES and TARGET_DETAIL_SHARES_5S. */
        static ShareSeries target() {
            double[] minuteShares = parseShares(TARGET_MINUTE_SHARES);
            List<String> labels = new ArrayList<>();
            for (int m = 0; m < minuteShares.length; m++)
                labels.add(Text.format("%d:%02d", m / 60, m % 60));
            return new ShareSeries(labels, minuteShares, parseShares(TARGET_DETAIL_SHARES_5S), null);
        }

        private static double[] parseShares(String shares) {
            return Arrays.stream(shares.trim().split("\\s+")).mapToDouble(Double::parseDouble).toArray();
        }

        double seconds() { return shares5s.length * RESOLUTION_SECONDS; }

        /** Finds swings: the share of one group goes beyond SWING_THRESHOLD and later back within BALANCED_THRESHOLD. */
        List<Swing> swings() {
            List<Swing> swings = new ArrayList<>();
            int i = 0;
            while (i < shares5s.length) {
                double deviation = shares5s[i] - 0.5;
                if (Math.abs(deviation) <= SWING_THRESHOLD) { i++; continue; }
                double sign = Math.signum(deviation);
                int start = i;
                while (start > 0 && Math.abs(shares5s[start] - 0.5) >= BALANCED_THRESHOLD) start--;
                int peak = i;
                while (peak + 1 < shares5s.length && (shares5s[peak + 1] - 0.5) * sign >= (shares5s[peak] - 0.5) * sign) peak++;
                int end = peak;
                while (end < shares5s.length && (shares5s[end] - 0.5) * sign >= BALANCED_THRESHOLD) end++;
                if (end < shares5s.length) // only count swings that end within the period
                    swings.add(new Swing(0.5 + Math.abs(shares5s[peak] - 0.5),
                                         (peak - start) * RESOLUTION_SECONDS,
                                         (end - peak) * RESOLUTION_SECONDS));
                i = end + 1;
            }
            return swings;
        }

        String secondsBetweenSwings() {
            List<Swing> swings = swings();
            return swings.isEmpty() ? "none" : Text.format("every %.0f s", seconds() / swings.size());
        }

        String medianPeakShare() {
            List<Swing> swings = swings();
            return swings.isEmpty() ? "-" : Text.format("%.2f", median(swings.stream().mapToDouble(Swing::peakShare).toArray()));
        }

        String medianRelaxation() {
            List<Swing> swings = swings();
            return swings.isEmpty() ? "-" : Text.format("%.0f s", median(swings.stream().mapToDouble(Swing::relaxationSeconds).toArray()));
        }

        /** Appends one character per minute, one line per hour, like a per-minute metrics graph. */
        void appendMinuteTimeline(StringBuilder out) {
            out.append("  Bar height is the share of queries to group 0:  ▁ 0-12%  ▂ 12-25%  ▃ 25-37%  ▄ 37-50%  " +
                       "▅ 50-62%  ▆ 62-75%  ▇ 75-87%  █ 87-100%\n");
            if (stalls5s != null)
                out.append("  Below each line: number of node stalls in that minute ('.' = none)\n");
            int perMinute = 60 / RESOLUTION_SECONDS;
            for (int hourStart = 0; hourStart < minuteShares.length; hourStart += 60) {
                StringBuilder bars = new StringBuilder();
                StringBuilder stallMarks = new StringBuilder();
                for (int m = hourStart; m < Math.min(hourStart + 60, minuteShares.length); m++) {
                    bars.append(LEVELS.charAt(Math.min(LEVELS.length() - 1, (int) (minuteShares[m] * LEVELS.length()))));
                    if (stalls5s != null) {
                        int stalls = 0;
                        for (int i = m * perMinute; i < (m + 1) * perMinute; i++) stalls += stalls5s[i];
                        stallMarks.append(stalls == 0 ? "." : stalls > 9 ? "+" : String.valueOf(stalls));
                    }
                }
                out.append(Text.format("  %5s  %s\n", minuteLabels.get(hourStart), bars));
                if (stalls5s != null)
                    out.append(Text.format("  %5s  %s\n", "", stallMarks));
            }
        }

        /** Appends the given minutes in detail: the per-minute metric value next to the 5 s values in that minute. */
        void appendDetail(StringBuilder out, int firstMinute, int minutes) {
            int perMinute = 60 / RESOLUTION_SECONDS;
            out.append("  minute  metric | share of queries to group 0 per 5 s" +
                       (stalls5s != null ? " ('*' = a node stall started in that interval)" : "") + "\n");
            for (int m = firstMinute; m < Math.min(firstMinute + minutes, minuteShares.length); m++) {
                out.append(Text.format("  %5s    %.2f  |", minuteLabels.get(m), minuteShares[m]));
                for (int i = m * perMinute; i < (m + 1) * perMinute; i++) {
                    boolean stall = stalls5s != null && stalls5s[i] > 0;
                    out.append(Text.format(" %.2f%s", shares5s[i], stall ? "*" : " "));
                }
                out.append("\n");
            }
        }

    }

    /** Prints the statistics of a simulated scenario next to the values of the swing pattern to reproduce. */
    private static class Report {

        private final String title;
        private final String question;
        private final Config config;
        private final SecondStats[] seconds; // all simulated seconds, including warmup
        private final int first;             // first measured second
        private final ShareSeries simulated;

        Report(String title, String question, Config config, SecondStats[] seconds) {
            this.title = title;
            this.question = question;
            this.config = config;
            this.seconds = seconds;
            this.first = (int) Math.ceil(config.warmupSeconds);
            this.simulated = ShareSeries.fromSimulation(seconds, first);
        }

        void print() {
            StringBuilder out = new StringBuilder();
            long queries = 0, group0 = 0, degraded = 0, failed = 0, stalls = 0;
            for (int s = first; s < seconds.length; s++) {
                queries += seconds[s].queries[0] + seconds[s].queries[1];
                group0 += seconds[s].queries[0];
                degraded += seconds[s].degraded;
                failed += seconds[s].failed;
                stalls += seconds[s].stalls;
            }
            double measured = seconds.length - first;

            out.append("\n").append("=".repeat(110)).append("\n");
            out.append(title).append("\n");
            out.append("Question: ").append(question).append("\n");
            out.append("-".repeat(110)).append("\n");
            out.append("SETUP\n");
            out.append(Text.format("  load balancing policy      %s (%s)\n", config.policy,
                                   config.partialResultSample == PartialResultSample.TIMEOUT
                                   ? "partial results count as taking the full timeout, as in the current code"
                                   : "what-if: partial results count as taking as long as the slowest node that answered"));
            out.append(Text.format("  traffic                    %d containers sending %.0f queries/s in total\n",
                                   config.containers, config.totalQps));
            out.append(Text.format("  content                    2 groups x %d nodes x %d match threads, %.0f%% busy when evenly split\n",
                                   config.nodesPerGroup, config.threadsPerNode, 100 * config.balancedUtilization()));
            out.append(Text.format("  query timeout              %.0f ms\n", config.timeoutMs));
            out.append(config.stallsPerMinute > 0
                       ? Text.format("  node stalls                %.1f per minute in the whole cluster, each %.0f-%.0f ms on one random node\n",
                                     config.stallsPerMinute, config.stallMinMs, config.stallMaxMs)
                       : "  node stalls                none\n");
            out.append(Text.format("  simulated time             %s measured, after %.0f s warmup\n", duration(measured), config.warmupSeconds));
            out.append("\n");

            out.append("OUTCOME\n");
            out.append(Text.format("  share of queries to group 0 overall      %.3f\n", (double) group0 / queries));
            out.append(Text.format("  node stalls                              %d\n", stalls));
            out.append(Text.format("  degraded results (some nodes timed out)  %.1f/s (%.2f%% of queries)\n",
                                   degraded / measured, 100.0 * degraded / queries));
            out.append(Text.format("  failed queries (no node answered)        %.1f/s\n", failed / measured));
            out.append("\n");

            out.append("SHARE OF QUERIES TO GROUP 0, COMPARED TO THE SWING PATTERN         simulation      swing pattern\n");
            out.append("  Per minute, as the metrics show it\n");
            appendRow(out, "standard deviation",
                      Text.format("%.2f", stddev(simulated.minuteShares)), Text.format("%.2f", TARGET_SHARE_STDDEV_60S));
            appendRow(out, Text.format("minutes outside %.2f-%.2f", 0.5 - SLIGHTLY_UNEVEN_THRESHOLD, 0.5 + SLIGHTLY_UNEVEN_THRESHOLD),
                      percent(fractionBeyond(simulated.minuteShares, SLIGHTLY_UNEVEN_THRESHOLD)),
                      percent(TARGET_FRACTION_SLIGHTLY_UNEVEN_60S));
            appendRow(out, Text.format("minutes outside %.2f-%.2f", 0.5 - UNEVEN_THRESHOLD, 0.5 + UNEVEN_THRESHOLD),
                      percent(fractionBeyond(simulated.minuteShares, UNEVEN_THRESHOLD)),
                      percent(TARGET_FRACTION_UNEVEN_60S));
            appendRow(out, "most uneven minute (distance from 0.50)",
                      Text.format("%.2f", maxDeviation(simulated.minuteShares)), "");
            out.append(Text.format("  Per %d s\n", RESOLUTION_SECONDS));
            appendRow(out, "standard deviation",
                      Text.format("%.2f", stddev(simulated.shares5s)), Text.format("%.2f", TARGET_SHARE_STDDEV_5S));
            appendRow(out, Text.format("time spent outside %.2f-%.2f", 0.5 - UNEVEN_THRESHOLD, 0.5 + UNEVEN_THRESHOLD),
                      percent(fractionBeyond(simulated.shares5s, UNEVEN_THRESHOLD)),
                      percent(TARGET_FRACTION_UNEVEN_5S));
            appendRow(out, Text.format("swings (one group gets more than %.0f%%)", 100 * (0.5 + SWING_THRESHOLD)),
                      simulated.secondsBetweenSwings(), Text.format("every %.0f s", TARGET_SECONDS_BETWEEN_SWINGS));
            appendRow(out, "median peak share of the favoured group",
                      simulated.medianPeakShare(), Text.format("%.2f", TARGET_MEDIAN_PEAK_SHARE));
            appendRow(out, Text.format("median time back to %.1f-%.1f after a peak", 0.5 - BALANCED_THRESHOLD, 0.5 + BALANCED_THRESHOLD),
                      simulated.medianRelaxation(), Text.format("%.0f s", TARGET_MEDIAN_RELAXATION_SECONDS));
            out.append("\n");

            out.append("TIMELINE PER MINUTE, AS THE METRICS WOULD SHOW IT (one character per minute, one line per hour)\n");
            simulated.appendMinuteTimeline(out);
            out.append("\n");
            out.append(Text.format("THE FIRST %d MINUTES IN DETAIL\n", DETAIL_MINUTES));
            simulated.appendDetail(out, 0, DETAIL_MINUTES);
            out.append("=".repeat(110)).append("\n");
            System.out.print(out);
        }

        private static void appendRow(StringBuilder out, String label, String simulated, String target) {
            out.append(Text.format("    %-60s %-15s %s\n", label, simulated, target));
        }

        private static String percent(double fraction) { return Text.format("%.0f%%", 100 * fraction); }

        private static String duration(double seconds) {
            long minutes = Math.round(seconds / 60);
            return minutes >= 60 ? Text.format("%d h %02d min", minutes / 60, minutes % 60) : Text.format("%d min", minutes);
        }

    }

    private static double fractionBeyond(double[] shares, double threshold) {
        int count = 0;
        for (double share : shares)
            if (Math.abs(share - 0.5) > threshold) count++;
        return (double) count / shares.length;
    }

    private static double maxDeviation(double[] shares) {
        double max = 0;
        for (double share : shares)
            max = Math.max(max, Math.abs(share - 0.5));
        return max;
    }

    private static double stddev(double[] values) {
        double mean = 0;
        for (double value : values) mean += value / values.length;
        double sum = 0;
        for (double value : values) sum += (value - mean) * (value - mean);
        return Math.sqrt(sum / values.length);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    @BeforeAll
    static void printBackground() {
        ShareSeries target = ShareSeries.target();
        StringBuilder out = new StringBuilder();
        out.append("\n").append("=".repeat(110)).append("\n");
        out.append(BACKGROUND);
        out.append("\n");
        out.append("THE SWING PATTERN TO REPRODUCE: SHARE OF QUERIES TO GROUP 0 DURING ONE HOUR\n");
        out.append("  Per minute, as the metrics show it (one character per minute):\n");
        target.appendMinuteTimeline(out);
        out.append("\n");
        out.append(Text.format("  The first %d minutes in detail, with the share per %d s:\n", DETAIL_MINUTES, RESOLUTION_SECONDS));
        target.appendDetail(out, 0, DETAIL_MINUTES);
        out.append("  Note how the per-minute value lags: it is a weighted average of the ~3 minutes before it is shown,\n");
        out.append("  and turns sudden jumps to ~0.05 or ~0.95 into moderate values between ~0.2 and ~0.8.\n");
        out.append("=".repeat(110)).append("\n");
        System.out.print(out);
    }

    private static void run(String title, String question, Config config) {
        SecondStats[] seconds = new Simulation(config).run();
        long queries = 0;
        for (SecondStats stats : seconds)
            queries += stats.queries[0] + stats.queries[1];
        assertEquals(config.totalQps * config.totalSeconds(), queries, config.totalQps * config.totalSeconds() * 0.05);
        new Report(title, question, config, seconds).print();
    }

    @Test
    void adaptiveWithNodeStalls() {
        run("1. ADAPTIVE with node stalls",
            "Do brief stalls of single nodes reproduce the swings?",
            new Config().withStalls());
    }

    @Test
    void adaptiveWithoutNodeStalls() {
        run("2. ADAPTIVE without node stalls (control)",
            "Are the swings caused by the stalls, rather than by the load balancer on its own?",
            new Config());
    }

    @Test
    void adaptiveWithNodeStallsWithoutTimeoutSamples() {
        Config config = new Config().withStalls();
        config.partialResultSample = PartialResultSample.ANSWERED;
        run("3. ADAPTIVE with node stalls, partial results not counted as taking the full timeout (what-if)",
            """
            Are the swings driven by the full-timeout latency samples of partial results?
              When only some nodes of a group answer before the timeout, the current code reports the query to the
              load balancer as a success that took the full timeout (~500 ms, ~100x a normal query). A few such
              samples multiply a group's latency average, so they were a suspected cause of the strong reaction.
              This scenario is identical to scenario 1, except that partial results are reported with the time of
              the slowest node that did answer. If the swings disappear, the timeout samples drive them, and not
              using them would be a fix. If the swings remain, the queries that are merely slowed down by a stall
              (all nodes answer, but late) are enough on their own.""",
            config);
    }

    @Test
    void roundRobinWithNodeStalls() {
        Config config = new Config().withStalls();
        config.policy = LoadBalancer.Policy.ROUNDROBIN;
        run("4. ROUNDROBIN with node stalls (control)",
            "Do the swings require latency-based load balancing?",
            config);
    }

    @Test
    void bestOfRandom2WithNodeStalls() {
        Config config = new Config().withStalls();
        config.policy = LoadBalancer.Policy.BEST_OF_RANDOM_2;
        run("5. BEST_OF_RANDOM_2 with node stalls (alternative policy)",
            "Would load balancing on outstanding requests instead of latency avoid the swings?",
            config);
    }

    @Test
    void latencyAmortizedOverTimeWithNodeStalls() {
        Config config = new Config().withStalls();
        config.policy = LoadBalancer.Policy.LATENCY_AMORTIZED_OVER_TIME;
        run("7. LATENCY_AMORTIZED_OVER_TIME with node stalls (alternative policy)",
            """
            Would averaging latency over time instead of over requests avoid the swings?
              ADAPTIVE updates a group's latency average per request, so a group that gets little traffic after a
              stall keeps its bad average for a long time: it needs ~1000 new requests per container to recover.
              LATENCY_AMORTIZED_OVER_TIME (dispatch-policy latency-amortized-over-time) instead weights each sample
              by the time since the previous one, over ~5 s, so a starved group recovers in a fixed time regardless
              of its traffic. This scenario is identical to scenario 1 except for the policy. If the swings become
              shorter but remain, the policy speeds up recovery but does not prevent the containers from moving
              their traffic at the same time.""",
            config);
    }

    @Test
    void adaptiveWithNodeStallsAndLowerTimeout() {
        Config config = new Config().withStalls();
        config.timeoutMs = Double.parseDouble(System.getProperty("lb.herd.lower.timeout.ms", "50"));
        run(Text.format("6. ADAPTIVE with node stalls and a %.0f ms timeout instead of 500 ms (mitigation)", config.timeoutMs),
            """
            Would a lower query timeout avoid the swings?
              A query to a group never takes longer than the timeout, so a lower timeout caps the latency samples
              a stall can produce: instead of waiting up to the full stall, queries to the stalled group return
              partial results (without the stalled node) when the timeout is reached. This scenario is identical
              to scenario 1 except for the timeout. If the swings disappear, a lower timeout is a mitigation. If
              they remain, even capped samples are far above the normal 1-3 ms and still make every container move
              its traffic, at the price of more degraded results.""",
            config);
    }

}
