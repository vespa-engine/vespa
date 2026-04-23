// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

class Deadline {
public:
    Deadline(const std::atomic<steady_time> & now, steady_time doom, bool is_timeout) noexcept;
    [[nodiscard]] duration ann_left() const noexcept { return _doom - get_time_ns(); }
    [[nodiscard]] bool ann_doom() const noexcept { return (get_time_ns() > _doom); }
    [[nodiscard]] bool is_ann_timeout() const noexcept { return _is_ann_timeout; }
    [[nodiscard]] static const Deadline& never() noexcept;
private:
    [[nodiscard]] vespalib::steady_time get_time_ns() const noexcept {
        return vespalib::steady_time(_now.load(std::memory_order_relaxed));
    }
    const std::atomic<steady_time>& _now;
    const steady_time               _doom;
    const bool                      _is_ann_timeout;
};

}
