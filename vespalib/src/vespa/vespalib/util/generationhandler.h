// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

        static bool valid(uint32_t refCount) { return (refCount & 1) == 0u; }
    public:
        generation_t _generation;
        GenerationHold *_next;	// next free element or next newer element.

        GenerationHold();
        ~GenerationHold();

        void setValid();
        bool setInvalid();
        void release();
        GenerationHold *acquire();
        static GenerationHold *copy(GenerationHold *self);
        uint32_t getRefCount() const;
    };

    /**
     * Class that keeps a reference to a generation until destroyed.
     **/
    class Guard {
    private:
        GenerationHold *_hold;
        void cleanup() {
            if (_hold != nullptr) {
                _hold->release();
                _hold = nullptr;
            }
        }
    public:
        Guard();
        Guard(GenerationHold *hold); // hold is never nullptr
        ~Guard();
        Guard(const Guard & rhs);
        Guard(Guard &&rhs);
        Guard & operator=(const Guard & rhs);
        Guard & operator=(Guard &&rhs);

        bool valid() const {
            return _hold != nullptr;
        }
        generation_t getGeneration() const { return _hold->_generation; }
    };

private:
    generation_t    _generation;
    generation_t    _firstUsedGeneration;
    GenerationHold *_last;      // Points to "current generation" entry
    GenerationHold *_first;     // Points to "firstUsedGeneration" entry
    GenerationHold *_free;      // List of free entries
    uint32_t        _numHolds;  // Number of allocated generation hold entries

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
     * Update first used generation.
     * Should be called by the writer thread.
     */
    void updateFirstUsedGeneration();

    /**
     * Returns the first generation guarded by a reader.  It might be too low
     * if writer hasn't updated first used generation after last reader left.
     */
    generation_t getFirstUsedGeneration() const {
        return _firstUsedGeneration;
    }

    /**
     * Returns the current generation.
     **/
    generation_t getCurrentGeneration() const {
        return _generation;
    }

    generation_t getNextGeneration() const {
        return _generation + 1;
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

    /**
     * Returns true if we still have readers.  False positives and
     * negatives are possible if readers come and go while writer
     * updates generations.
     */
    bool hasReaders() const;
};

}

