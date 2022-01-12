// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cpu_usage.h"
#include "require.h"
#include <pthread.h>

namespace vespalib {

namespace cpu_usage {

namespace {

class DummyThreadSampler : public ThreadSampler {
private:
    steady_time _start;
    double _load;
public:
    DummyThreadSampler(double load) : _start(steady_clock::now()), _load(load) {}
    duration sample() const override {
        return from_s(to_s(steady_clock::now() - _start) * _load);
    }
};

#ifdef __linux__

class LinuxThreadSampler : public ThreadSampler {
private:
    clockid_t _my_clock;
public:
    LinuxThreadSampler() : _my_clock() {
        REQUIRE_EQ(pthread_getcpuclockid(pthread_self(), &_my_clock), 0);
    }
    duration sample() const override {
        timespec ts;
        REQUIRE_EQ(clock_gettime(_my_clock, &ts), 0);
        return from_timespec(ts);
    }
};

#endif

} // <unnamed>

ThreadSampler::UP create_thread_sampler(bool force_mock_impl, double expected_load) {
    if (force_mock_impl) {
        return std::make_unique<DummyThreadSampler>(expected_load);
    }
#ifdef __linux__
    return std::make_unique<LinuxThreadSampler>();
#endif
    return std::make_unique<DummyThreadSampler>(expected_load);
}

} // cpu_usage

} // namespace
