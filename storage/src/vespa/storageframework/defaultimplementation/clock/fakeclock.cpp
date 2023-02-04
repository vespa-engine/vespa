// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fakeclock.h"

namespace storage::framework::defaultimplementation {

FakeClock::FakeClock(Mode m, framework::MicroSecTime startTime)
    : _mode(m),
      _absoluteTime(startTime),
      _cycleCount(0)
{
}

framework::MicroSecTime
FakeClock::getTimeInMicros() const {
    std::lock_guard guard(_lock);
    if (_mode == FAKE_ABSOLUTE) return _absoluteTime;
    MicroSecTime tmp(_absoluteTime);
    tmp += framework::MicroSecTime(1000000 * _cycleCount++);
    return tmp;
}

}
