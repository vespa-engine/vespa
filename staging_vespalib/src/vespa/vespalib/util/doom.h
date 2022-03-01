// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "clock.h"

namespace vespalib {

class Doom {
public:
    Doom(const Clock &clock, steady_time doom)
        : Doom(clock, doom, doom, false)
    {}
    Doom(const Clock &clock, steady_time softDoom,
         steady_time hardDoom, bool explicitSoftDoom);

    bool soft_doom() const { return (_clock.getTimeNS() > _softDoom); }
    bool hard_doom() const { return (_clock.getTimeNS() > _hardDoom); }
    duration soft_left() const { return _softDoom - _clock.getTimeNS(); }
    duration hard_left() const { return _hardDoom - _clock.getTimeNS(); }
    bool isExplicitSoftDoom() const { return _isExplicitSoftDoom; }
private:
    const Clock   &_clock;
    steady_time    _softDoom;
    steady_time    _hardDoom;
    bool           _isExplicitSoftDoom;
};

}
