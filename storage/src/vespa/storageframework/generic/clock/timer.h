// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Timer
 * \ingroup clock
 *
 * \brief Class used to measure time differences.
 */

#pragma once

#include "clock.h"

namespace storage::framework {

class MilliSecTimer {
    const Clock* _clock;
    vespalib::steady_time _startTime;

public:
    explicit MilliSecTimer(const Clock& clock)
        : _clock(&clock), _startTime(_clock->getMonotonicTime()) {}

    // Copy construction makes the most sense when creating a timer that is
    // intended to inherit another timer's start time point, without incurring
    // the cost of an initial clock sampling.
    MilliSecTimer(const MilliSecTimer&) = default;
    MilliSecTimer& operator=(const MilliSecTimer&) = default;

    [[nodiscard]] vespalib::duration getElapsedTime() const {
        return _clock->getMonotonicTime() - _startTime;
    }

    [[nodiscard]] double getElapsedTimeAsDouble() const {
        using ToDuration = std::chrono::duration<double, std::milli>;
        return std::chrono::duration_cast<ToDuration>(getElapsedTime()).count();
    }
};

}
