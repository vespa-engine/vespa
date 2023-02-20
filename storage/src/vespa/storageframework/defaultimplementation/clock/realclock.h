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
    vespalib::steady_time getMonotonicTime() const override;
    vespalib::system_time getSystemTime() const override;
};

}
