// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/gate.h>
#include <thread>

namespace vespalib {

/**
 * A TimeBomb is used to protect against deadlocked unit tests. A
 * TimeBomb is constructed with a given number of seconds. If the
 * TimeBomb is not destructed before the time runs out, it will abort
 * the program. The recommended way to use this class is as a fixture
 * for multi-threaded tests where the test may hang if something is
 * wrong.
 **/
class TimeBomb
{
private:
    Gate _gate;
    std::thread _thread;
public:
    TimeBomb(size_t seconds) : TimeBomb(from_s(seconds)) {}
    TimeBomb(vespalib::duration duration);
    ~TimeBomb(); // defuse the bomb
};

} // namespace vespalib
