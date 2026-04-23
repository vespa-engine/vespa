// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_deadline.h"

namespace vespalib {

FakeDeadline::FakeDeadline() noexcept
    : FakeDeadline(1s) {
}

FakeDeadline::FakeDeadline(steady_time::duration time_to_deadline) noexcept
    : FakeDeadline(time_to_deadline, Deadline::Type::BUDGET) {
}

FakeDeadline::FakeDeadline(steady_time::duration time_to_deadline, Deadline::Type type) noexcept
    : _time(steady_clock::now()),
      _deadline(_time, _time.load(std::memory_order_relaxed) + time_to_deadline, type) {
}

FakeDeadline::~FakeDeadline() = default;

} // namespace vespalib
