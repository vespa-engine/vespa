// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


class Scheduler {
    private static final int TICK  = 100;
    private static final int SLOTS = 512;
    private static final int MASK  = 511;
    private static final int SHIFT = 9;

    private Task[] slots    = new Task[SLOTS + 1];
    private int[]  counts   = new int[SLOTS + 1];
    private Queue  queue    = new Queue(TICK);
    private int    currIter = 0;
    private int    currSlot = 0;
    private long   nextTick;

    private static boolean isActive(Task task) {
        return (task.next() != null);
    }

    private void linkIn(Task task) {
        Task head = slots[task.slot()];
        if (head == null) {
            task.next(task);
            task.prev(task);
            slots[task.slot()] = task;
        } else {
            task.next(head);
            task.prev(head.prev());
            head.prev().next(task);
            head.prev(task);
        }
        ++counts[task.slot()];
    }

    private void linkOut(Task task) {
        Task head = slots[task.slot()];
        if (task.next() == task) {
            slots[task.slot()] = null;
        } else {
            task.prev().next(task.next());
            task.next().prev(task.prev());
            if (head == task) {
                slots[task.slot()] = task.next();
            }
        }
        task.next(null);
        task.prev(null);
        --counts[task.slot()];
    }

    public Scheduler(long now) {
        nextTick = now + TICK;
    }

    public synchronized void schedule(Task task, double seconds) {
        if (task.isKilled()) {
            return;
        }
        if (seconds < 0.0) {
            throw new IllegalArgumentException("cannot schedule a Task in the past");
        }
        int ticks = 2 + (int) Math.ceil(seconds * (1000.0 / TICK));
        if (isActive(task)) {
            linkOut(task);
        }
        task.slot((ticks + currSlot) & MASK);
        task.iter(currIter + ((ticks + currSlot) >> SHIFT));
        linkIn(task);
    }

    public synchronized void scheduleNow(Task task) {
        if (task.isKilled()) {
            return;
        }
        if (isActive(task)) {
            linkOut(task);
        }
        task.slot(SLOTS);
        task.iter(0);
        linkIn(task);
    }

    public synchronized boolean unschedule(Task task) {
        if (isActive(task)) {
            linkOut(task);
            return true;
        }
        return false;
    }

    public synchronized boolean kill(Task task) {
        task.setKilled();
        if (isActive(task)) {
            linkOut(task);
            return true;
        }
        return false;
    }

    private void queueTasks(int slot, int iter) {
        int cnt = counts[slot];
        Task task = slots[slot];
        for (int i = 0; i < cnt; i++) {
            Task next = task.next();
            if (task.iter() == iter) {
                linkOut(task);
                queue.enqueue(task);
            }
            task = next;
        }
    }

    public void checkTasks(long now) {
        if (slots[SLOTS] == null && now < nextTick) {
            return;
        }
        synchronized (this) {
            queueTasks(SLOTS, 0);
            for (int i = 0; now >= nextTick; i++, nextTick += TICK) {
                if (i < 3) {
                    if (++currSlot >= SLOTS) {
                        currSlot = 0;
                        currIter++;
                    }
                    queueTasks(currSlot, currIter);
                }
            }
        }
        while (!queue.isEmpty()) {
            Task task = (Task) queue.dequeue();
            task.perform();
        }
    }
}
