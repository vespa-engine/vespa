// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Clock
 * \ingroup clock
 *
 * \brief Class used to attain current time.
 *
 * This class wraps how the time is retrieved. A common clock is useful in order
 * to let unit tests fake time. It is also useful to have one point for all
 * time calculations, such that one can possibly optimize if time retrieval
 * becomes a bottle neck.
 */

#pragma once

#include "time.h"
#include <memory>

namespace storage::framework {

struct Clock {
    using UP = std::unique_ptr<Clock>;

    virtual ~Clock() = default;

    virtual MicroSecTime getTimeInMicros() const = 0;
    virtual MilliSecTime getTimeInMillis() const = 0;
    virtual SecondTime getTimeInSeconds() const = 0;

    // Time point resolution is intentionally not defined here.
    virtual MonotonicTimePoint getMonotonicTime() const = 0;
};

}
