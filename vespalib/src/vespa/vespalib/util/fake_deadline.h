// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "deadline.h"

namespace vespalib {

/*
 * Class containing a fake deadline controlled by the time_to_deadline
 * constructor argument.
 */
class FakeDeadline {
    std::atomic<steady_time> _time;
    Deadline                 _deadline;

public:
    FakeDeadline() noexcept;
    FakeDeadline(steady_time::duration time_to_deadline) noexcept;
    FakeDeadline(steady_time::duration time_to_deadline, Deadline::Type type) noexcept;
    ~FakeDeadline();
    const Deadline& get_deadline() const noexcept { return _deadline; }
};

} // namespace vespalib
