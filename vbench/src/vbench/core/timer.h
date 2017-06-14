// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/time.h>

namespace vbench {

/**
 * Simple utility class used to handle low-level time sampling.
 **/
class Timer
{
private:
    FastOS_Time _time;

public:
    Timer();
    void reset();
    double sample() const;
};

} // namespace vbench

