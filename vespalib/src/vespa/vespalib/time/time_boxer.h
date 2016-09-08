// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>

namespace vespalib {

/**
 * Simple utility for time boxing your activity
 *
 * <pre>
 * Example:
 *
 * TimeBoxer timebox(5.0);
 * while (timebox.hasTimeLeft()) {
 *   ... do stuff
 *   ... do stuff with timeout(timebox.timeLeft())
 * }
 * </pre>
 **/
class TimeBoxer
{
private:
    using clock = std::conditional<std::chrono::high_resolution_clock::is_steady,
                                   std::chrono::high_resolution_clock,
                                   std::chrono::steady_clock>::type;

    using seconds = std::chrono::duration<double, std::ratio<1,1>>;

    template<typename DURATION>
    static inline clock::duration to_internal(DURATION x) {
        return std::chrono::duration_cast<clock::duration>(x);
    }

    template<typename DURATION>
    static inline double to_external(DURATION x) {
        return std::chrono::duration_cast<seconds>(x).count();
    }

    clock::time_point _end_time;

public:
    /**
     * Construct a TimeBoxer with a given budget from now
     * @param budget amount of time in seconds
     **/
    explicit TimeBoxer(double budget)
        : _end_time(clock::now() + to_internal(seconds(budget)))
    {
    }

    /**
     * @return true if there is time left in the budget
     **/
    bool hasTimeLeft() {
        return clock::now() < _end_time;
    }

    /**
     * @return the seconds left before the budget elapses
     **/
    double timeLeft() {
        clock::time_point now = clock::now();
        if (now < _end_time) {
            return to_external(_end_time - now);
        }
        return 0.0;
    }
};

} // namespace vespalib
