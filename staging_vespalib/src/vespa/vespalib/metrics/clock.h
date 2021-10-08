// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <memory>

namespace vespalib::metrics {

using TimeStamp = std::chrono::duration<double, std::ratio<1,1>>;

/**
 * Simple interface abstracting both timing and time measurement for
 * threads wanting to do stuff at regular intervals and also knowing
 * at what time stuff was done. The 'next' function blocks until the
 * next tick is due and returns the current number of seconds since
 * epoch. The parameter passed to the 'next' function should be its
 * previous return value, except the first time it is called, then 0
 * should be used. A convenience function called 'first' is added for
 * this purpose.
 **/
struct Tick {
    using UP = std::unique_ptr<Tick>;
    virtual TimeStamp next(TimeStamp prev) = 0;
    virtual TimeStamp first() = 0;
    virtual void kill() = 0;
    virtual bool alive() const = 0;
    virtual ~Tick() {}
};

} // namespace vespalib::metrics
