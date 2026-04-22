// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ann_doom.h"
#include "time.h"
#include <atomic>

namespace vespalib {

class Doom {
public:
    Doom(const std::atomic<steady_time>& now_ref, steady_time doom) noexcept : Doom(now_ref, doom, doom, false) {}
    Doom(const std::atomic<steady_time>& now_ref, steady_time softDoom, steady_time hardDoom,
         bool explicitSoftDoom) noexcept;
    Doom(const std::atomic<steady_time>& now_ref, steady_time soft_doom, steady_time hard_doom,
         bool explicit_soft_doom, vespalib::duration ann_timebudget, bool ann_timeout_enabled,
         steady_time ann_timeout) noexcept;

    bool soft_doom() const noexcept { return (getTimeNS() > _softDoom); }
    bool hard_doom() const noexcept { return (getTimeNS() > _hardDoom); }
    duration soft_left() const noexcept { return _softDoom - getTimeNS(); }
    duration hard_left() const noexcept { return _hardDoom - getTimeNS(); }
    bool isExplicitSoftDoom() const noexcept { return _isExplicitSoftDoom; }
    static const Doom& never() noexcept;

    const AnnDoom make_ann_doom(uint32_t remaining_searches) const noexcept;

private:
    vespalib::steady_time getTimeNS() const noexcept {
        return vespalib::steady_time(_now.load(std::memory_order_relaxed));
    }
    const std::atomic<steady_time>& _now;
    steady_time                     _softDoom;
    steady_time                     _hardDoom;
    bool                            _isExplicitSoftDoom;
    vespalib::duration              _ann_timebudget;
    bool                            _ann_timeout_enabled;
    steady_time                     _ann_timeout;
};

} // namespace vespalib
