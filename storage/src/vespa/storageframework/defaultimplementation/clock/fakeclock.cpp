// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fakeclock.h"

namespace storage {
namespace framework {
namespace defaultimplementation {

FakeClock::FakeClock(Mode m, framework::MicroSecTime startTime)
    : _mode(m),
      _absoluteTime(startTime),
      _cycleCount(0)
{
}

} // defaultimplementation
} // framework
} // storage
