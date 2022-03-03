// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clock.h"
#include <cassert>
namespace vespalib {

Clock::Clock(const std::atomic<steady_time> & source) noexcept
    : _timeNS(source)
{
    static_assert(std::atomic<steady_time>::is_always_lock_free);
}

Clock::~Clock() = default;

}
