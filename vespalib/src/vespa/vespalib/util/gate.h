// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "count_down_latch.h"

namespace vespalib {

/**
 * A gate is a countdown latch with an initial count of 1, indicating
 * that we are only waiting for a single operation to complete.
 **/
class Gate : public CountDownLatch
{
public:
    /**
     * Sets the initial count to 1.
     **/
    Gate() noexcept : CountDownLatch(1) {}
    ~Gate() override;
};

} // namespace vespalib
