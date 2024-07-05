// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * The context object is used to pass optional per operation data down to the
 * persistence layer. It contains the following:
 *
 * The load type of the operation. Users can tag their load with load types,
 * such that the backend can be configured to handle them differently. This can
 * for instance be used to:
 *   - Control what should be cached.
 *   - Keep different metrics per load type, such that users can see metrics of
 *     what they are interested in without getting them polluted with data from
 *     other types of load.
 *
 * The priority used by the service layer is given. The service layer keeps a
 * priority queue so the highest priority operations pending should be issued
 * first, but priority can also be useful in the provider, for instance for the
 * following:
 *   - Prioritize load through SPI against other load in provider.
 *   - Pause low priority load when we have high priority load running at the
 *     same time using the same resources.
 *
 * Our messagebus protocol allows tracing, which simplifies debugging. For
 * instance, if some operation is slow, one can add tracing and see where it
 * uses time, whether it has hit caches etc. As the persistence provider itself
 * can become complex, we want that also to be able to add to the trace. Thus we
 * want to give it a way to specify something that we will add to the mbus
 * trace.
 */

#pragma once

#include "read_consistency.h"
#include <vespa/vespalib/trace/trace.h>

namespace storage::spi {

using Priority = uint16_t; // 0 - max pri, 255 - min pri

// Define this type just because a ton of tests currently use it.
struct Trace {
    using TraceLevel = uint32_t;
};

class Context {
    Priority _priority;
    vespalib::Trace _trace;
    ReadConsistency _readConsistency;
public:
    Context(Context &&) noexcept = default;
    Context & operator = (Context &&) noexcept = default;
    Context(Priority pri, uint32_t maxTraceLevel) noexcept;
    ~Context();

    [[nodiscard]] Priority getPriority() const noexcept { return _priority; }

    /**
     * A read operation might choose to relax its consistency requirements,
     * allowing the persistence provider to perform optimizations on the
     * operation as a result.
     *
     * A persistence provider is not required to support relaxed consistency
     * and it might only support this on a subset of read operations, so this
     * should only be considered a hint.
     */
    void setReadConsistency(ReadConsistency consistency) noexcept {
        _readConsistency = consistency;
    }
    [[nodiscard]] ReadConsistency getReadConsistency() const noexcept {
        return _readConsistency;
    }

    [[nodiscard]] vespalib::Trace && steal_trace() noexcept { return std::move(_trace); }
    [[nodiscard]] vespalib::Trace& getTrace() noexcept { return _trace; }
    [[nodiscard]] const vespalib::Trace& getTrace() const noexcept { return _trace; }

    [[nodiscard]] bool shouldTrace(uint32_t level) const noexcept { return _trace.shouldTrace(level); }
    void trace(uint32_t level, std::string_view msg, bool addTime = true) {
        _trace.trace(level, msg, addTime);
    }
};

}
