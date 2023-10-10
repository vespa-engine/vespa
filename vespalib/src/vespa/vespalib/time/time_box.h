// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>

namespace vespalib {

/**
 * Simple utility for time boxing your activity
 *
 * <pre>
 * Example:
 *
 * TimeBox timebox(5.0);
 * while (timebox.hasTimeLeft()) {
 *   ... do stuff
 *   ... do stuff with timeout(timebox.timeLeft())
 * }
 * </pre>
 **/
class TimeBox
{
private:
    using clock = std::conditional<std::chrono::high_resolution_clock::is_steady,
                                   std::chrono::high_resolution_clock,
                                   std::chrono::steady_clock>::type;

    using seconds = std::chrono::duration<double, std::ratio<1,1>>;

    static inline clock::duration to_internal(double x) {
        return std::chrono::duration_cast<clock::duration>(seconds(x));
    }

    static inline double to_external(clock::duration x) {
        return std::chrono::duration_cast<seconds>(x).count();
    }

    clock::time_point _end_time;
    clock::duration _min_time;

public:
    /**
     * Construct a TimeBox with a given budget from now
     * @param budget amount of time in seconds
     **/
    explicit TimeBox(double budget)
        : _end_time(clock::now() + to_internal(budget)),
          _min_time(to_internal(0))
    {
    }

    /**
     * Construct a TimeBox with a given budget from now
     * @param budget amount of time in seconds
     * @param min_time a minimum to be returned from timeLeft()
     **/
    TimeBox(double budget, double min_time)
        : _end_time(clock::now() + to_internal(budget)),
          _min_time(to_internal(min_time))
    {
    }

    /**
     * @return true if there is time left in the budget
     **/
    bool hasTimeLeft() {
        return clock::now() < _end_time;
    }

    /**
     * Note: Never returns less than min_time even if budget is expired.
     * @return the seconds left before the budget expires
     **/
    double timeLeft() {
        clock::time_point now = clock::now();
        if (now + _min_time < _end_time) {
            return to_external(_end_time - now);
        }
        return to_external(_min_time);
    }
};

} // namespace vespalib
