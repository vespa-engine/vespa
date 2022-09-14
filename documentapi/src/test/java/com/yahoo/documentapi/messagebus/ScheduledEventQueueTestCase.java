// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.concurrent.ManualTimer;
import org.junit.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScheduledEventQueueTestCase {

    static class TestTask implements Runnable {
        public long timestamp = 0;

        public void run() {

        }
    }

    @Test
    public void testPushTask() {
        ScheduledEventQueue queue = new ScheduledEventQueue();
        TestTask task = new TestTask();
        queue.pushTask(task);
        assertEquals(task, queue.popTask());
    }

    @Test
    public void testPushTwoTasks() {
        ScheduledEventQueue queue = new ScheduledEventQueue();
        TestTask task1 = new TestTask();
        TestTask task2 = new TestTask();
        queue.pushTask(task1);
        queue.pushTask(task2);
        assertEquals(task1, queue.popTask());
        assertEquals(task2, queue.popTask());
    }

    @Test
    public void testNullWhenPoppingNonexistantTask() {
        ScheduledEventQueue queue = new ScheduledEventQueue();
        assertNull(queue.popTask());
    }

    @Test
    public void testPushTaskWithTime() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask task = new TestTask();
        queue.pushTask(task, 1000);
        assertNull(queue.popTask());
        timer.set(1000);
        assertEquals(task, queue.popTask());
    }

    @Test
    public void testTwoTasksWithSameTime() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask task1 = new TestTask();
        queue.pushTask(task1, 1000);
        TestTask task2 = new TestTask();
        queue.pushTask(task2, 1000);
        assertNull(queue.popTask());
        timer.set(1000);
        assertEquals(task1, queue.popTask());
        assertEquals(task2, queue.popTask());
    }

    @Test
    public void testThreeTasksWithDifferentTime() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask task1 = new TestTask();
        queue.pushTask(task1, 1000);
        TestTask task2 = new TestTask();
        queue.pushTask(task2, 500);
        TestTask task3 = new TestTask();
        queue.pushTask(task3);
        assertEquals(task3, queue.popTask());
        assertNull(queue.popTask());
        timer.set(1000);
        assertEquals(task2, queue.popTask());
        assertEquals(task1, queue.popTask());
    }

    static class ClockSetterThread implements Runnable {
        ScheduledEventQueue queue;
        ManualTimer timer;
        long newTime;

        public ClockSetterThread(ScheduledEventQueue queue, ManualTimer timer, long newTime) {
            this.queue = queue;
            this.timer = timer;
            this.newTime = newTime;
        }

        public void run() {
            try {
                while (!queue.isWaiting()) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
            }
            timer.set(newTime);
            queue.wakeTasks();
        }
    }

    @Test
    public void testPushAndWaitForTask() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask task = new TestTask();
        queue.pushTask(task, 50);
        assertNull(queue.popTask());
        new Thread(new ClockSetterThread(queue, timer, 50)).start();
        assertEquals(task, queue.getNextTask());
        assertEquals(50, timer.milliTime());
    }

    static class TaskPusherThread implements Runnable {
        ScheduledEventQueue queue;
        TestTask task;

        public TaskPusherThread(ScheduledEventQueue queue, TestTask task) {
            this.queue = queue;
            this.task = task;
        }

        public void run() {
            try {
                while (!queue.isWaiting()) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
            }
            queue.pushTask(task);
        }
    }

    @Test
    public void testPushAndWaitSingle() {
        ScheduledEventQueue queue = new ScheduledEventQueue();
        TestTask task = new TestTask();
        new Thread(new TaskPusherThread(queue, task)).start();
        assertNull(queue.popTask());
        assertEquals(task, queue.getNextTask());
    }

    @Test
    public void testPushAndWaitMultiple() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask lastTask = new TestTask();
        queue.pushTask(lastTask, 250);
        TestTask task = new TestTask();
        new Thread(new TaskPusherThread(queue, task)).start();
        assertNull(queue.popTask());
        assertEquals(task, queue.getNextTask());
        new Thread(new ClockSetterThread(queue, timer, 250)).start();
        assertEquals(lastTask, queue.getNextTask());
        assertEquals(250, timer.milliTime());
    }

    @Test
    public void testPushTaskRejectedAfterShutdown() {
        ScheduledEventQueue queue = new ScheduledEventQueue();
        TestTask task = new TestTask();
        queue.shutdown();
        assertTrue(queue.isShutdown());
        try {
            queue.pushTask(task);
            fail();
        } catch (RejectedExecutionException e) {
        }
    }

    static class ShutdownThread implements Runnable {
        ScheduledEventQueue queue;
        ManualTimer timer;

        public ShutdownThread(ScheduledEventQueue queue, ManualTimer timer) {
            this.queue = queue;
            this.timer = timer;
        }

        public void run() {
            try {
                while (!queue.isWaiting()) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
            }
            queue.shutdown();
            timer.set(100);
            queue.wakeTasks();
        }
    }

    @Test
    public void testShutdownInGetNext() {
        ManualTimer timer = new ManualTimer();
        ScheduledEventQueue queue = new ScheduledEventQueue(timer);
        TestTask task = new TestTask();
        queue.pushTask(task, 100);
        new Thread(new ShutdownThread(queue, timer)).start();
        assertEquals(task, queue.getNextTask());
        assertEquals(100, timer.milliTime());
    }

}
