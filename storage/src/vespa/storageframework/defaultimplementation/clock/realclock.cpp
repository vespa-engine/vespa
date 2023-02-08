// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "realclock.h"
#include <sys/time.h>

namespace storage::framework::defaultimplementation {

vespalib::steady_time
RealClock::getMonotonicTime() const {
    return vespalib::steady_clock::now();
}

vespalib::system_time
RealClock::getSystemTime() const {
    return vespalib::system_clock::now();
}

}
