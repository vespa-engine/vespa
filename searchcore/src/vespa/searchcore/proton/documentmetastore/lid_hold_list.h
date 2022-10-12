// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include <deque>

namespace proton {

class LidStateVector;

/**
 * Class used to hold <lid, generation> pairs before reuse.
 * A lid is free for reuse if the associated generation < first used
 * generation by readers.
 **/
class LidHoldList
{
private:
    typedef vespalib::GenerationHandler::generation_t generation_t;
    typedef std::pair<uint32_t, generation_t> Element;
    typedef std::deque<Element> ElementDeque;

    ElementDeque _holdList;

public:
    LidHoldList();
    ~LidHoldList();

    /**
     * Adds a new element with the given generation.
     * Elements must be added with ascending generations.
     **/
    void add(const uint32_t data, generation_t generation);

    /**
     * Returns the total number of elements.
     **/
    size_t size() const { return _holdList.size(); }

    /**
     * Clears the free list.
     **/
    void clear();

    /**
     * Frees up elements with generation < oldest used generation for reuse.
     **/
    void reclaim_memory(generation_t oldest_used_gen, LidStateVector &freeLids);
};


} // namespace proton

