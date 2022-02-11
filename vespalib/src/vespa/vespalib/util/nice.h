// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "runnable.h"

namespace vespalib {

// Wraps an init function inside another init function that adjusts
// the niceness of the thread being started. The 'how_nice' parameter
// is a value from 0.0 (not nice at all) to 1.0 (super nice). It will
// be mapped into an actual nice value in a linear fashion based on
// the nice value space that is still available.

Runnable::init_fun_t be_nice(Runnable::init_fun_t init, double how_nice);

}
