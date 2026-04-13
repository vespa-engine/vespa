// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

class ANNDoom {
public:
    ANNDoom(const std::atomic<steady_time> & now, steady_time doom, bool is_timeout) noexcept
        : _now(now), _doom(doom), _is_timeout(is_timeout) {
    }
    duration ann_left() const noexcept { return _doom - get_time_ns(); }
    bool ann_doom() const noexcept { return (get_time_ns() > _doom); }
    bool is_timeout() const noexcept { return _is_timeout; }
    static const ANNDoom& never() noexcept;
private:
    vespalib::steady_time get_time_ns() const noexcept {
        return vespalib::steady_time(_now.load(std::memory_order_relaxed));
    }
    const std::atomic<steady_time> & _now;
    steady_time                      _doom;
    bool                             _is_timeout;
};

class Doom {
public:
    Doom(const std::atomic<steady_time> & now_ref, steady_time doom) noexcept
        : Doom(now_ref, doom, doom, false)
    {}
    Doom(const std::atomic<steady_time> & now_ref, steady_time softDoom,
         steady_time hardDoom, bool explicitSoftDoom) noexcept;
    Doom(const std::atomic<steady_time> & now_ref,
         vespalib::duration ann_timebudget, bool ann_timeout_enabled, steady_time ann_timeout,
         steady_time softDoom, steady_time hardDoom, bool explicitSoftDoom) noexcept;

    const ANNDoom make_ann_doom(uint32_t timeout_divisor) const noexcept;

    bool soft_doom() const noexcept { return (getTimeNS() > _softDoom); }
    bool hard_doom() const noexcept { return (getTimeNS() > _hardDoom); }
    duration soft_left() const noexcept { return _softDoom - getTimeNS(); }
    duration hard_left() const noexcept { return _hardDoom - getTimeNS(); }
    bool isExplicitSoftDoom() const noexcept { return _isExplicitSoftDoom; }
    static const Doom & never() noexcept;
private:
    vespalib::steady_time getTimeNS() const noexcept {
        return vespalib::steady_time(_now.load(std::memory_order_relaxed));
    }
    const std::atomic<steady_time> & _now;
    vespalib::duration               _ann_timebudget;
    bool                             _ann_timeout_enabled;
    steady_time                      _ann_timeout;
    steady_time                      _softDoom;
    steady_time                      _hardDoom;
    bool                             _isExplicitSoftDoom;
};

}
