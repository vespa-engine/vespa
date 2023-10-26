// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author baldersheim
 * @since 5.2
 */
public class RcuHashMapBenchmark {
    static class Actor implements Runnable {
        private final CopyOnWriteHashMap<Long, Long> m;
        private long mSum = 0;
        private long mMissRate = 0;
        Actor(CopyOnWriteHashMap<Long, Long> m) {
            this.m = m;
        }
        @Override
        public void run() {
            final int NUM_UPDATES=100;
            final long NUM_LOOKUPS=10000000;
            final List<Long> upd = new ArrayList<Long>(NUM_UPDATES);
            upd.add(0l);
            long missRate = 0;
            long sum = 0;
            for (long i=0; i < NUM_LOOKUPS; i++) {
                long t = i%upd.size();
                Long v = m.get(upd.get((int)t));
                if (v == null) {
                    missRate++;
                    m.put(upd.get((int)t), i);
                    sum += i;
                } else {
                    sum += v;
                }
                if (i%(NUM_LOOKUPS/NUM_UPDATES) == 0) {
                    upd.add((long)upd.size());
                }
            }
            synchronized (this) {
                mSum = sum;
                mMissRate = missRate;
            }
        }
        long getSum() { synchronized (this) { return mSum; } }
        long getMissRate() { synchronized (this) { return mMissRate;} }
    }
    RcuHashMapBenchmark(int numThreads) {
        CopyOnWriteHashMap<Long, Long> m = new CopyOnWriteHashMap<Long, Long>();
        Thread[] threads = new Thread[numThreads];
        Actor [] actors = new Actor[threads.length];
        for (int i = 0; i < threads.length; ++i) {
            Actor a = new Actor(m);
            actors[i] = a;
            threads[i] = new Thread(a);
        }
        runAll(threads);
        long missRate=0;
        long sum=0;
        for (Actor a : actors) {
            missRate += a.getMissRate();
            sum += a.getSum();
            System.out.println("Missrate: " + a.getMissRate() + " sum = " + a.getSum());
        }
        System.out.println("Total Missrate: " + missRate + " sum = " + sum);
    }

    private void runAll(Thread[] threads) {
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                // nop
            }
        }
    }
    public static void main(String[] args) {
        long start, end;
        start = System.currentTimeMillis();
        new RcuHashMapBenchmark(1);
        end = System.currentTimeMillis();
        System.out.println("Elapsed during warmup: " + (end - start) + " ms.");
        for (int i=0; i < 16; i++) {
            start = System.currentTimeMillis();
            new RcuHashMapBenchmark(i+1);
            end = System.currentTimeMillis();
            System.out.println("Elapsed during " + (i+1) + " threads: " + (end - start) + " ms.");
        }

    }
}
