// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "deadline.h"
#include "fake_deadline.h"

namespace vespalib {

Deadline::Deadline(const std::atomic<steady_time>& now, steady_time point_of_deadline, Type type) noexcept
    : _now(now), _deadline(point_of_deadline), _type(type), _missed(false) {
}

const Deadline& Deadline::never() noexcept {
    static vespalib::FakeDeadline never_missed;
    return never_missed.get_deadline();
}

}
