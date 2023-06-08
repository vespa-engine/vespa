// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "doom.h"

namespace vespalib {

/*
 * Class containing a fake doom controlled by the time_to_doom
 * constructor argument.
 */
class FakeDoom {
    std::atomic<steady_time> _time;
    Clock                    _clock;
    Doom                     _doom;
public:
    FakeDoom() : FakeDoom(1s) { }
    FakeDoom(steady_time::duration time_to_doom);
    ~FakeDoom();
    const Doom& get_doom() const noexcept { return _doom; }
};

}
