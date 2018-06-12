// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

namespace search::common {

/**
 * Interface for a component that has a lid space that can be compacted and shrunk.
 */     
struct ICompactableLidSpace {
    virtual ~ICompactableLidSpace() {}

    /**
     * Compacts the lid space down to the wanted given doc id limit.
     * After this, the remaining lid space is a candidate for shrinking (freeing of memory resources).
     */  
    virtual void compactLidSpace(uint32_t wantedDocLidLimit) = 0;

    /**
     * Returns whether this lid space can be shrunk down to the wanted doc id limit.
     */ 
    virtual bool canShrinkLidSpace() const = 0;

    /*
     * Returns how much memory (in bytes) that can be saved by shrinking lid space.
     */
    virtual size_t getEstimatedShrinkLidSpaceGain() const = 0;

    /**
     * Shrinks this lid space down to the wanted doc id limit (frees memory resources).
     */
    virtual void shrinkLidSpace() = 0;
};

}
