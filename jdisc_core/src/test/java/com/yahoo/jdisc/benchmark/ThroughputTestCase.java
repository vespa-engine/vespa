// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.benchmark;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CallableResponseDispatch;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ThroughputTestCase {

    private static final int NUM_REQUESTS = 100;
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 64;
    private static final int MIN_LOOPS = 0;
    private static final int MAX_LOOPS = 1024;

    private static final String HANDLER_URI = "http://localhost/";
    private static final URI REQUEST_URI = URI.create(HANDLER_URI);
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS * 2);
    private static long preventOptimization = 0;

    @Test
    void runUnthreadedMeasurementsWithWorkload() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        runMeasurements(driver, new UnthreadedHandler(MAX_LOOPS)); // warmup

        StringBuilder out = new StringBuilder();
        out.append("\n");
        out.append("      | ");
        for (int i = MIN_THREADS; i <= MAX_THREADS; i *= 2) {
            out.append(String.format("%10d", i));
        }
        out.append("\n");
        out.append("------+-");
        for (int i = MIN_THREADS; i <= MAX_THREADS; i *= 2) {
            out.append("----------");
        }
        out.append("\n");
        for (int i = MIN_LOOPS; i <= MAX_LOOPS; i = Math.max(1, i * 2)) {
            out.append(String.format("%5d | ", i));
            RequestHandler handler = new UnthreadedHandler(i);
            for (Long val : runMeasurements(driver, handler)) {
                out.append(String.format("%10d", val));
            }
            out.append("\n");
        }
        System.err.println(out);
        System.err.println(preventOptimization);
        assertTrue(driver.close());
    }

    @Test
    void runThreadedMeasurements() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        runMeasurements(driver, new ThreadedHandler()); // warmup

        Iterator<Long> it = runMeasurements(driver, new ThreadedHandler()).iterator();
        for (int numThreads = MIN_THREADS; numThreads <= MAX_THREADS; numThreads *= 2) {
            System.err.println(String.format("%2d threads: %10d", numThreads, it.next()));
        }
        assertTrue(driver.close());
    }

    private static List<Long> runMeasurements(TestDriver driver, RequestHandler handler) throws Exception {
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind(HANDLER_URI, handler);
        driver.activateContainer(builder);
        handler.release();
        List<Long> ret = new LinkedList<>();
        for (int i = MIN_THREADS; i <= MAX_THREADS; i *= 2) {
            ret.add(measureThroughput(driver, i));
        }
        return ret;
    }

    private static long measureThroughput(CurrentContainer container, int numThreads) throws Exception {
        List<RequestTask> tasks = new LinkedList<>();
        for (int i = 0; i < numThreads; ++i) {
            RequestTask task = new RequestTask(container);
            tasks.add(task);
        }
        List<Future<Long>> results = executor.invokeAll(tasks);
        long nanos = 0;
        for (Future<Long> res : results) {
            nanos = Math.max(nanos, res.get());
        }
        return (numThreads * NUM_REQUESTS * TimeUnit.SECONDS.toNanos(1)) / nanos;
    }

    private static class RequestTask implements Callable<Long> {

        final CurrentContainer container;

        RequestTask(CurrentContainer container) {
            this.container = container;
        }

        @Override
        public Long call() throws Exception {
            long time = System.nanoTime();
            for (int i = 0; i < NUM_REQUESTS; ++i) {
                new RequestDispatch() {

                    @Override
                    protected Request newRequest() {
                        Request request = new Request(container, REQUEST_URI);
                        request.setTimeout(600, TimeUnit.SECONDS);
                        return request;
                    }
                }.dispatch().get();
            }
            return System.nanoTime() - time;
        }
    }

    private static class UnthreadedHandler extends AbstractRequestHandler {

        final int numLoops;

        UnthreadedHandler(int numLoops) {
            this.numLoops = numLoops;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            ResponseDispatch.newInstance(Response.Status.OK).dispatch(handler);
            preventOptimization += nextLong();
            return null;
        }

        long nextLong() {
            Random rnd = new Random();
            int k = 0;
            for (int i = 0; i < numLoops; ++i) {
                k += rnd.nextInt();
            }
            return k;
        }
    }

    private static class ThreadedHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            executor.submit(new CallableResponseDispatch(handler) {

                @Override
                public Response newResponse() {
                    return new Response(Response.Status.OK);
                }
            });
            return null;
        }
    }
}
