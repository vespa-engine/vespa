// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.hpp"
#include "clock.h"
#include <iomanip>
#include <vector>
#include <cassert>
#include <sstream>

namespace storage {
namespace framework {

namespace {
    void detectUnit(uint64_t& val, const char* unit, uint64_t size,
                    std::vector<std::pair<uint64_t, vespalib::string> >& units) {
        if (val / size > 0) {
            uint64_t value = val / size;
            vespalib::string unitname = unit;
            if (value != 1) unitname += "s";
            units.push_back(std::make_pair(value, unitname));
            val -= value * size;
        }
    }
}

vespalib::string
getTimeString(uint64_t microSecondTime, TimeFormat format)
{
        // Rewrite to use other type of stream later if needed for performance
    std::ostringstream ost;
    if (format & DIFFERENCE_ALL) {
        std::vector<std::pair<uint64_t, vespalib::string> > vals;
        detectUnit(microSecondTime, "day", 24 * 60 * 60 * 1000 * 1000ull, vals);
        detectUnit(microSecondTime, "hour", 60 * 60 * 1000 * 1000ull, vals);
        detectUnit(microSecondTime, "minute", 60 * 1000 * 1000, vals);
        detectUnit(microSecondTime, "second", 1000 * 1000, vals);
        if (format & DIFFERENCE_WITH_MICROS) {
            detectUnit(microSecondTime, "microsecond", 1, vals);
            if (vals.empty()) { ost << "0 microseconds"; }
        } else {
            if (vals.empty()) { ost << "0 seconds"; }
        }
        if (vals.empty()) {
            return vespalib::string(ost.str().c_str());
        }
        ost << vals[0].first << " " << vals[0].second;
        for (uint32_t i=1; i<vals.size(); ++i) {
            if (i + 1 >= vals.size()) {
                ost << " and ";
            } else {
                ost << ", ";
            }
            ost << vals[i].first << " " << vals[i].second;
        }
        return vespalib::string(ost.str().c_str());
    }
    time_t secondTime = microSecondTime / 1000000;
    struct tm datestruct;
    struct tm* datestructptr = gmtime_r(&secondTime, &datestruct);
    assert(datestructptr);
    (void) datestructptr;
    ost << std::setfill('0')
        << std::setw(4) << (datestruct.tm_year + 1900)
        << '-' << std::setw(2) << (datestruct.tm_mon + 1)
        << '-' << std::setw(2) << datestruct.tm_mday
        << ' ' << std::setw(2) << datestruct.tm_hour
        << ':' << std::setw(2) << datestruct.tm_min
        << ':' << std::setw(2) << datestruct.tm_sec;
    uint64_t micros = microSecondTime % 1000000;
    if (format == DATETIME_WITH_MILLIS) {
        ost << '.' << std::setw(3) << (micros / 1000);
    } else if (format == DATETIME_WITH_MICROS) {
        ost << '.' << std::setw(6) << micros;
    }
    return vespalib::string(ost.str().c_str());
}

uint64_t
getRawMicroTime(const Clock& clock)
{
    return clock.getTimeInMicros().getTime();
}

template std::ostream& operator<< <MicroSecTime, 1>(std::ostream&, const Time<MicroSecTime, 1> &);
template std::ostream& operator<< <MilliSecTime, 1000>(std::ostream&, const Time<MilliSecTime, 1000> &);
template std::ostream& operator<< <SecondTime, 1000000>(std::ostream&, const Time<SecondTime, 1000000> &);

template vespalib::asciistream& operator<< <MicroSecTime, 1>(vespalib::asciistream &, const Time<MicroSecTime, 1> &);
template vespalib::asciistream& operator<< <MilliSecTime, 1000>(vespalib::asciistream &, const Time<MilliSecTime, 1000> &);

} // framework
} // storage
