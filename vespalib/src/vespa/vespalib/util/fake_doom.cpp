// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fake_doom.h"

namespace vespalib {

FakeDoom::FakeDoom(steady_time::duration time_to_doom)
    : _time(steady_clock::now()),
      _clock(_time),
      _doom(_clock, _clock.getTimeNS() + time_to_doom)
{
}

FakeDoom::~FakeDoom() = default;

}
