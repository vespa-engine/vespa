// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::RealClock
 * \ingroup frameworkimpl
 *
 * \brief Implements a class for calculating current time.
 *
 * Real implementation for gathering all clock information used in application.
 */
#pragma once

#include <vespa/storageframework/generic/clock/clock.h>

namespace storage::framework::defaultimplementation {

struct RealClock : public Clock {
    MicroSecTime getTimeInMicros() const override;
    MilliSecTime getTimeInMillis() const override;
    SecondTime getTimeInSeconds() const override;
    MonotonicTimePoint getMonotonicTime() const override;
};

}
