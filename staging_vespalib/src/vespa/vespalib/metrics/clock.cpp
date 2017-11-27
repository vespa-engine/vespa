// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "clock.h"

namespace vespalib {

std::chrono::microseconds since_epoch(InternalTimeStamp stamp)
{
    using namespace std::chrono;
    using MyInt = microseconds::rep;

    auto before = system_clock::now();
    auto now    = steady_clock::now();
    auto after  = system_clock::now();

    MyInt beforems = (time_point_cast<microseconds>(before)).time_since_epoch().count();
    MyInt nowms    = (time_point_cast<microseconds>(now)).time_since_epoch().count();
    MyInt afterms  = (time_point_cast<microseconds>(after)).time_since_epoch().count();
    
    MyInt difference = beforems - nowms;
    MyInt adjust = (afterms - beforems) / 2;

    MyInt stampms = stamp.time_since_epoch().count();

    return microseconds(stampms + difference + adjust);
}

} // namespace vespalib
