// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"

namespace vespalib {

Doom::Doom(const vespalib::Clock &clock, fastos::SteadyTimeStamp softDoom,
           fastos::SteadyTimeStamp hardDoom, bool explicitSoftDoom)
    : _clock(clock),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

}