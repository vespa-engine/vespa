// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>

namespace proton {

/**
 * Interface class for reprocessing task, subclassed by specific
 * task.
 */
class IReprocessingTask
{
public:
    typedef std::shared_ptr<IReprocessingTask> SP;
    typedef std::unique_ptr<IReprocessingTask> UP;
    typedef std::vector<SP> List;

    struct Progress
    {
        double _progress;
        double _weight;

        Progress()
            : _progress(0.0),
              _weight(0.0)
        {}

        Progress(double progress, double weight)
            : _progress(progress),
              _weight(weight)
        {}
    };

    virtual ~IReprocessingTask() {}

    /**
     * Run reprocessing task.
     */
    virtual void run() = 0;

    virtual Progress getProgress() const = 0;
};

} // namespace proton
