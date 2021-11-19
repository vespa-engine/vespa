// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "initializer_task.h"
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <cassert>

namespace proton::initializer {

/*
 * Class to run multiple init tasks with dependent tasks.
 */
class TaskRunner {
    // Executor for the tasks, not to be confused by the context executor.
    vespalib::Executor      &_executor;     // can be multithreaded
    uint32_t                 _runningTasks; // used by context executor
    using State = InitializerTask::State;
    using TaskList = InitializerTask::List;
    using TaskSet = vespalib::hash_set<const void *>;

    class Context {
        InitializerTask::SP _rootTask;
        vespalib::Executor &_contextExecutor;      // single threaded executor
        vespalib::Executor::Task::UP _doneTask;

    public:
        using SP = std::shared_ptr<Context>;
        Context(InitializerTask::SP rootTask,
                vespalib::Executor &contextExecutor,
                vespalib::Executor::Task::UP doneTask) noexcept
            : _rootTask(rootTask),
              _contextExecutor(contextExecutor),
              _doneTask(std::move(doneTask))
        {
        }
        bool done() const { return !_doneTask; }
        void execute(vespalib::Executor::Task::UP task) {
            auto res = _contextExecutor.execute(std::move(task));
            assert(!res);
        }
        void setDone() { execute(std::move(_doneTask)); }
        const InitializerTask::SP &rootTask() { return _rootTask; }
    };
    void getReadyTasks(const InitializerTask::SP task, TaskList &readyTasks, TaskSet &checked);
    void setTaskRunning(InitializerTask &task);
    void setTaskDone(InitializerTask &task, Context::SP context);
    void internalRunTask(InitializerTask::SP task, Context::SP context);
    void internalRunTasks(const TaskList &taskList, Context::SP context);
    void pollTask(Context::SP context);
public:
    TaskRunner(vespalib::Executor &executor);

    ~TaskRunner();

    // Depecreated blocking API
    void runTask(InitializerTask::SP task);

    // Event based API, executor must be single threaded
    void runTask(InitializerTask::SP rootTask,
                 vespalib::Executor &contextExecutor,
                 vespalib::Executor::Task::UP doneTask);
};

}
