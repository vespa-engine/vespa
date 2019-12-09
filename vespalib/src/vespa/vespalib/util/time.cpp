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

string
to_string(duration dur)
{
    time_t timeStamp = std::chrono::duration_cast<std::chrono::seconds>(dur).count();
    struct tm timeStruct;
    gmtime_r(&timeStamp, &timeStruct);
    char timeString[128];
    strftime(timeString, sizeof(timeString), "%F %T", &timeStruct);
    char retval[160];
    uint32_t milliSeconds = count_ms(dur)%1000;
    snprintf(retval, sizeof(retval), "%s.%03u UTC", timeString, milliSeconds);
    return std::string(retval);
}

string
to_string(system_time time) {
    return to_string(time.time_since_epoch());
}

string
to_string(steady_time time) {
    return to_string(time.time_since_epoch());
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
