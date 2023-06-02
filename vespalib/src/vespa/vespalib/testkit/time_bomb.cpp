// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time_bomb.h"
#include <cstdint>
#include <vespa/log/log.h>
LOG_SETUP(".vespalib.testkit.time_bomb");

namespace vespalib {

namespace {

void bomb(Gate &gate, vespalib::duration timeout) {
    if (timeout > 5s) {
        if (gate.await(timeout - 5s)) {
            return;
        }
    }
    size_t countdown = std::min(count_s(timeout), INT64_C(5));
    while (countdown > 0) {
        fprintf(stderr, "...%zu...\n", countdown--);
        if (gate.await(1s)) {
            return;
        }
    }
    fprintf(stderr, "BOOM!\n");
    LOG_ABORT("should not be reached");
}

} // namespace vespalib::<unnamed>

TimeBomb::TimeBomb(duration timeout)
    : _gate(),
      _thread(bomb, std::ref(_gate), timeout)
{
}

TimeBomb::~TimeBomb()
{
    _gate.countDown();
    _thread.join();
}

} // namespace vespalib
