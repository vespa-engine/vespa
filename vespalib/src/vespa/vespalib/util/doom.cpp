// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"
#include "fake_doom.h"
#include <cassert>

namespace vespalib {

const ANNDoom& ANNDoom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_ann_doom();
}

Doom::Doom(const std::atomic<steady_time> & now_ref, steady_time softDoom,
           steady_time hardDoom, bool explicitSoftDoom) noexcept
    : Doom(now_ref,
           vespalib::duration(std::numeric_limits<uint32_t>::max()), false, softDoom,
           softDoom, hardDoom, explicitSoftDoom)
{ }

Doom::Doom(const std::atomic<steady_time> & now_ref,
           vespalib::duration ann_timebudget, bool ann_timeout_enabled, steady_time ann_timeout,
           steady_time softDoom, steady_time hardDoom, bool explicitSoftDoom) noexcept
    : _now(now_ref),
      _ann_timebudget(ann_timebudget),
      _ann_timeout_enabled(ann_timeout_enabled),
      _ann_timeout(ann_timeout),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

const ANNDoom Doom::make_ann_doom(uint32_t timeout_divisor) const noexcept {
    assert(timeout_divisor > 0);
    vespalib::steady_time now(_now.load(std::memory_order_relaxed));
    vespalib::duration timeout_left = (_ann_timeout - now) / timeout_divisor;

    if (timeout_left < _ann_timebudget) {
        return ANNDoom(_now, now + timeout_left, _ann_timeout_enabled);
    } else {
        return ANNDoom(_now, now + _ann_timebudget, false);
    }
}

const Doom &
Doom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_doom();
}

}
