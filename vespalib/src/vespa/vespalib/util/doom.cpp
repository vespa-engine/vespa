// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"
#include "fake_doom.h"

namespace vespalib {

namespace {
    vespalib::FakeDoom practicallyNeverExpire(std::chrono::hours(24*365*100)); // doom in 100 years
}

Doom::Doom(const Clock &clock, steady_time softDoom,
           steady_time hardDoom, bool explicitSoftDoom) noexcept
    : _clock(clock),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

const Doom & Doom::armageddon() noexcept { return practicallyNeverExpire.get_doom(); }

}
