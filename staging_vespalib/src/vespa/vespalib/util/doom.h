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

} // namespace vespalib

