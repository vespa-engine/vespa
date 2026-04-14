// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generation_guard.h"

namespace vespalib {

/**
 * Class used to keep track of the current generation of a component
 * (changed by a single writer), and previous generations still
 * occupied by multiple readers.  Readers will take a generation guard
 * by calling takeGuard().
 **/
class GenerationHandler {
public:
    using generation_t = GenerationHold::generation_t;
    using sgeneration_t = GenerationHold::sgeneration_t;

    using Guard = GenerationGuard;

private:
    std::atomic<generation_t>     _generation;
    std::atomic<generation_t>     _oldest_used_generation;
    std::atomic<GenerationHold *> _last;      // Points to "current generation" entry
    GenerationHold               *_first;     // Points to "firstUsedGeneration" entry
    GenerationHold               *_free;      // List of free entries
    uint32_t                      _numHolds;  // Number of allocated generation hold entries

    void set_generation(generation_t generation) noexcept { _generation.store(generation, std::memory_order_relaxed); }

public:
    /**
     * Creates a new generation handler.
     **/
    GenerationHandler();
    ~GenerationHandler();

    /**
     * Take a generation guard on the current generation.
     * Should be called by reader threads.
     **/
    GenerationGuard takeGuard() const;

    /**
     * Increases the current generation by 1.
     * Should be called by the writer thread.
     **/
    void incGeneration();

    /**
     * Update the oldest used generation.
     * Should be called by the writer thread.
     */
    void update_oldest_used_generation();

    /**
     * Returns the oldest generation guarded by a reader.
     * It might be too low if writer hasn't updated oldest used generation after last reader left.
     */
    generation_t get_oldest_used_generation() const noexcept {
        return _oldest_used_generation.load(std::memory_order_relaxed);
    }

    /**
     * Returns the current generation.
     **/
    generation_t getCurrentGeneration() const noexcept {
        return _generation.load(std::memory_order_relaxed);
    }

    generation_t getNextGeneration() const noexcept {
        return getCurrentGeneration() + 1;
    }

    /**
     * Returns the number of readers holding a generation guard on the
     * given generation.  Should be called by the writer thread.
     */
    uint32_t getGenerationRefCount(generation_t gen) const;

    /**
     * Returns the number of readers holding a generation guard.
     * Should be called by the writer thread.
     */
    uint64_t getGenerationRefCount() const;
};

}

