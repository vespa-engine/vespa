// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/time.h>
#include <memory>

namespace vespalib {

namespace cpu_usage {

/**
 * Samples the total CPU usage of the thread that created it. Note
 * that this must not be used after thread termination. Enables
 * sampling the CPU usage of a thread from outside the thread.
 **/
struct ThreadSampler {
    using UP = std::unique_ptr<ThreadSampler>;
    virtual duration sample() const = 0;
    virtual ~ThreadSampler() {}
};

ThreadSampler::UP create_thread_sampler(bool force_mock_impl = false, double expected_load = 0.16);

} // cpu_usage

} // namespace
