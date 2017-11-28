// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "clock.h"

namespace vespalib {
namespace metrics {

std::chrono::microseconds since_epoch(InternalTimeStamp stamp)
{
    using namespace std::chrono;
    return duration_cast<microseconds>(stamp.time_since_epoch());
}

} // namespace metrics
} // namespace vespalib
