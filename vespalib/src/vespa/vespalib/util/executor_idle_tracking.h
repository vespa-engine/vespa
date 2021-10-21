// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"

// Used by various executors to adjust the utilization number reported
// in ExecutorStats. How to use (also see unit test):
//
// (0) Remember that these classes are not thread-safe themselves
//
// (1) Keep one ThreadIdleTracker per worker thread and one
//     ExecutorIdleTracker in the executor itself. Note that the
//     ExecutorIdleTracker needs the current time as a constructor
//     parameter.
//
// (2) Each time a worker thread is blocked; call set_idle with the
//     current time right before blocking and call set_active with the
//     current time right after waking up again. Pass the result from
//     the set_active function to the was_idle function.
//
// (3) Each time stats are sampled, start by sampling the current
//     time. Then call ThreadIdleTracker::reset for (at least) all
//     blocked worker threads and pass the results to the was_idle
//     function. Then call ExecutorIdleTracker::reset with the current
//     time and the number of threads as parameters. Subtract this
//     result from the utilization of the stats to be reported.

namespace vespalib {

class ThreadIdleTracker {
private:
    steady_time _idle_tag = steady_time::min();
public:
    bool is_idle() const { return (_idle_tag != steady_time::min()); }
    void set_idle(steady_time t) {
        if (!is_idle()) {
            _idle_tag = t;
        }
    }
    duration set_active(steady_time t) {
        if (is_idle()) {
            duration how_long_idle = (t - _idle_tag);
            _idle_tag = steady_time::min();
            return how_long_idle;
        } else {
            return duration::zero();
        }
    }
    duration reset(steady_time t) {
        if (is_idle()) {
            duration how_long_idle = (t - _idle_tag);
            _idle_tag = t;
            return how_long_idle;
        } else {
            return duration::zero();
        }
    }
};

class ExecutorIdleTracker {
private:
    steady_time _start;
    duration _total_idle = duration::zero();
public:
    ExecutorIdleTracker(steady_time t) : _start(t) {}
    void was_idle(duration how_long_idle) {
        _total_idle += how_long_idle;
    }
    double reset(steady_time t, size_t num_threads) {
        double idle = count_ns(_total_idle);
        double elapsed = std::max(idle, double(count_ns((t - _start) * num_threads)));
        _start = t;
        _total_idle = duration::zero();
        return (elapsed > 0) ? (idle / elapsed) : 0.0;
    }
};

}
