// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time_bomb.h"
#include <vespa/log/log.h>
LOG_SETUP(".vespalib.testkit.time_bomb");

namespace vespalib {

namespace {

void bomb(Gate &gate, size_t seconds) {    
    if (seconds > 5) {
        if (gate.await((seconds - 5) * 1000)) {
            return;
        }
    }
    size_t countdown = std::min(seconds, size_t(5));
    while (countdown > 0) {
        fprintf(stderr, "...%zu...\n", countdown--);
        if (gate.await(1000)) {
            return;
        }
    }
    fprintf(stderr, "BOOM!\n");
    LOG_ABORT("should not be reached");
}

} // namespace vespalib::<unnamed>

TimeBomb::TimeBomb(size_t seconds)
    : _gate(),
      _thread(bomb, std::ref(_gate), seconds)
{
}

TimeBomb::~TimeBomb()
{
    _gate.countDown();
    _thread.join();
}

} // namespace vespalib
