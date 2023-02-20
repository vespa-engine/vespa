// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fakeclock.h"

namespace storage::framework::defaultimplementation {

FakeClock::FakeClock(Mode m, vespalib::duration startTime)
    : _mode(m),
      _absoluteTime(startTime),
      _cycleCount(0)
{
}

int64_t
FakeClock::getTimeInMicros() const {
    std::lock_guard guard(_lock);
    if (_mode == FAKE_ABSOLUTE) return vespalib::count_us(_absoluteTime);
    vespalib::duration tmp(_absoluteTime);
    tmp += std::chrono::seconds(_cycleCount++);
    return vespalib::count_us(tmp);
}

}
