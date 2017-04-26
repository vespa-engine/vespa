// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "itimer.h"

namespace mbus {

/**
 * This is the implementation of the {@link Timer} interface that all time-based
 * constructs in message bus use by default. The only reason for replacing this
 * is for writing unit tests.
 */
class SystemTimer : public ITimer {
public:
    uint64_t getMilliTime() const override;
};

} // namespace mbus

