// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/clock.h>

namespace vespalib {

class Doom
{
private:
    const vespalib::Clock &_clock;
    fastos::TimeStamp      _timeOfDoom;

public:
    Doom(const vespalib::Clock &clock, fastos::TimeStamp timeOfDoom);
    bool doom() const {
        return (_clock.getTimeNSAssumeRunning() > _timeOfDoom);
    }
    fastos::TimeStamp left() const { return _timeOfDoom - _clock.getTimeNS(); }
};

} // namespace vespalib

