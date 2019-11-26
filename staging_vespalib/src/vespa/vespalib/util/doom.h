// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "clock.h"

namespace vespalib {

class Doom
{
private:
    const vespalib::Clock   &_clock;
    fastos::SteadyTimeStamp  _timeOfDoom;

public:
    Doom(const vespalib::Clock &clock, fastos::SteadyTimeStamp timeOfDoom);
    bool doom() const {
        return (_clock.getTimeNSAssumeRunning() > _timeOfDoom);
    }
    fastos::TimeStamp left() const { return _timeOfDoom - _clock.getTimeNS(); }
};

class CombinedDoom {
public:
    CombinedDoom(const vespalib::Clock &clock, fastos::SteadyTimeStamp softDoom,
                 fastos::SteadyTimeStamp hardDoom, bool explicitSoftDoom)
        : _soft(clock, softDoom),
          _hard(clock, hardDoom),
          _isExplicitSoftDoom(explicitSoftDoom)
    { }
    const Doom & soft() const { return _soft; }
    const Doom & hard() const { return _hard; }
    bool isExplicitSoftDoom() const { return _isExplicitSoftDoom; }
private:
    Doom _soft;
    Doom _hard;
    bool _isExplicitSoftDoom;
};

}
