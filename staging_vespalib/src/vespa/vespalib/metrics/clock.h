// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>

namespace vespalib {

using InternalClock = std::chrono::steady_clock;
using InternalTimeStamp = std::chrono::time_point<std::chrono::steady_clock,
                                                  std::chrono::microseconds>;
using WallClock = std::chrono::system_clock;
using WallTimeStamp = std::chrono::time_point<std::chrono::system_clock,
                                              std::chrono::microseconds>;

inline InternalTimeStamp now_stamp()
{
    using namespace std::chrono;
    return time_point_cast<microseconds>(steady_clock::now());
}

std::chrono::microseconds since_epoch(InternalTimeStamp stamp);

} // namespace vespalib
