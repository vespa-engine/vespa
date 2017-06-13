// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class vespalib::TaskScheduler
 * \ingroup util
 *
 * \brief Class to register tasks in to get them run in a separate thread.
 *
 * Imported to vdslib as C++ document API needs an independent thread to run
 * events in, as it was subject to errors to use FNET event thread. Converted
 * this class used in storage API client code before.
 */
#pragma once

#include <vespa/vespalib/util/document_runnable.h>
#include <map>
#include <memory>
#include <vector>
#include <vespa/vespalib/util/sync.h>

namespace vdslib {

class TaskScheduler : public document::Runnable
{
public:
    typedef uint64_t Time;

    struct Task {
        typedef std::unique_ptr<Task> UP;
        virtual ~Task() {}
        /**
         * Return 0 to unregister this task. Return a negative number to get a
         * new callback in that many (times -1) milliseconds. Return a positive
         * number to get a callback as soon as thread is available after that
         * absolute point in time (in milliseconds). If returning current time
         * or before, this task will be scheduled to be rerun immediately
         * (after other already waiting tasks have had a chance to run).
         * The current time for the scheduler is given to the task.
         */
        virtual int64_t run(Time) = 0;
    };

    /**
     * If you want to fake time (useful for testing), implement your own watch
     * for the scheduler to use.
     */
    struct Watch {
        virtual ~Watch() {}
        virtual Time getTime() const; // In ms since 1970
    };

    /** Creates a task scheduler. Remember to call start() to get it going. */
    TaskScheduler();
    ~TaskScheduler();

    /** Register a task for immediate execution */
    void add(Task::UP);

    /** Register a task to be run in a given number of milliseconds from now */
    void addRelative(Task::UP, Time);

    /** Register a task to be run at given absolute time in milliseconds */
    void addAbsolute(Task::UP, Time);

    /**
     * Removes a scheduled task from the scheduler. Note that this is
     * currently not efficiently implemented but an exhaustive iteration of
     * current tasks. Assuming number of tasks is small so this doesn't matter.
     * If current task is running while this is called, function will block
     * until it has completed before removing it. (To be safe if you want to
     * delete task after.)
     */
    void remove(Task*);

    /** Get the schedulers current time. */
    Time getTime() const;

    /** Set a custom watch to be used for this scheduler (Useful for testing) */
    void setWatch(const Watch& watch);

    /** Returns a number of current task */
    uint64_t getTaskCounter() const;

    /** Wait until a given number of tasks have been completed */
    void waitForTaskCounterOfAtLeast(uint64_t taskCounter,
                                     uint64_t timeout = 5000) const;

    /** Call this to wait until no tasks are scheduled (Useful for testing) */
    void waitUntilNoTasksRemaining(uint64_t timeout = 5000) const;

private:
    typedef std::vector<Task*> TaskVector;
    typedef std::map<Time, TaskVector> TaskMap;
    vespalib::Monitor _lock;
    Watch             _defaultWatch;
    const Watch*      _watch;
    TaskMap           _tasks;
    TaskVector        _currentRunningTasks;
    uint64_t          _taskCounter;

    void run() override;
    bool onStop() override;
};

} // vdslib

