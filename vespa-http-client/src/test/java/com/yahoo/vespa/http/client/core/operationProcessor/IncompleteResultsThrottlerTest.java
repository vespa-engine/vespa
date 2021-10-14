// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.ManualClock;
import com.yahoo.vespa.http.client.core.ThrottlePolicy;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IncompleteResultsThrottlerTest {

    @Test
    public void simpleStaticQueueSizeTest() {
        IncompleteResultsThrottler incompleteResultsThrottler = new IncompleteResultsThrottler(2, 2, null, null);
        assertEquals(0, incompleteResultsThrottler.waitingThreads());
        incompleteResultsThrottler.operationStart();
        incompleteResultsThrottler.operationStart();
        assertEquals(2, incompleteResultsThrottler.waitingThreads());
        incompleteResultsThrottler.resultReady(true);
        assertEquals(1, incompleteResultsThrottler.waitingThreads());
        incompleteResultsThrottler.resultReady(true);
        assertEquals(0, incompleteResultsThrottler.waitingThreads());
    }

    /**
     * Simulate running requests.
     * @param clientCount number of parallel clients.
     * @param breakPoint how many requests the server should handle in parallel before it gets slower.
     * @param simulationTimeMs how many ms to simulate.
     * @return median queue length.
     */
   int getAverageQueue(int clientCount, int breakPoint, int simulationTimeMs) {
       ManualClock clock = new ManualClock(Instant.ofEpochMilli(0));

       ArrayList<IncompleteResultsThrottler> incompleteResultsThrottlers = new ArrayList<>();

       MockServer mockServer = new MockServer(breakPoint);
       for (int x = 0; x < clientCount; x++) {
           IncompleteResultsThrottler incompleteResultsThrottler =
                   new IncompleteResultsThrottler(10, 50000, clock, new ThrottlePolicy());
           incompleteResultsThrottlers.add(incompleteResultsThrottler);
       }
       long sum = 0;
       long samples = 0;

       for (long time = 0; time < simulationTimeMs; time++) {
           // Fast forward, if we can. If all clients are blocked, we can move to the time when the server has a
           // request that is finished.
           boolean fastForward = true;
           for (int x = 0; x < clientCount; x++) {
               if (incompleteResultsThrottlers.get(x).availableCapacity() > 0 ) {
                   fastForward = false;
                   break;
               }
           }
           if (fastForward) {
               time = mockServer.nextRequestFinished();
           }
           clock.setInstant(Instant.ofEpochMilli(time));
           mockServer.moveTime(clock.instant().toEpochMilli());
           for (int y = 0; y < clientCount; y++) {
               // Fill up, but don't block as that would stop the simulation.
               while (incompleteResultsThrottlers.get(y).availableCapacity() > 0) {
                   incompleteResultsThrottlers.get(y).operationStart();
                   mockServer.newRequest(incompleteResultsThrottlers.get(y));
               }
           }
           // Don't take the first iterations into account as the system is eagerly learning.
           if (time > 60*1000) {
               sum += mockServer.messageDoneByTime.size();
               samples ++;
           }
       }
       return (int)(sum/samples);
   }

    private void testAndPrintVariousClientSizes(int breakPoint) {
        final int sampleRuns = 6;
        final int maxParallelClients = 4;
        final int minParallelClients = 1;
        final int simulationTimeMs = 400000;
        System.out.print("\nBreakpoint is " + breakPoint + ", average queue on server:");
        int[][] resultQueuesAverage = new int[maxParallelClients][sampleRuns];
        for (int clientNo = minParallelClients; clientNo <= maxParallelClients; clientNo++) {
            System.out.print("\nNow with " + clientNo + " parallel clients:");
            long sum = 0;
            for (int x = 0; x < sampleRuns; x++) {
                resultQueuesAverage[clientNo-minParallelClients][x] = getAverageQueue(1 + x, breakPoint, simulationTimeMs);
                System.out.print(" " + resultQueuesAverage[clientNo-minParallelClients][x]);
                sum += resultQueuesAverage[clientNo-minParallelClients][x];
            }
            System.out.print(" average is " + sum/sampleRuns);
            Arrays.sort(resultQueuesAverage[clientNo - minParallelClients]);
            int median = resultQueuesAverage[clientNo - minParallelClients][sampleRuns/2];
            System.out.print(" median is " + median);
            System.out.print(" min " + resultQueuesAverage[clientNo - minParallelClients][0]);
            System.out.print(" max " + resultQueuesAverage[clientNo - minParallelClients][sampleRuns - 1]);
            assertTrue(median < 2 * breakPoint + 200);
            assertTrue(median > breakPoint / 10);
        }
    }

    @Test
    public void testVariousBreakpoints() {
        testAndPrintVariousClientSizes(200);
        testAndPrintVariousClientSizes(1000);
    }

    List<Thread> threads = new ArrayList<>();

    private void postOperations(int count, final IncompleteResultsThrottler throttler) {
        for (int i = 0; i < count; i++) {
            Thread thread = new Thread(()->throttler.operationStart());
            thread.start();
            threads.add(thread);
        }
    }

    private void waitForThreads() throws InterruptedException {
        while(!threads.isEmpty()) {
            threads.remove(0).join();
        }
    }

    private void postSuccesses(int count, final IncompleteResultsThrottler throttler) {
        for (int i = 0; i < count; i++) {
            throttler.resultReady(true);
        }
    }

    private void moveToNextCycle(final IncompleteResultsThrottler throttler, ManualClock clock)
            throws InterruptedException {
        waitForThreads();
        // Enter an adaption phase, we don't care about this phase.
        clock.advance(Duration.ofMillis(throttler.phaseSizeMs));
        throttler.operationStart();
        throttler.resultReady(false);
        // Now enter the real next phase.
        clock.advance(Duration.ofMillis(throttler.phaseSizeMs));
        throttler.operationStart();
        throttler.resultReady(false);
    }

    @Test
    public void testInteractionWithPolicyByMockingPolicy() throws InterruptedException {
        ManualClock clock = new ManualClock(Instant.ofEpochMilli(0));
        final int MAX_SIZE = 1000;
        final int MORE_THAN_MAX_SIZE = MAX_SIZE + 20;
        final int SIZE_AFTER_CYCLE_FIRST = 30;
        final int SIZE_AFTER_CYCLE_SECOND = 5000;
        ThrottlePolicy policy = mock(ThrottlePolicy.class);
        IncompleteResultsThrottler incompleteResultsThrottler =
                new IncompleteResultsThrottler(2, MAX_SIZE, clock, policy);
        long bucketSizeMs = incompleteResultsThrottler.phaseSizeMs;

        // Cycle 1 - Algorithm has fixed value for max-in-flight: INITIAL_MAX_IN_FLIGHT_VALUE.
        // We post a few operations, not all finishing in this cycle. We explicitly do not fill the window
        // size to test the argument about any requests blocked.
        assertEquals(IncompleteResultsThrottler.INITIAL_MAX_IN_FLIGHT_VALUE,
                     incompleteResultsThrottler.availableCapacity());
        postOperations(20, incompleteResultsThrottler);
        postSuccesses(15, incompleteResultsThrottler);
        moveToNextCycle(incompleteResultsThrottler, clock);


        // Cycle 2 - Algorithm has fixed value also for second iteration: SECOND_MAX_IN_FLIGHT_VALUE.
        // Test verifies that this value is used, and insert a value to be used for next phase SIZE_AFTER_CYCLE_FIRST.
        assertEquals("5 slots already taken earlier",
                     IncompleteResultsThrottler.SECOND_MAX_IN_FLIGHT_VALUE - 5,
                     incompleteResultsThrottler.availableCapacity());
        postSuccesses(5, incompleteResultsThrottler);
        when(policy.calcNewMaxInFlight(
                anyDouble(),  // Max performance change
                eq(5), //numOk
                eq(15), // previousNumOk
                eq(IncompleteResultsThrottler.INITIAL_MAX_IN_FLIGHT_VALUE), // previous size
                eq(IncompleteResultsThrottler.SECOND_MAX_IN_FLIGHT_VALUE),  // current size
                eq(false)))  // is any request blocked, should be false since we only posted 20 docs.
                .thenReturn(SIZE_AFTER_CYCLE_FIRST);
        moveToNextCycle(incompleteResultsThrottler, clock);

        // Cycle 3 - Test that value set in previous phase is used. Now return a very large number.
        // However, this number should be cropped by the system (tested in next cycle).
        assertEquals(SIZE_AFTER_CYCLE_FIRST, incompleteResultsThrottler.availableCapacity());
        postOperations(MORE_THAN_MAX_SIZE, incompleteResultsThrottler);
        postSuccesses(MORE_THAN_MAX_SIZE, incompleteResultsThrottler);
        when(policy.calcNewMaxInFlight(
                anyDouble(), // Max performance change
                eq(MORE_THAN_MAX_SIZE), //numOk
                eq(5), // previousNumOk
                eq(IncompleteResultsThrottler.SECOND_MAX_IN_FLIGHT_VALUE), // previous size
                eq(SIZE_AFTER_CYCLE_FIRST),// current size
                eq(true))) // is any request blocked, should be true since we posted MORE_THAN_MAX_SIZE docs.
                .thenReturn(SIZE_AFTER_CYCLE_SECOND);
        moveToNextCycle(incompleteResultsThrottler, clock);

        // Cycle 4 - Test that the large number from previous cycle is cropped and that max value is used instead.
        assertEquals(MAX_SIZE, incompleteResultsThrottler.availableCapacity());
    }

    private long inversesU(int size, int sweetSpot) {
        // Peak performance at sweetSPot.
        int distance = Math.abs(sweetSpot - size);
        return 1 + 20 * distance;
    }

    /**
     * A mock 'gateway' this is slower with more requests in-flight. It starts to become really much slower at
     * 'breakPoint' number of parallel requests.
     */
    class MockServer {
        final LinkedList<Tuple2<Long, IncompleteResultsThrottler> > messageDoneByTime = new LinkedList<>();
        final int breakPoint;
        final Random random = new Random();
        long time = 0;

        MockServer(int breakPoint) {
            this.breakPoint = breakPoint;
        }

        /**
         * Figures out when next processed data will be ready.
         * @return time in ms for next request to be finished.
         */
        long nextRequestFinished() {
            if (messageDoneByTime.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            return messageDoneByTime.peek().first;
        }

        /**
         * Advance simulation time and call finished on any requests.
         * @param time to move to
         */
        void moveTime(long time) {
            this.time = time;
            while (!messageDoneByTime.isEmpty() && messageDoneByTime.peek().first <= time) {
                messageDoneByTime.pop().second.resultReady(true);
            }
        }

        /**
         * New request.
         * @param blocker do callback on blocker when request is done.
         */
        void newRequest(IncompleteResultsThrottler blocker) {
            long nextTime = (long)(20 + 0.1 * messageDoneByTime.size());

            if (messageDoneByTime.size() > breakPoint) {
                nextTime += (long) (40 + (random.nextDouble()) * 0.01 *  messageDoneByTime.size()* messageDoneByTime.size());
            }
            nextTime += time + random.nextInt()%4;
            messageDoneByTime.push(new Tuple2<>(nextTime, blocker));
        }
    }

    private static class Tuple2<T1, T2> {

        public final T1 first;
        public final T2 second;

        public Tuple2(final T1 first, final T2 second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int hashCode() { throw new UnsupportedOperationException(); }

        @Override
        public boolean equals(final Object obj) { throw new UnsupportedOperationException(); }

        @Override
        public String toString() {
            return "Tuple2(" + first + ", " + second + ")";
        }

    }

}
