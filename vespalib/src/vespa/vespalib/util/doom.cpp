// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"
#include "fake_doom.h"

namespace vespalib {

Doom::Doom(const std::atomic<steady_time> & now_ref, steady_time softDoom,
           steady_time hardDoom, bool explicitSoftDoom) noexcept
    : _now(now_ref),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

const Doom &
Doom::never() noexcept {
    static vespalib::FakeDoom neverExpire;
    return neverExpire.get_doom();
}

}
