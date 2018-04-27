// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import org.junit.After;
import org.junit.Before;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SchedulerTest {

    long      now;       // fake time
    Scheduler scheduler;

    private class MyTask implements Runnable {
        private Task task;
        private long target;
        private long actual = 0;
        private boolean done = false;
        private boolean multiple = false;

        public MyTask(long target) {
            task = new Task(scheduler, this);
            this.target = target;
        }

        public void schedule() {
            task.schedule(target / 1000.0);
        }

        public boolean unschedule() {
            return task.unschedule();
        }

        public boolean kill() {
            return task.kill();
        }

        public boolean done() {
            return done;
        }

        public boolean check() {
            if (!done || multiple) {
                return false;
            }
            if (actual < target) {
                return false;
            }
            //     2 * Scheduler.TICK == 200
            return ((actual - target) <= 200);
        }

        public void run() {
            multiple = done;
            done = true;
            actual = now;
        }
    }

    private class RTTask implements Runnable {
        private Task task;
        private int cnt = 0;

        public RTTask() {
            task = new Task(scheduler, this);
        }

        public Task task() {
            return task;
        }

        public void schedule() {
            task.scheduleNow();
        }

        public boolean unschedule() {
            return task.unschedule();
        }

        public boolean kill() {
            return task.kill();
        }

        public int cnt() {
            return cnt;
        }

        public void run() {
            cnt++;
            task.scheduleNow();
        }
    }

    @Before
    public void setUp() {
        now = 0;
        scheduler = new Scheduler(now);
    }

    @After
    public void tearDown() {
        scheduler = null;
    }

    @org.junit.Test
    public void testTimeliness() {
        Random rand = new Random(73201242);

        RTTask rt1 = new RTTask();
        RTTask rt2 = new RTTask();
        RTTask rt3 = new RTTask();
        rt1.schedule();
        rt2.schedule();
        rt3.schedule();

        MyTask[] tasks = new MyTask[250000];
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = new MyTask(rand.nextInt(131072));
            tasks[i].schedule();
        }
        int iterations = 0;
        while (now < 135000) {
            now += 10;
            scheduler.checkTasks(now);
            iterations++;
        }
        assertEquals(iterations, rt1.cnt());
        assertEquals(iterations, rt2.cnt());
        assertEquals(iterations, rt3.cnt());
        for (int i = 0; i < tasks.length; i++) {
            assertTrue(tasks[i].check());
        }
    }

    @org.junit.Test
    public void testUnschedule() {
        MyTask t1 = new MyTask(1000);
        MyTask t2 = new MyTask(1000);
        MyTask t3 = new MyTask(1000);
        MyTask t4 = new MyTask(1000);
        MyTask t5 = new MyTask(1000);

        RTTask rt1 = new RTTask();
        RTTask rt2 = new RTTask();
        RTTask rt3 = new RTTask();
        RTTask rt4 = new RTTask();
        RTTask rt5 = new RTTask();

        assertFalse(t4.kill());

        t1.schedule();
        t2.schedule();
        t3.schedule();
        t4.schedule();
        t5.schedule();

        assertFalse(rt4.kill());

        rt1.schedule();
        rt2.schedule();
        rt3.schedule();
        rt4.schedule();
        rt5.schedule();

        assertTrue(t2.unschedule());
        assertTrue(t1.unschedule());
        assertTrue(t5.unschedule());

        assertFalse(t2.unschedule());
        assertFalse(t1.unschedule());
        assertFalse(t5.unschedule());

        t2.schedule();
        t1.schedule();
        assertTrue(t2.kill());
        t2.schedule();
        assertFalse(t2.kill());

        int cnt = 0;
        while (now < 5000) {
            scheduler.checkTasks(now);
            now += 10;
            cnt++;
        }
        int old_cnt = cnt;
        assertTrue(rt1.kill());
        assertTrue(rt3.unschedule());
        assertTrue(rt2.unschedule());
        rt1.schedule();
        rt2.schedule();
        while (now < 10000) {
            scheduler.checkTasks(now);
            now += 10;
            cnt++;
        }

        assertTrue(t1.check());
        assertFalse(t2.done());
        assertTrue(t3.check());
        assertFalse(t4.done());
        assertFalse(t5.done());

        assertEquals(old_cnt, rt1.cnt());
        assertEquals(cnt, rt2.cnt());
        assertEquals(old_cnt, rt3.cnt());
        assertEquals(0, rt4.cnt());
        assertEquals(cnt, rt5.cnt());
    }

    @org.junit.Test
    public void testSlowEventLoop() {
        scheduler.checkTasks(now);
        now += 10000;
        MyTask task1 = new MyTask(5000);
        task1.schedule();
        int cnt1 = 0;
        while (true) {
            scheduler.checkTasks(now);
            if (task1.done()) {
                break;
            }
            cnt1++;
            now += 10;
        }
        assertTrue(cnt1 > 400 && cnt1 < 500);

        scheduler.checkTasks(now);
        now += 10000;
        MyTask task2 = new MyTask(5000);
        task2.schedule();
        int cnt2 = 0;
        while (true) {
            scheduler.checkTasks(now);
            if (task2.done()) {
                break;
            }
            cnt2++;
            now += 10000;
        }
        assertTrue(cnt2 > 10 && cnt2 < 30);
    }

}
