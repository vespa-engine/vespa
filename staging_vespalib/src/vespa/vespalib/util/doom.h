// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "clock.h"

namespace vespalib {

class Doom {
public:
    Doom(const vespalib::Clock &clock, fastos::SteadyTimeStamp doom)
        : Doom(clock, doom, doom, false)
    {}
    Doom(const vespalib::Clock &clock, fastos::SteadyTimeStamp softDoom,
         fastos::SteadyTimeStamp hardDoom, bool explicitSoftDoom);

    bool soft_doom() const { return (_clock.getTimeNSAssumeRunning() > _softDoom); }
    bool hard_doom() const { return (_clock.getTimeNSAssumeRunning() > _hardDoom); }
    fastos::TimeStamp soft_left() const { return _softDoom - _clock.getTimeNS(); }
    fastos::TimeStamp hard_left() const { return _hardDoom - _clock.getTimeNS(); }
    bool isExplicitSoftDoom() const { return _isExplicitSoftDoom; }
private:
    const vespalib::Clock   &_clock;
    fastos::SteadyTimeStamp  _softDoom;
    fastos::SteadyTimeStamp  _hardDoom;
    bool                     _isExplicitSoftDoom;
};

}
