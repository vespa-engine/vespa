// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace mbus {

/**
 * This interface wraps access to some timer that can be used to measure elapsed
 * time, in milliseconds. This abstraction allows for unit testing the behavior
 * of time-based constructs.
 */
class ITimer {
public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<ITimer>;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~ITimer() { /* empty */ }

    /**
     * Returns the current value of some arbitrary timer, in milliseconds. This
     * method can only be used to measure elapsed time and is not related to any
     * other notion of system or wall-clock time.
     *
     * @return The current value of the timer, in milliseconds.
     */
    virtual uint64_t getMilliTime() const = 0;
};

} // namespace mbus

