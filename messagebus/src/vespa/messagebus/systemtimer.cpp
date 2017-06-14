// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "systemtimer.h"
#include <vespa/fastos/time.h>

namespace mbus {

uint64_t
SystemTimer::getMilliTime() const
{
    FastOS_Time time;
    time.SetNow();
    return (uint64_t)time.MilliSecs();
}

} // namespace mbus
