// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timer.h"

namespace vbench {

Timer::Timer()
    : _zero(clock::now())
{
}

void
Timer::reset()
{
    _zero = clock::now();
}

double
Timer::sample() const
{
    using seconds = std::chrono::duration<double>;
    seconds seconds_since_zero = (clock::now() - _zero);
    return seconds_since_zero.count();
}

} // namespace vbench
