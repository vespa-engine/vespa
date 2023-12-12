// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "clock.h"

namespace vespalib {

class Doom {
public:
    Doom(const Clock &clock, steady_time doom) noexcept
        : Doom(clock, doom, doom, false)
    {}
    Doom(const Clock &clock, steady_time softDoom,
         steady_time hardDoom, bool explicitSoftDoom) noexcept;

    bool soft_doom() const noexcept { return (_clock.getTimeNS() > _softDoom); }
    bool hard_doom() const noexcept { return (_clock.getTimeNS() > _hardDoom); }
    duration soft_left() const noexcept { return _softDoom - _clock.getTimeNS(); }
    duration hard_left() const noexcept { return _hardDoom - _clock.getTimeNS(); }
    bool isExplicitSoftDoom() const noexcept { return _isExplicitSoftDoom; }
private:
    const Clock   &_clock;
    steady_time    _softDoom;
    steady_time    _hardDoom;
    bool           _isExplicitSoftDoom;
};

}
