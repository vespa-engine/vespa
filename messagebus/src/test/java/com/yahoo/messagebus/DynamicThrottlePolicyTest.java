// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.concurrent.ManualTimer;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests are based on a simulated server, the {@link MockServer} below.
 * The purpose is to both verify the behaviour of the algorithm, while also providing a playground
 * for development, tuning, etc..
 *
 * @author jonmv
 */
public class DynamicThrottlePolicyTest {

    static final Message message = new SimpleMessage("message");
    static final Reply success = new SimpleReply("success");
    static final Reply error = new SimpleReply("error");
    static {
        success.setContext(message.getApproxSize());
        error.setContext(message.getApproxSize());
        error.addError(new Error(0, "overload"));
    }

    @Test
    void singlePolicyWithSmallWindows() {
        long operations = 1_000_000;
        int numberOfWorkers = 1;
        int maximumTasksPerWorker = 16;
        int workerParallelism = 12;

        { // This setup is lucky with the artificial local maxima for latency, and gives good results. See below for counter-examples.
            int workPerSuccess = 8;

            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer).setMinWindowSize(1)
                    .setWindowSizeIncrement(0.1)
                    .setResizeRate(100);
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy);

            double minMaxPending = numberOfWorkers * workerParallelism;
            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            System.err.println(operations / (double) timer.milliTime());
            assertInRange(minMaxPending, summary.averagePending, maxMaxPending);
            assertInRange(minMaxPending, summary.averageWindows[0], maxMaxPending);
            assertInRange(1, summary.inefficiency, 1.1);
            assertInRange(0, summary.waste, 0.01);
        }

        { // This setup is not so lucky, and the artificial behaviour pushes it into overload.
            int workPerSuccess = 5;

            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer).setMinWindowSize(1)
                    .setWindowSizeIncrement(0.1)
                    .setResizeRate(100);
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy);

            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            assertInRange(maxMaxPending, summary.averagePending, maxMaxPending * 1.1);
            assertInRange(maxMaxPending, summary.averageWindows[0], maxMaxPending * 1.1);
            assertInRange(1.2, summary.inefficiency, 1.5);
            assertInRange(0.5, summary.waste, 1.5);
        }

        { // This setup is not so lucky either, and the artificial behaviour keeps it far below a good throughput.
            int workPerSuccess = 4;

            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer).setMinWindowSize(1)
                    .setWindowSizeIncrement(0.1)
                    .setResizeRate(100);
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy);

            double minMaxPending = numberOfWorkers * workerParallelism;
            assertInRange(0.3 * minMaxPending, summary.averagePending, 0.5 * minMaxPending);
            assertInRange(0.3 * minMaxPending, summary.averageWindows[0], 0.5 * minMaxPending);
            assertInRange(2, summary.inefficiency, 4);
            assertInRange(0, summary.waste, 0);
        }
    }

    /** Sort of a dummy test, as the conditions are perfect. In a more realistic scenario, below, the algorithm needs luck to climb this high. */
    @Test
    void singlePolicySingleWorkerWithIncreasingParallelism() {
        for (int exponent = 0; exponent < 4; exponent++) {
            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);
            int scaleFactor = (int) Math.pow(10, exponent);
            long operations = 3_000L * scaleFactor;
            int workPerSuccess = 6;
            int numberOfWorkers = 1;
            int maximumTasksPerWorker = 100000;
            int workerParallelism = scaleFactor;
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy);

            double minMaxPending = numberOfWorkers * workerParallelism;
            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            assertInRange(minMaxPending, summary.averagePending, maxMaxPending);
            assertInRange(minMaxPending, summary.averageWindows[0], maxMaxPending);
            assertInRange(1, summary.inefficiency, 1 + (5e-5 * scaleFactor)); // Slow ramp-up
            assertInRange(0, summary.waste, 0.1);
        }
    }

    /** A more realistic test, where throughput gradually flattens with increasing window size, and with more variance in throughput. */
    @Test
    void singlePolicyIncreasingWorkersWithNoParallelism() {
        for (int exponent = 0; exponent < 4; exponent++) {
            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy = new DynamicThrottlePolicy(timer);
            int scaleFactor = (int) Math.pow(10, exponent);
            long operations = 2_000L * scaleFactor;
            // workPerSuccess determines the latency of the simulated server, which again determines the impact of the
            // synthetic attractors of the algorithm, around latencies which give (close to) integer log10(1 / latency).
            // With a value of 5, the impact is that the algorithm is pushed upwards slightly above 10k window size,
            // which is the optimal choice for the case with 10000 clients.
            // Change this to, e.g., 6 and the algorithm fails to climb as high, as the "ideal latency" is obtained at
            // a lower latency than what is measured at 10k window size.
            // On the other hand, changing it to 4 moves the attractor out of reach for the algorithm, which fails to
            // push window size past 2k on its own.
            int workPerSuccess = 5;
            int numberOfWorkers = scaleFactor;
            int maximumTasksPerWorker = 100000;
            int workerParallelism = 1;
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy);

            double minMaxPending = numberOfWorkers * workerParallelism;
            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            assertInRange(minMaxPending, summary.averagePending, maxMaxPending);
            assertInRange(minMaxPending, summary.averageWindows[0], maxMaxPending);
            assertInRange(1, summary.inefficiency, 1 + 0.25 * exponent); // Even slower ramp-up.
            assertInRange(0, summary.waste, 0);
        }
    }

    @Test
    void twoWeightedPoliciesWithUnboundedTaskQueue() {
        for (int repeat = 0; repeat < 3; repeat++) {
            long operations = 1_000_000;
            int workPerSuccess = 6 + (int) (30 * Math.random());
            int numberOfWorkers = 1 + (int) (10 * Math.random());
            int maximumTasksPerWorker = 100_000;
            int workerParallelism = 32;
            ManualTimer timer = new ManualTimer();
            DynamicThrottlePolicy policy1 = new DynamicThrottlePolicy(timer);
            DynamicThrottlePolicy policy2 = new DynamicThrottlePolicy(timer).setWeight(0.5);
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policy1, policy2);

            double minMaxPending = numberOfWorkers * workerParallelism;
            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            assertInRange(minMaxPending, summary.averagePending, maxMaxPending);
            // Actual shares are not distributed perfectly proportionally to weights, but close enough.
            assertInRange(minMaxPending * 0.6, summary.averageWindows[0], maxMaxPending * 0.6);
            assertInRange(minMaxPending * 0.4, summary.averageWindows[1], maxMaxPending * 0.4);
            assertInRange(1, summary.inefficiency, 1.02);
            assertInRange(0, summary.waste, 0);
        }
    }

    @Test
    void tenPoliciesVeryParallelServerWithShortTaskQueue() {
        for (int repeat = 0; repeat < 2; repeat++) {
            long operations = 1_000_000;
            int workPerSuccess = 6;
            int numberOfWorkers = 6;
            int maximumTasksPerWorker = 180 + (int) (120 * Math.random());
            int workerParallelism = 60 + (int) (40 * Math.random());
            ManualTimer timer = new ManualTimer();
            int p = 10;
            DynamicThrottlePolicy[] policies = IntStream.range(0, p)
                    .mapToObj(j -> new DynamicThrottlePolicy(timer)
                            .setWeight((j + 1.0) / p)
                            .setWindowSizeIncrement(5)
                            .setMinWindowSize(1))
                    .toArray(DynamicThrottlePolicy[]::new);
            Summary summary = run(operations, workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism, timer, policies);

            double minMaxPending = numberOfWorkers * workerParallelism;
            double maxMaxPending = numberOfWorkers * maximumTasksPerWorker;
            assertInRange(minMaxPending, summary.averagePending, maxMaxPending);
            for (int j = 0; j < p; j++) {
                double expectedShare = (j + 1) / (0.5 * p * (p + 1));
                double imperfectionFactor = 1.6;
                // Actual shares are not distributed perfectly proportionally to weights, but close enough.
                assertInRange(minMaxPending * expectedShare / imperfectionFactor,
                        summary.averageWindows[j],
                        maxMaxPending * expectedShare * imperfectionFactor);
            }
            assertInRange(1.0, summary.inefficiency, 1.05);
            assertInRange(0, summary.waste, 0.1);
        }
    }

    static void assertInRange(double lower, double actual, double upper) {
        System.err.printf("%10.4f  <= %10.4f  <= %10.4f\n", lower, actual, upper);
        assertTrue(lower <= actual, actual + " should be not be smaller than " + lower);
        assertTrue(upper >= actual, actual + " should be not be greater than " + upper);
    }

    private Summary run(long operations, int workPerSuccess, int numberOfWorkers, int maximumTasksPerWorker,
                        int workerParallelism, ManualTimer timer, DynamicThrottlePolicy... policies) {
        System.err.printf("\n### Running %d operations of %d ticks each against %d workers with parallelism %d and queue size %d\n",
                          operations, workPerSuccess, numberOfWorkers, workerParallelism, maximumTasksPerWorker);

        List<Integer> order = IntStream.range(0, policies.length).boxed().collect(toList());
        MockServer resource = new MockServer(workPerSuccess, numberOfWorkers, maximumTasksPerWorker, workerParallelism);
        AtomicLong outstanding = new AtomicLong(operations);
        AtomicLong errors = new AtomicLong(0);
        long ticks = 0;
        long totalPending = 0;
        double[] windows = new double[policies.length];
        int[] pending = new int[policies.length];
        while (outstanding.get() + resource.pending() > 0) {
            Collections.shuffle(order);
            for (int j = 0; j < policies.length; j++) {
                int i = order.get(j);
                DynamicThrottlePolicy policy = policies[i];
                windows[i] += policy.getWindowSize();
                while (policy.canSend(message, pending[i])) {
                    outstanding.decrementAndGet();
                    policy.processMessage(message);
                    ++pending[i];
                    resource.send(successful -> {
                        --pending[i];
                        if (successful)
                            policy.processReply(success);
                        else {
                            errors.incrementAndGet();
                            outstanding.incrementAndGet();
                            policy.processReply(error);
                        }
                    });
                }
            }
            ++ticks;
            totalPending += resource.pending();
            resource.tick();
            timer.advance(1);
        }

        for (int i = 0; i < windows.length; i++)
            windows[i] /= ticks;

        return new Summary(timer.milliTime() / (workPerSuccess * operations / (double) numberOfWorkers) * workerParallelism,
                           errors.get() / (double) operations,
                           totalPending / (double) ticks,
                           windows);
    }

    static class Summary {
        final double inefficiency;
        final double waste;
        final double averagePending;
        final double[] averageWindows;
        Summary(double inefficiency, double waste, double averagePending, double[] averageWindows) {
            this.inefficiency = inefficiency;           // Time spent working / minimum time possible
            this.waste = waste;                     // Number of error replies / number of successful replies
            this.averagePending = averagePending;   // Average number of pending operations in the server
            this.averageWindows = averageWindows;   // Average number of pending operations per policy
        }
    }

    /**
     * Resource shared between clients with throttle policies, with simulated throughput and efficiency.
     *
     * The model used for delay per request, and success/error, is derived from four basic attributes:
     * <ul>
     *     <li>Ratio between work per successful and per failed reply</li>
     *     <li>Number of workers, each with minimum throughput equal to one failed reply per tick</li>
     *     <li>Parallelism of each worker — throughput increases linearly with queued tasks up to this number</li>
     *     <li>Maximum number of queued tasks per worker</li>
     * </ul>
     * <p>All messages are assumed to get a successful reply unless maximum pending replies is exceeded; when further
     * messages arrive, the worker must immediately spend work to reject these before continuing its other work.
     * The delay for a message is computed by assigning it to a random worker, and simulating the worker emptying its
     * work queue. Since messages are assigned randomly, there will be some variation in delays and maximum throughput.
     * The local correlation between max number of in-flight messages from the client, and its throughput,
     * measured as number of successful replies per time unit, will start out at 1 and decrease gradually,
     * eventually turning negative, as workers must spend work effort on failure replies as well.</p>
     * <p> More specifically, a single worker yields a piecewise linear relationship between max pending and throughput —
     * throughput first increases linearly with max pending, until saturated, and then remains constant, until overload,
     * where it falls sharply. Several such workers together instead yield a throughput curve which gradually flattens
     * as it approaches saturation, and also more gradually falls again, as overload is reached on some workers sometimes.</p>
     */
    static class MockServer {

        final Random random = new Random();
        final int workPerSuccess;
        final int numberOfWorkers;
        final int maximumTaskPerWorker;
        final int workerParallelism;
        final int[] currentTask;
        final List<Deque<Consumer<Boolean>>> outstandingTasks;
        int pending = 0;

        MockServer(int workPerSuccess, int numberOfWorkers, int maximumTaskPerWorker, int workerParallelism) {
            this.workPerSuccess = workPerSuccess;
            this.numberOfWorkers = numberOfWorkers;
            this.maximumTaskPerWorker = maximumTaskPerWorker;
            this.workerParallelism = workerParallelism;
            this.currentTask = new int[numberOfWorkers];
            this.outstandingTasks = IntStream.range(0, numberOfWorkers)
                                             .mapToObj(__ -> new ArrayDeque<Consumer<Boolean>>())
                                             .collect(toUnmodifiableList());
        }

        void tick() {
            for (int i = 0; i < numberOfWorkers; i++)
                tick(i);
        }

        private void tick(int worker) {
            Deque<Consumer<Boolean>> tasks = outstandingTasks.get(worker);
            for (int i = 0; i < Math.min(workerParallelism, tasks.size()); i++) {
                if (currentTask[worker] == 0) {
                    if (tasks.size() > maximumTaskPerWorker) {
                        tasks.pop().accept(false);
                        continue; // Spend work to signal failure to one excess task.
                    }
                    currentTask[worker] = workPerSuccess; // Start work on next task.
                }
                if (--currentTask[worker] == 0)
                    tasks.poll().accept(true); // Signal success to the completed task.
            }
        }

        void send(Consumer<Boolean> replyHandler) {
            ++pending;
            outstandingTasks.get(random.nextInt(numberOfWorkers))
                            .addLast(outcome -> { --pending; replyHandler.accept(outcome); });
        }

        int pending() { return pending; }

    }

}