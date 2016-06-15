// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::RealClock
 * \ingroup frameworkimpl
 *
 * \brief Implements a class for calculating current time.
 *
 * Real implementation for gathering all clock information used in application.
 */
#pragma once

#include <vespa/storageframework/storageframework.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct RealClock : public Clock {
    virtual MicroSecTime getTimeInMicros() const;
    virtual MilliSecTime getTimeInMillis() const;
    virtual SecondTime getTimeInSeconds() const;
};

} // defaultimplementation
} // framework
} // storage

