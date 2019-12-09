// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"
#include <thread>

namespace vespalib {

system_time
to_utc(steady_time ts) {
    system_clock::time_point nowUtc = system_clock::now();
    steady_time nowSteady = steady_clock::now();
    return system_time(nowUtc.time_since_epoch() - nowSteady.time_since_epoch() + ts.time_since_epoch());
}

Timer::~Timer() = default;

void
Timer::waitAtLeast(duration dur, bool busyWait) {
    if (busyWait) {
        steady_clock::time_point deadline = steady_clock::now() + dur;
        while (steady_clock::now() < deadline) {
            for (int i = 0; i < 1000; i++) { }
        }
    } else {
        std::this_thread::sleep_for(dur);
    }
}

}
