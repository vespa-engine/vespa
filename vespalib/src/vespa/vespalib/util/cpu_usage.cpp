// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cpu_usage.h"
#include "require.h"

namespace vespalib {

namespace cpu_usage {

DummyThreadSampler::DummyThreadSampler()
  : _start(steady_clock::now()),
    _load(0.16)
{
}

void
DummyThreadSampler::expected_load(double load) {
    _load = load;
}

duration
DummyThreadSampler::sample() const
{
    return from_s(to_s(steady_clock::now() - _start) * _load);
}

#ifdef __linux__

ThreadSampler::ThreadSampler()
  : _my_clock()
{
    REQUIRE_EQ(pthread_getcpuclockid(pthread_self(), &_my_clock), 0);
}

duration
ThreadSampler::sample() const
{
    timespec ts;
    REQUIRE_EQ(clock_gettime(_my_clock, &ts), 0);
    return from_timespec(ts);
}

#endif

} // cpu_usage

} // namespace
