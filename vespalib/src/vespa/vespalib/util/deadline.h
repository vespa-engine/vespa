// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

class Deadline {
public:
    enum Type { BUDGET, TIMEOUT };

    Deadline(const std::atomic<steady_time>& now, steady_time point_of_deadline, Type type) noexcept;
    [[nodiscard]] Type type() const noexcept { return _type; }
    [[nodiscard]] duration time_left() const noexcept { return _deadline - get_time_ns(); }
    [[nodiscard]] bool is_missed() const noexcept { _missed = _missed || (get_time_ns() > _deadline); return _missed; }
    [[nodiscard]] bool was_missed() const noexcept { return _missed; }
    [[nodiscard]] static const Deadline& never() noexcept;
private:
    [[nodiscard]] vespalib::steady_time get_time_ns() const noexcept {
        return vespalib::steady_time(_now.load(std::memory_order_relaxed));
    }
    const std::atomic<steady_time>& _now;
    steady_time                     _deadline;
    Type                            _type;
    mutable bool                    _missed;
};

}
