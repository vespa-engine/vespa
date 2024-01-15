// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_doom.h"

namespace vespalib {

FakeDoom::FakeDoom(steady_time::duration time_to_doom) noexcept
    : _time(steady_clock::now()),
      _doom(_time, _time.load(std::memory_order_relaxed) + time_to_doom)
{
}

FakeDoom::~FakeDoom() = default;

}
