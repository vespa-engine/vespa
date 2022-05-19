// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doom.h"

namespace vespalib {

Doom::Doom(const Clock &clock, steady_time softDoom,
           steady_time hardDoom, bool explicitSoftDoom)
    : _clock(clock),
      _softDoom(softDoom),
      _hardDoom(hardDoom),
      _isExplicitSoftDoom(explicitSoftDoom)
{ }

}