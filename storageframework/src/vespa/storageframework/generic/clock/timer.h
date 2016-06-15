// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::Timer
 * \ingroup clock
 *
 * \brief Class used to measure time differences.
 *
 * This timer class is a simple class that then used as a time_t instance, it
 * will calculate time difference from some preset point of time.
 */

#pragma once

#include <vespa/storageframework/generic/clock/clock.h>

namespace storage {
namespace framework {

class MilliSecTimer {
    const Clock* _clock;
    time_t _startTime;

public:
    MilliSecTimer(const Clock& clock)
        : _clock(&clock), _startTime(getCurrentTime()) {}

    MilliSecTime getTime() const { return MilliSecTime(getCurrentTime() - _startTime); }
    operator time_t() const { return getCurrentTime() - _startTime; }

    time_t getCurrentTime() const
        { return _clock->getTimeInMillis().getTime(); }
};

} // framework
} // storage

