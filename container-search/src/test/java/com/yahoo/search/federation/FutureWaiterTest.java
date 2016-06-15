// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;


/**
 * @author tonytv
 */
// TODO: Fix or remove!
public class FutureWaiterTest {

/*

    @MockClass(realClass = System.class)
    public static class MockSystem {

        private static long currentTime;
        private static boolean firstTime;

        private static final long startTime = 123;

        @Mock
        public static synchronized  long currentTimeMillis() {
            if (firstTime) {
                firstTime = false;
                return startTime;
            }
            return currentTime;
        }

        static synchronized void setElapsedTime(long elapsedTime) {
            firstTime = true;
            currentTime = elapsedTime + startTime;
        }
    }

    @Mocked()
    FutureResult result1;

    @Mocked()
    FutureResult result2;

    @Mocked()
    FutureResult result3;

    @Mocked()
    FutureResult result4;

    @Before
    public void before() {
        Mockit.setUpMock(FutureWaiterTest.MockSystem.class);
    }

    @After
    public void after() {
        Mockit.tearDownMocks();
    }

    @Test
    public void require_time_to_wait_is_adjusted_for_elapsed_time() {
        MockSystem.setElapsedTime(300);

        FutureWaiter futureWaiter = new FutureWaiter();
        futureWaiter.add(result1, 350);
        futureWaiter.waitForFutures();

        new FullVerifications() {
            {
                result1.get(350 - 300, TimeUnit.MILLISECONDS);
            }
        };
    }

    @Test
    public void require_do_not_wait_for_expired_timeouts() {
        MockSystem.setElapsedTime(300);

        FutureWaiter futureWaiter = new FutureWaiter();
        futureWaiter.add(result1, 300);
        futureWaiter.add(result2, 290);

        futureWaiter.waitForFutures();

        new FullVerifications() {
            {}
        };
    }

    @Test
    public void require_wait_for_largest_timeout_first() throws InterruptedException {
        MockSystem.setElapsedTime(600);

        FutureWaiter futureWaiter = new FutureWaiter();
        futureWaiter.add(result1, 500);
        futureWaiter.add(result4, 800);
        futureWaiter.add(result2, 600);
        futureWaiter.add(result3, 700);

        futureWaiter.waitForFutures();

        new FullVerifications() {
            {
                result4.get(800 - 600, TimeUnit.MILLISECONDS);
                result3.get(700 - 600, TimeUnit.MILLISECONDS);
            }
        };
    }

    */
}
