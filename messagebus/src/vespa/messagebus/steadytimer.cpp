// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "steadytimer.h"
#include <vespa/vespalib/util/time.h>

using namespace std::chrono;

namespace mbus {

uint64_t
SteadyTimer::getMilliTime() const
{
    return vespalib::count_ms(steady_clock::now().time_since_epoch());
}

} // namespace mbus
