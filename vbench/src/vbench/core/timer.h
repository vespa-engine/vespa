// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>

namespace vbench {

/**
 * Simple utility class used to handle low-level time sampling.
 **/
class Timer
{
private:
    using clock = std::chrono::steady_clock;
    clock::time_point _zero;
public:
    Timer();
    void reset();
    double sample() const;
};

} // namespace vbench
