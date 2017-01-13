// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search {
namespace common {

/**
 * Interface for a component that has a lid space that can be compacted and shrinked.
 */     
struct ICompactableLidSpace {
    virtual ~ICompactableLidSpace() {}

    /**
     * Compacts the lid space down to the wanted given doc id limit.
     * After this, the remaining lid space is a candidate for shrinking (freeing of memory resources).
     */  
    virtual void compactLidSpace(uint32_t wantedDocLidLimit) = 0;

    /**
     * Returns whether this lid space can be shrinked down to the wanted doc id limit.
     */ 
    virtual bool canShrinkLidSpace() const = 0;

    /**
     * Shrinks this lid space down to the wanted doc id limit (frees memory resources).
     */
    virtual void shrinkLidSpace() = 0;
};

}
}

