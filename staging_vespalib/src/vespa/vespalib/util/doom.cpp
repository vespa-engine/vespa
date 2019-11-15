// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"

namespace vespalib {

Doom::Doom(const vespalib::Clock &clock, fastos::SteadyTimeStamp timeOfDoom) :
    _clock(clock),
    _timeOfDoom(timeOfDoom)
{
}

} // namespace vespalib