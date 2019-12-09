// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "timestamp.h"
#include <chrono>

using std::chrono::system_clock;

namespace fastos {

time_t
time() {
    return system_clock::to_time_t(system_clock::now());
}

}
