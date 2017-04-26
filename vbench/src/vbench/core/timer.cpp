// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timer.h"

namespace vbench {

Timer::Timer()
    : _time()
{
    reset();
}

void
Timer::reset()
{
    _time.SetNow();
}

double
Timer::sample() const
{
    return (_time.MilliSecsToNow() / 1000.0);
}

} // namespace vbench
