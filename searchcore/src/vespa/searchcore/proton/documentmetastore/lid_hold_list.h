// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "lidstatevector.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <deque>

namespace proton {

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
    LidHoldList()
        : _holdList()
    {
    }

    /**
     * Adds a new element with the given generation.
     * Elements must be added with ascending generations.
     **/
    void add(const uint32_t data, generation_t generation) {
        if (!_holdList.empty()) {
            assert(generation >= _holdList.back().second);
        }
        _holdList.push_back(std::make_pair(data, generation));
    }

    /**
     * Returns the total number of elements.
     **/
    size_t size() const { return _holdList.size(); }

    /**
     * Clears the free list.
     **/
    void clear() { _holdList.clear(); }

    /**
     * Frees up elements with generation < first used generation for reuse.
     **/
    void trimHoldLists(generation_t firstUsed, LidStateVector &freeLids)
    {
        while (!_holdList.empty() && _holdList.front().second < firstUsed) {
            uint32_t lid = _holdList.front().first;
            freeLids.setBit(lid);
            _holdList.pop_front();
        }
    }
};


} // namespace proton

