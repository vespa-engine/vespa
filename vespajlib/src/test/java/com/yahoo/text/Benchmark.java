// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

// import com.google.common.base.Preconditions;
// import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
class Benchmark {

    public static interface Task {
        public long run(CyclicBarrier barrier, int numIterations) throws Exception;
    }


    public static class TaskProvider {
        final Class<? extends Task> taskClass;
        public Task get() {
            try {
                return taskClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        public TaskProvider(final Class<? extends Task> taskClass) {
            this.taskClass = taskClass;
        }
    }

    private final TaskProvider taskProvider;
    private final int numIterationsPerThread;
    private final int numThreads;

    private Benchmark(Builder builder) {
        Objects.requireNonNull(builder.taskProvider, "taskProvider");
/*
        Preconditions.checkArgument(builder.numIterationsPerThread > 0, "numIterationsPerThread; %s",
                                    builder.numIterationsPerThread);
        Preconditions.checkArgument(builder.numThreads > 0, "numThreads; %s",
                                    builder.numThreads);
*/
        taskProvider = builder.taskProvider;
        numIterationsPerThread = builder.numIterationsPerThread;
        numThreads = builder.numThreads;
    }

    public long run() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(numThreads);
        List<Callable<Long>> clients = new ArrayList<>(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            final Task task = taskProvider.get();
            clients.add(new Callable<Long>() {

                @Override
                public Long call() throws Exception {
                    return task.run(barrier, numIterationsPerThread);
                }
            });
        }
        long maxNanosPerClient = 0;
        for (Future<Long> result : Executors.newFixedThreadPool(numThreads).invokeAll(clients)) {
            maxNanosPerClient = Math.max(maxNanosPerClient, result.get());
        }
        return TimeUnit.SECONDS.toNanos(1) * numThreads * numIterationsPerThread / maxNanosPerClient;
    }

    public static class Builder {

        private TaskProvider taskProvider;
        private int numIterationsPerThread = 1000;
        private int numThreads = 1;

        public Builder setNumThreads(int numThreads) {
            this.numThreads = numThreads;
            return this;
        }

        public Builder setNumIterationsPerThread(int numIterationsPerThread) {
            this.numIterationsPerThread = numIterationsPerThread;
            return this;
        }

        public Builder setTaskClass(final Class<? extends Task> taskClass) {
            return setTaskProvider(new TaskProvider(taskClass));
        }

        public Builder setTaskProvider(TaskProvider taskProvider) {
            this.taskProvider = taskProvider;
            return this;
        }

        public Benchmark build() {
            return new Benchmark(this);
        }
    }

}
