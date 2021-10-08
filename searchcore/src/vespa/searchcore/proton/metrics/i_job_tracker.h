// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton {

/**
 * Interface for tracking the start and end of jobs.
 */
struct IJobTracker
{
    typedef std::shared_ptr<IJobTracker> SP;

    virtual ~IJobTracker() {}

    virtual void start() = 0;
    virtual void end() = 0;
};

} // namespace proton

