// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "realclock.h"
#include <sys/time.h>

namespace storage::framework::defaultimplementation {

MicroSecTime RealClock::getTimeInMicros() const {
    struct timeval mytime;
    gettimeofday(&mytime, 0);
    return MicroSecTime(mytime.tv_sec * 1000000llu + mytime.tv_usec);
}

MilliSecTime RealClock::getTimeInMillis() const {
    struct timeval mytime;
    gettimeofday(&mytime, 0);
    return MilliSecTime(
            mytime.tv_sec * 1000llu + mytime.tv_usec / 1000);
}

SecondTime RealClock::getTimeInSeconds() const {
    struct timeval mytime;
    gettimeofday(&mytime, 0);
    return SecondTime(mytime.tv_sec);
}

MonotonicTimePoint RealClock::getMonotonicTime() const {
    return std::chrono::steady_clock::now();
}

}
