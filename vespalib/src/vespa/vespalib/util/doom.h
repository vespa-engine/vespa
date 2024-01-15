// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

class Doom {
public:
    Doom(const std::atomic<steady_time> & now_ref, steady_time doom) noexcept
        : Doom(now_ref, doom, doom, false)
    {}
    Doom(const std::atomic<steady_time> & now_ref, steady_time softDoom,
         steady_time hardDoom, bool explicitSoftDoom) noexcept;

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
    steady_time    _softDoom;
    steady_time    _hardDoom;
    bool           _isExplicitSoftDoom;
};

}
