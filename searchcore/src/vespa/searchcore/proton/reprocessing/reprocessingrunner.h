// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <memory>
#include <vector>

namespace proton {

class IReprocessingTask;

/**
 * Class for running reprocessing task.
 */
class ReprocessingRunner
{
public:
    typedef std::vector<std::shared_ptr<IReprocessingTask>> ReprocessingTasks;
private:
    mutable std::mutex _lock;
    ReprocessingTasks _tasks; // Protected by _lock
    enum State
    {
        NOT_STARTED,
        RUNNING,
        DONE
    };
    State _state;
public:
    ReprocessingRunner();

    void addTasks(const ReprocessingTasks &tasks);
    void run();
    void reset();
    bool empty() const;
    double getProgress() const;
};

} // namespace proton
