// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.search.searchchain.FutureResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tonytv
 */
class FutureWaiter {

    private class Future {
        final FutureResult result;
        final long timeoutInMilliseconds;

        public Future(FutureResult result, long timeoutInMilliseconds) {
            this.result = result;
            this.timeoutInMilliseconds = timeoutInMilliseconds;
        }
    }

    private List<Future> futures = new ArrayList<>();

    public void add(FutureResult futureResult, long timeoutInMilliseconds) {
        futures.add(new Future(futureResult, timeoutInMilliseconds));
    }

    public void waitForFutures() {
        sortFuturesByTimeoutDescending();

        long startTime = System.currentTimeMillis();

        for (Future future : futures) {
            long timeToWait = startTime + future.timeoutInMilliseconds - System.currentTimeMillis();
            if (timeToWait <= 0)
                break;

            future.result.get(timeToWait, TimeUnit.MILLISECONDS);
        }
    }

    private void sortFuturesByTimeoutDescending() {
        Collections.sort(futures, new Comparator<Future>() {
            @Override
            public int compare(Future lhs, Future rhs) {
                return -compareLongs(lhs.timeoutInMilliseconds, rhs.timeoutInMilliseconds);
            }

            private int compareLongs(long lhs, long rhs) {
                return new Long(lhs).compareTo(rhs);
            }
        });
    }

}
