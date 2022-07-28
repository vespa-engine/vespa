// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.benchmark;

import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.UriPattern;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class BindingMatchingTestCase {

    private static final int NUM_CANDIDATES = 1024;
    private static final int NUM_MATCHES = 100;
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 64;
    private static final Random random = new Random();
    private static final ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);

    @Test
    void runThroughtputMeasurements() throws Exception {
        System.err.format("%15s%15s%15s%15s%15s%15s%15s%15s\n",
                "No. of Bindings", "1 thread", "2 thread", "4 thread", "8 thread", "16 thread", "32 thread", "64 thread");
        for (int numBindings : Arrays.asList(1, 10, 25, 50, 100, 250)) {
            BindingRepository<Object> repo = new BindingRepository<>();
            for (int binding = 0; binding < numBindings; ++binding) {
                repo.bind("http://*/v" + binding + "/*/data/", new Object());
            }
            System.err.format("%15s", numBindings + " binding(s)");

            List<URI> candidates = newCandidates(repo);
            measureThroughput(repo.activate(), candidates, MAX_THREADS); // warmup

            BindingSet<Object> bindings = repo.activate();
            for (int numThreads = MIN_THREADS;
                 numThreads <= MAX_THREADS;
                 numThreads *= 2)
            {
                System.err.format("%15s", measureThroughput(bindings, candidates, numThreads));
            }
            System.err.format("\n");
        }
    }

    private static long measureThroughput(BindingSet<Object> bindings, List<URI> candidates, int numThreads) throws Exception {
        List<MatchTask> tasks = new LinkedList<>();
        for (int i = 0; i < numThreads; ++i) {
            MatchTask task = new MatchTask(bindings, candidates);
            tasks.add(task);
        }
        List<Future<Long>> results = executor.invokeAll(tasks);
        long nanos = 0;
        for (Future<Long> res : results) {
            nanos = Math.max(nanos, res.get());
        }
        return (numThreads * NUM_MATCHES * TimeUnit.SECONDS.toNanos(1)) / nanos;
    }

    private List<URI> newCandidates(BindingRepository<Object> bindings) {
        List<URI> lst = new ArrayList<>(NUM_CANDIDATES);
        Iterator<Map.Entry<UriPattern, Object>> it = bindings.iterator();
        for (int i = 0; i < NUM_CANDIDATES; ++i) {
            if (!it.hasNext()) {
                it = bindings.iterator();
            }
            lst.add(newCandidate(it.next().getKey()));
        }
        return lst;
    }

    private URI newCandidate(UriPattern key) {
        String pattern = key.toString();
        StringBuilder uri = new StringBuilder();
        for (int i = 0, len = pattern.length(); i < len; ++i) {
            char c = pattern.charAt(i);
            if (c == '*') {
                uri.append(random.nextInt(Integer.MAX_VALUE));
            } else {
                uri.append(c);
            }
        }
        return URI.create(uri.toString());
    }

    private static class MatchTask implements Callable<Long> {

        final BindingSet<Object> bindings;
        final List<URI> candidates;

        MatchTask(BindingSet<Object> bindings, List<URI> candidates) {
            this.bindings = bindings;
            this.candidates = candidates;
        }

        @Override
        public Long call() throws Exception {
            Iterator<URI> it = candidates.iterator();
            for (int i = 0, len = random.nextInt(candidates.size()); i < len; ++i) {
                it.next();
            }
            long time = System.nanoTime();
            for (int i = 0; i < NUM_MATCHES; ++i) {
                if (!it.hasNext()) {
                    it = candidates.iterator();
                }
                bindings.match(it.next());
            }
            return System.nanoTime() - time;
        }
    }
}
