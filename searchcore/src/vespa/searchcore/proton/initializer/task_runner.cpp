// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "task_runner.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <future>

using vespalib::makeLambdaTask;

namespace proton::initializer {

namespace {
    VESPA_THREAD_STACK_TAG(task_runner)
}

TaskRunner::TaskRunner(vespalib::Executor &executor)
    : _executor(executor),
      _runningTasks(0u)
{
}

TaskRunner::~TaskRunner()
{
    assert(_runningTasks == 0u);
}

void
TaskRunner::getReadyTasks(const InitializerTask::SP task, TaskList &readyTasks, TaskSet &checked)
{
    if (task->getState() != State::BLOCKED) {
        return; // task running or done, all dependencies done
    }
    if (!checked.insert(task.get()).second) {
        return; // task already checked from another depender
    }
    const TaskList &deps = task->getDependencies();
    bool ready = true;
    for (const auto &dep : deps) {
        switch (dep->getState()) {
        case State::RUNNING:
            ready = false;
            break;
        case State::DONE:
            break;
        case State::BLOCKED:
            ready = false;
            getReadyTasks(dep, readyTasks, checked);
        }
    }
    if (ready) {
        readyTasks.push_back(task);
    }
}

void
TaskRunner::setTaskRunning(InitializerTask &task)
{
    // run by context executor
    task.setRunning();
    ++_runningTasks;
}

void
TaskRunner::setTaskDone(InitializerTask &task, Context::SP context)
{
    // run by context executor
    task.setDone();
    --_runningTasks;
    pollTask(context);
}

void
TaskRunner::internalRunTask(InitializerTask::SP task, Context::SP context)
{
    // run by context executor
    assert(task->getState() == State::BLOCKED);
    setTaskRunning(*task);
    auto done(makeLambdaTask([this, task, context]() { setTaskDone(*task, context); }));
    _executor.execute(makeLambdaTask([task, context, done(std::move(done))]() mutable
                                     {   task->run();
                                         context->execute(std::move(done)); }));
}

void
TaskRunner::internalRunTasks(const TaskList &taskList, Context::SP context)
{
    // run by context executor
    for (auto &task : taskList) {
        internalRunTask(task, context);
    }
}

void
TaskRunner::runTask(InitializerTask::SP task)
{
    vespalib::ThreadStackExecutor executor(1, 128_Ki, task_runner);
    std::promise<void> promise;
    auto future = promise.get_future();
    runTask(task, executor, makeLambdaTask([&]() { promise.set_value(); }));
    future.wait();
}

void
TaskRunner::pollTask(Context::SP context)
{
    // run by context executor
    if (context->done()) {
        return;
    }
    if (context->rootTask()->getState() == State::DONE) {
        context->setDone();
        return;
    }
    TaskList readyTasks;
    TaskSet checked;
    getReadyTasks(context->rootTask(), readyTasks, checked);
    std::sort(readyTasks.begin(), readyTasks.end(), [](const auto &a, const auto &b) -> bool { return a->get_transient_memory_usage() > b->get_transient_memory_usage(); });
    internalRunTasks(readyTasks, context);
}

void
TaskRunner::runTask(InitializerTask::SP rootTask,
                    vespalib::Executor &contextExecutor,
                    vespalib::Executor::Task::UP doneTask)
{
    auto context(std::make_shared<Context>(rootTask, contextExecutor, std::move(doneTask)));
    context->execute(makeLambdaTask([this, context=std::move(context)]() { pollTask(context); } ));
}

}
