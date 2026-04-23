// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"

#include "fake_doom.h"
#include <cassert>

namespace vespalib {

Doom::Doom(const std::atomic<steady_time>& now_ref, steady_time softDoom, steady_time hardDoom,
           bool explicitSoftDoom) noexcept
    : Doom(now_ref, softDoom, hardDoom, explicitSoftDoom,
           vespalib::duration::max(), false, softDoom) {
}

Doom::Doom(const std::atomic<steady_time>& now_ref, steady_time soft_doom, steady_time hard_doom,
           bool explicit_soft_doom, vespalib::duration ann_timebudget, bool ann_timeout_enabled,
           steady_time ann_timeout) noexcept
    : _now(now_ref),
      _softDoom(soft_doom),
      _hardDoom(hard_doom),
      _isExplicitSoftDoom(explicit_soft_doom),
      _ann_timebudget(ann_timebudget),
      _ann_timeout_enabled(ann_timeout_enabled),
      _ann_timeout(ann_timeout) {
    assert(_ann_timeout <= _softDoom);
}

const Deadline Doom::make_ann_doom(uint32_t remaining_searches) const noexcept {
    assert(remaining_searches > 0);
    vespalib::steady_time now(getTimeNS());
    // ANN might doom due to a depleted time budget or a timeout,
    // whatever happens first. The timeout might be the ANN timeout
    // or the soft-timeout. The soft-timeout is used when ANN timeouts
    // are not enabled, and in this case, reaching the soft-timeout
    // is not reported as an ANN timeout.
    vespalib::duration timeout_left = _ann_timeout_enabled ? ((_ann_timeout - now) / remaining_searches)
                                                           : _softDoom - now;

    if (_ann_timebudget < timeout_left) {
        return Deadline(_now, now + _ann_timebudget, Deadline::Type::BUDGET);
    } else {
        return Deadline(_now, now + timeout_left, _ann_timeout_enabled ? Deadline::Type::TIMEOUT : Deadline::Type::BUDGET);
    }
}

const Doom& Doom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_doom();
}

} // namespace vespalib
