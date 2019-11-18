// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "systemtimer.h"
#include <chrono>

using namespace std::chrono;
namespace mbus {

uint64_t
SystemTimer::getMilliTime() const
{
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

} // namespace mbus
