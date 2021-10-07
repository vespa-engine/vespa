// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SimpleLoggerResultCallbackTest {
    @Test
    public void testAverageCalculation() {
        SimpleLoggerResultCallback logger = new SimpleLoggerResultCallback(new AtomicInteger(3), 0);
        Instant now = Instant.now();
        logger.newSamplingPeriod(now);
        Result result = mock(Result.class);
        when(result.isSuccess()).thenReturn(true);
        // 3 documents in 0.2 secs --> 15 docs/sec
        logger.onCompletion("1", result);
        logger.onCompletion("1", result);
        logger.onCompletion("1", result);
        double rate = logger.newSamplingPeriod(now.plusMillis(200)).rate;
        assertEquals(rate, 15., 0.1 /* delta */);
    }

    @Test
    public void testAverageCalculationExteremeValues() {
        SimpleLoggerResultCallback logger = new SimpleLoggerResultCallback(new AtomicInteger(3), 0);
        Instant now = Instant.now();
        logger.newSamplingPeriod(now);
        // 0 duration, 0 documents
        double rate = logger.newSamplingPeriod(now).rate;
        assertEquals(rate, 0, 0.1 /* delta */);
    }

    @Test
    public void testOutput() {
        SimpleLoggerResultCallback logger = new SimpleLoggerResultCallback(new AtomicInteger(3), 0);
        Instant now = Instant.now();
        logger.newSamplingPeriod(now);
        Result result = mock(Result.class);
        when(result.isSuccess()).thenReturn(true);
        // 3 documents in 0.2 secs --> 15 docs/sec
        logger.onCompletion("1", result);
        logger.onCompletion("1", result);
        logger.onCompletion("1", result);
        double rate = logger.newSamplingPeriod(now.plusMillis(200)).rate;
        assertEquals(rate, 15., 0.1 /* delta */);
    }

    @Test
    public void testPrintout() {
        ArrayList<String> outputList = new ArrayList<>();

        SimpleLoggerResultCallback logger = new SimpleLoggerResultCallback(new AtomicInteger(30), 0) {
            @Override
            protected void println(String output) {
                outputList.add(output);
            }
            @Override
            protected DocumentRate newSamplingPeriod(Instant now) {
                return new DocumentRate(19999999.2342342366664);
            }
        };
        // 2 success, 1 failure
        Result result = mock(Result.class);
        when(result.isSuccess()).thenReturn(true);
        logger.onCompletion("1", result);
        logger.onCompletion("1", result);
        when(result.isSuccess()).thenReturn(false);
        when(result.toString()).thenReturn("fooError");
        logger.onCompletion("1", result);
        logger.printProgress();
        assertThat(outputList.toString(),
                containsString("Result received: 3 (1 failed so far, 30 sent, success rate 19999999.23 docs/sec)."));
        assertThat(outputList.toString(), containsString("Failure: fooError"));
    }

}
