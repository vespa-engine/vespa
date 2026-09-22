// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"

#include <thread>

#include <vespa/log/log.h>

LOG_SETUP(".vespalib.time");
namespace vespalib {

system_time to_utc(steady_time ts) {
    system_clock::time_point nowUtc = system_clock::now();
    steady_time              nowSteady = steady_clock::now();
    return system_time(std::chrono::duration_cast<system_time::duration>(
        nowUtc.time_since_epoch() - nowSteady.time_since_epoch() + ts.time_since_epoch()));
}

uint32_t getVespaTimerHz() {
    const char* vespa_timer_hz = getenv("VESPA_TIMER_HZ");
    if (vespa_timer_hz != nullptr) {
        try {
            size_t   idx(0);
            uint32_t tmp = std::stoi(vespa_timer_hz, &idx, 0);
            return std::max(1u, std::min(1000u, tmp));
        } catch (const std::exception& e) {
            LOG(warning, "Parsing environment VESPA_TIMER_HZ='%s' failed with exception: %s", vespa_timer_hz,
                e.what());
        }
    }
    return 1000u;
}

duration adjustTimeoutByHz(duration timeout, long hz) {
    return (timeout * 1000) / hz;
}

duration adjustTimeoutByDetectedHz(duration timeout) {
    return adjustTimeoutByHz(timeout, getVespaTimerHz());
}

namespace {

std::string to_string(duration dur) {
    time_t    timeStamp = std::chrono::duration_cast<std::chrono::seconds>(dur).count();
    struct tm timeStruct;
    toGmtDateAndTime(timeStamp, timeStruct);
    char timeString[128];
    strftime(timeString, sizeof(timeString), "%F %T", &timeStruct);
    char     retval[160];
    uint32_t milliSeconds = count_ms(dur) % 1000;
    snprintf(retval, sizeof(retval), "%s.%03u UTC", timeString, milliSeconds);
    return std::string(retval);
}

} // namespace

std::string to_string(system_time time) {
    return to_string(time.time_since_epoch());
}

std::string to_string(file_time time) {
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

void Timer::waitAtLeast(duration dur, bool busyWait) {
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

// Helper to determine if it's a leap year
static inline bool is_leap_year(int year) {
    return (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0));
}

void toGmtDateAndTime(time_t t, struct tm& target) {
    // 1. Time of day
    int64_t days = t / 86400;
    int64_t rem = t % 86400;
    if (rem < 0) {
        rem += 86400;
        days--;
    }

    target.tm_hour = (int)(rem / 3600);
    rem %= 3600;
    target.tm_min = (int)(rem / 60);
    target.tm_sec = (int)(rem % 60);

    // 2. Day of the week calculation (1970-01-01 was a Thursday = 4)
    int wday = (int)((days + 4) % 7);
    if (wday < 0) wday += 7;
    target.tm_wday = wday;

    // 3. Date extraction (musl algorithm)
    int64_t qc_cycles = (days - 11017) / 146097;
    int64_t remdays = (days - 11017) % 146097;
    if (remdays < 0) {
        remdays += 146097;
        qc_cycles--;
    }

    int64_t c_cycles = remdays / 36524;
    if (c_cycles == 4) c_cycles = 3;
    remdays -= c_cycles * 36524;

    int64_t q_cycles = remdays / 1461;
    remdays %= 1461;

    int64_t y_years = remdays / 365;
    if (y_years == 4) y_years = 3;
    remdays -= y_years * 365;

    int64_t year = 2000 + qc_cycles * 400 + c_cycles * 100 + q_cycles * 4 + y_years;
    int64_t month = (remdays * 5 + 2) / 153;
    int64_t day = remdays - (month * 153 + 2) / 5 + 1;

    month += 3;
    if (month > 12) {
        month -= 12;
        year++;
    }

    target.tm_year = (int)year - 1900; // struct tm uses 1900 as base year
    target.tm_mon = (int)month - 1; // struct tm uses 0-11 for months
    target.tm_mday = (int)day;

    // 4. Day of the year calculation [0-365]
    // Non-leap year cumulative days preceding the start of each month (1-indexed mapping)
    static const int32_t days_before_month[] = {
        0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334
    };

    int yday = days_before_month[month] + day - 1;
    if (month > 2 && is_leap_year(year)) {
        yday++;
    }
    target.tm_yday = yday;
    target.tm_isdst = 0;
    target.tm_gmtoff = 0;
    // note: tm_zone is 'char *' (not 'const char *') on BSD/macOS
    static char gmt_name[] = "GMT";
    target.tm_zone = gmt_name;
}

} // namespace vespalib
