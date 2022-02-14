// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Interface for tracking the start and end of jobs.
 */
struct IJobTracker
{
    virtual ~IJobTracker() = default;

    virtual void start() = 0;
    virtual void end() = 0;
};

} // namespace proton

