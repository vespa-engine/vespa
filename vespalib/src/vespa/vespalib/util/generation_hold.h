// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <atomic>

namespace vespalib {

/*
 * GenerationHold owns the reference count for a given generation managed by a GenerationHandler.
 *
 * This must be type stable memory, and cannot be freed before the GenerationHandler is freed (i.e. when external
 * methods ensure that no readers are still active).
 *
 * Instances are managed by GenerationHandler.
 */
class GenerationHold {
    // least significant bit is invalid flag
    std::atomic<uint32_t> _refCount;

    static bool valid(uint32_t refCount) noexcept { return (refCount & 1) == 0u; }

public:
    using generation_t = uint64_t;
    using sgeneration_t = int64_t;

    std::atomic<generation_t> _generation;
    GenerationHold*           _next; // next free element or next newer element.

    GenerationHold() noexcept;
    ~GenerationHold();
    void setValid() noexcept;
    bool setInvalid() noexcept;
    void release() noexcept { _refCount.fetch_sub(2, std::memory_order_release); }
    GenerationHold* acquire() noexcept;
    static GenerationHold* copy(GenerationHold* self) noexcept;
    uint32_t getRefCount() const noexcept { return _refCount.load(std::memory_order_relaxed) / 2; }
    uint32_t getRefCountAcqRel() noexcept { return _refCount.fetch_add(0, std::memory_order_acq_rel) / 2; }
};

}
