// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"

#include "fake_doom.h"
#include <cassert>

namespace vespalib {

Doom::Doom(const std::atomic<steady_time>& now_ref, steady_time soft_doom, steady_time hard_doom,
           bool explicit_soft_doom) noexcept
    : _now(now_ref),
      _softDoom(soft_doom),
      _hardDoom(hard_doom),
      _isExplicitSoftDoom(explicit_soft_doom) {
}

Deadline Doom::make_deadline(steady_time point_of_deadline, Deadline::Type type) const noexcept {
    return Deadline(_now, point_of_deadline, type);
}

const Doom& Doom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_doom();
}

} // namespace vespalib
