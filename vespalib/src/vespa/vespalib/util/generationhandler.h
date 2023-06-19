// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <atomic>

namespace vespalib {

/**
 * Class used to keep track of the current generation of a component
 * (changed by a single writer), and previous generations still
 * occupied by multiple readers.  Readers will take a generation guard
 * by calling takeGuard().
 **/
class GenerationHandler {
public:
    using generation_t = uint64_t;
    using sgeneration_t = int64_t ;

    /*
     * This must be type stable memory, and cannot be freed before the
     * GenerationHandler is freed (i.e. when external methods ensure that
     * no readers are still active).
     */
    class GenerationHold
    {
        // least significant bit is invalid flag
        std::atomic<uint32_t> _refCount;

        static bool valid(uint32_t refCount) noexcept { return (refCount & 1) == 0u; }
    public:
        std::atomic<generation_t> _generation;
        GenerationHold *_next;	// next free element or next newer element.

        GenerationHold() noexcept;
        ~GenerationHold();

        void setValid() noexcept;
        bool setInvalid() noexcept;
        void release() noexcept {
            _refCount.fetch_sub(2, std::memory_order_release);
        }
        GenerationHold *acquire() noexcept;
        static GenerationHold *copy(GenerationHold *self) noexcept;
        uint32_t getRefCount() const noexcept {
            return _refCount.load(std::memory_order_relaxed) / 2;
        }
        uint32_t getRefCountAcqRel() noexcept {
            return _refCount.fetch_add(0, std::memory_order_acq_rel) / 2;
        }
    };

    /**
     * Class that keeps a reference to a generation until destroyed.
     **/
    class Guard {
    private:
        GenerationHold *_hold;
        void cleanup() noexcept {
            if (_hold != nullptr) {
                _hold->release();
                _hold = nullptr;
            }
        }
    public:
        Guard() noexcept : _hold(nullptr) { }
        Guard(GenerationHold *hold) noexcept : _hold(hold->acquire()) { } // hold is never nullptr
        ~Guard() { cleanup(); }
        Guard(const Guard & rhs) noexcept : _hold(GenerationHold::copy(rhs._hold)) { }
        Guard(Guard &&rhs) noexcept
            : _hold(rhs._hold)
        {
            rhs._hold = nullptr;
        }
        Guard & operator=(const Guard & rhs) noexcept;
        Guard & operator=(Guard &&rhs) noexcept;

        bool valid() const noexcept {
            return _hold != nullptr;
        }
        generation_t getGeneration() const { return _hold->_generation.load(std::memory_order_relaxed); }
    };

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
    Guard takeGuard() const;

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

