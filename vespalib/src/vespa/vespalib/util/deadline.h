// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

/**
 * Simple class based on the Doom class modeling a generic deadline.
 */
class Deadline {
public:
    /**
     * The type of this deadline. Used to indicate whether missing this deadline should be treated
     * as a failure (TIMEOUT) or not (BUDGET).
     */
    enum Type { BUDGET, TIMEOUT };

    Deadline(const std::atomic<steady_time>& now, steady_time point_of_deadline, Type type) noexcept;
    /**
     * Returns the type of this deadline.
     */
    [[nodiscard]] Type type() const noexcept { return _type; }
    /**
     * Returns how much time is left until the deadline is reached.
     */
    [[nodiscard]] duration time_left() const noexcept { return _deadline - get_time_ns(); }
    /**
     * Returns whether the deadline is missed, i.e., whether the current time is after the deadline's time point.
     */
    [[nodiscard]] bool is_missed() const noexcept { _missed = _missed || (get_time_ns() > _deadline); return _missed; }
    /**
     * Returns whether the deadline was missed, i.e., is_missed() was called after the deadline.
     */
    [[nodiscard]] bool was_missed() const noexcept { return _missed; }

    /**
     * Returns a Deadline object that is never missed.
     */
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
