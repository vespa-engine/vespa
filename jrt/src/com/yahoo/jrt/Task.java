// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * A Task enables a Runnable to be scheduled for execution in the
 * transport thread some time in the future. Tasks are used internally
 * to handle RPC timeouts. Use the {@link TransportThread#createTask
 * TransportThread.createTask} method to create a task associated with a
 * {@link Transport} object. Note that Task execution is designed to
 * be low-cost, so do not expect extreme accuracy. Also note that any
 * tasks that are pending execution when the owning {@link Transport}
 * object is shut down will never be run.
 **/
public class Task {
    private Scheduler owner;
    private Runnable  doit;
    private int       slot;
    private int       iter;
    private Task      next;
    private Task      prev;
    private boolean   killed;

    // methods used by the scheduler
    int     slot()         { return slot;   }
    void    slot(int val)  { slot = val;    }
    int     iter()         { return iter;   }
    void    iter(int val)  { iter = val;    }
    Task    next()         { return next;   }
    void    next(Task val) { next = val;    }
    Task    prev()         { return prev;   }
    void    prev(Task val) { prev = val;    }
    boolean isKilled()     { return killed; }
    void    setKilled()    { killed = true; }
    void    perform()      { doit.run();    }

    /**
     * Create a Task owned by the given scheduler
     *
     * @param owner the scheduler owning this task
     * @param doit what to run when the task is executed
     **/
    Task(Scheduler owner, Runnable doit) {
        this.owner = owner;
        this.doit = doit;
    }

    /**
     * Schedule this task for execution. A task may be scheduled
     * multiple times, but may only have a single pending execution
     * time. Re-scheduling a task that is not yet run will move the
     * execution time. If the task has already been executed,
     * scheduling works just like if the task was never run.
     *
     * @param seconds the number of seconds until the task should be
     *                executed
     * @see #kill
     **/
    public void schedule(double seconds) {
        owner.schedule(this, seconds);
    }

    /**
     * Schedule this task for execution as soon as possible. This will
     * result in the task being executed the next time the reactor
     * loop inside the owning {@link Transport} object checks for
     * tasks to run. If you have something that is even more urgent,
     * or something you need to be executed even if the {@link
     * Transport} is shut down, use the {@link TransportThread#perform}
     * method instead.
     * @see #kill
     **/
    public void scheduleNow() {
        owner.scheduleNow(this);
    }

    /**
     * Cancel the execution of this task.
     *
     * @return true if the task was scheduled and we managed to avoid
     *         execution
     **/
    public boolean unschedule() {
        return owner.unschedule(this);
    }

    /**
     * Cancel the execution of this task and make sure it can never be
     * scheduled for execution again. After this method is invoked,
     * invoking the {@link #schedule schedule} and {@link #scheduleNow
     * scheduleNow} methods will have no effect.
     *
     * @return true if the task was scheduled and we managed to avoid
     *         execution
     **/
    public boolean kill() {
        return owner.kill(this);
    }
}
