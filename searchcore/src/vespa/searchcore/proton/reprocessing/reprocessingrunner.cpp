// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reprocessingrunner.h"
#include "i_reprocessing_task.h"
#include <mutex>

namespace proton {

ReprocessingRunner::ReprocessingRunner()
    : _lock(),
      _tasks(),
      _state(NOT_STARTED)
{
}


void
ReprocessingRunner::addTasks(const ReprocessingTasks &tasks)
{
    std::lock_guard<std::mutex> guard(_lock);
    for (auto task : tasks) {
        _tasks.push_back(task);
    }
}


void
ReprocessingRunner::run()
{
    {
        std::lock_guard<std::mutex> guard(_lock);
        _state = RUNNING;
    }
    for (auto &task : _tasks) {
        task->run();
    }
    std::lock_guard<std::mutex> guard(_lock);
    _tasks.clear();
    _state = DONE;
    
}


void
ReprocessingRunner::reset()
{
    std::lock_guard<std::mutex> guard(_lock);
    _tasks.clear();
    _state = NOT_STARTED;
}


bool
ReprocessingRunner::empty() const
{
    std::lock_guard<std::mutex> guard(_lock);
    return _tasks.empty();
}


double
ReprocessingRunner::getProgress() const
{
    std::lock_guard<std::mutex> guard(_lock);
    switch (_state) {
    case State::NOT_STARTED:
        return 0.0;
    case State::DONE:
        return 1.0;
    default:
        ;
    }
    double weightedProgress = 0.0;
    double weight = 0.0;
    for (auto task : _tasks) {
        IReprocessingTask::Progress progress = task->getProgress();
        weightedProgress += progress._progress * progress._weight;
        weight += progress._weight;
    }
    if (weight == 0.0)
        return 1.0;
    return weightedProgress / weight;
}

} // namespace proton
