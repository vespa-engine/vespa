// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"
#include "fake_doom.h"

namespace vespalib {

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
      _ann_doom(softDoom),
      _ann_timebudget(ann_timebudget),
      _ann_timeout_enabled(ann_timeout_enabled),
      _ann_timeout(ann_timeout),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

void Doom::arm_ann_doom(uint32_t num_ann_searches) const noexcept {
    vespalib::steady_time now(_now.load(std::memory_order_relaxed));
    vespalib::duration ann_duration = _ann_timebudget;
    if (_ann_timeout_enabled) {
        ann_duration = std::min(ann_duration, (_ann_timeout - now) / num_ann_searches);
    }
    _ann_doom = now + ann_duration;
}

const Doom &
Doom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_doom();
}

}
