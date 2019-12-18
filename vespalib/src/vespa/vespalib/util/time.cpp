// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"

namespace vespalib {

system_time
to_utc(steady_time ts) {
    system_clock::time_point nowUtc = system_clock::now();
    steady_time nowSteady = steady_clock::now();
    return system_time(nowUtc.time_since_epoch() - nowSteady.time_since_epoch() + ts.time_since_epoch());
}

Timer::~Timer() = default;

}
