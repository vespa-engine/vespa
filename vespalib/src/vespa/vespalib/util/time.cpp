// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"
#include <thread>

#include <vespa/log/log.h>

LOG_SETUP(".vespalib.time");
namespace vespalib {

system_time
to_utc(steady_time ts) {
    system_clock::time_point nowUtc = system_clock::now();
    steady_time nowSteady = steady_clock::now();
    return system_time(std::chrono::duration_cast<system_time::duration>(nowUtc.time_since_epoch() - nowSteady.time_since_epoch() + ts.time_since_epoch()));
}

uint32_t
getVespaTimerHz() {
    const char * vespa_timer_hz = getenv("VESPA_TIMER_HZ");
    if (vespa_timer_hz != nullptr) {
        try {
            size_t idx(0);
            uint32_t tmp = std::stoi(vespa_timer_hz, &idx, 0);
            return std::max(1u, std::min(1000u, tmp));
        } catch (const std::exception & e) {
            LOG(warning, "Parsing environment VESPA_TIMER_HZ='%s' failed with exception: %s", vespa_timer_hz, e.what());
        }
    }
    return 1000u;
}

duration
adjustTimeoutByHz(duration timeout, long hz) {
    return  (timeout * 1000) / hz;
}

duration
adjustTimeoutByDetectedHz(duration timeout) {
    return  adjustTimeoutByHz(timeout, getVespaTimerHz());
}

namespace {

string
to_string(duration dur) {
    time_t timeStamp = std::chrono::duration_cast<std::chrono::seconds>(dur).count();
    struct tm timeStruct;
    gmtime_r(&timeStamp, &timeStruct);
    char timeString[128];
    strftime(timeString, sizeof(timeString), "%F %T", &timeStruct);
    char retval[160];
    uint32_t milliSeconds = count_ms(dur) % 1000;
    snprintf(retval, sizeof(retval), "%s.%03u UTC", timeString, milliSeconds);
    return std::string(retval);
}

}

string
to_string(system_time time) {
    return to_string(time.time_since_epoch());
}

steady_time saturated_add(steady_time time, duration diff) {
    auto td = time.time_since_epoch();
    using dur_t = decltype(td);
    using val_t = dur_t::rep;
    val_t a = td.count();
    val_t b = std::chrono::duration_cast<dur_t>(diff).count();
    val_t res;
    if (__builtin_add_overflow(a, b, &res)) {
        return (b > 0) ? steady_time::max() : steady_time::min();
    }
    return steady_time(dur_t(res));
}

Timer::~Timer() = default;

void
Timer::waitAtLeast(duration dur, bool busyWait) {
    if (busyWait) {
        steady_clock::time_point deadline = steady_clock::now() + dur;
        while (steady_clock::now() < deadline) {
            for (int i = 0; i < 1000; i++) {
                std::this_thread::yield();
            }
        }
    } else {
        std::this_thread::sleep_for(dur);
    }
}

}

#if defined(_LIBCPP_VERSION) && _LIBCPP_VERSION < 160000

// Temporary workaround until libc++ supports stream operators for duration

#include <ostream>

namespace std::chrono {

ostream&
operator<<(ostream& os, const nanoseconds& value)
{
    os << value.count() << "ns";
    return os;
}

}
#endif
