// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <pthread.h>
#include <vespa/vespalib/util/time.h>

namespace vespalib {

namespace cpu_usage {

// do not use this directly (use ThreadSampler)
class DummyThreadSampler {
private:
    steady_time _start;
    double _load;
public:
    DummyThreadSampler();
    void expected_load(double load);
    duration sample() const;
};

#ifdef __linux__

/**
 * Samples the total CPU usage of the thread that created it. Note
 * that this must not be used after thread termination. Enables
 * sampling the CPU usage of a thread from outside the thread.
 *
 * uses: pthread_self, pthread_getcpuclockid, clock_gettime
 **/
class ThreadSampler {
private:
    clockid_t _my_clock;
public:
    ThreadSampler();
    constexpr void expected_load(double) noexcept {}
    duration sample() const;
};

#else

using ThreadSampler = DummyThreadSampler;

#endif

} // cpu_usage

} // namespace
