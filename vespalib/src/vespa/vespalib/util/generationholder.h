// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vector>
#include <memory>
#include "generationhandler.h"

namespace vespalib {

class GenerationHeldBase
{
public:
    typedef GenerationHandler::generation_t generation_t;
    typedef std::unique_ptr<GenerationHeldBase> UP;
    typedef std::shared_ptr<GenerationHeldBase> SP;

    generation_t _generation;
private:
    size_t	 _size;

public:
    GenerationHeldBase(size_t size)
        : _generation(0u),
          _size(size)
    { }

    virtual ~GenerationHeldBase();
    size_t getSize() const { return _size; }
};

template<typename A>
class GenerationHeldAlloc : public GenerationHeldBase
{
public:
    GenerationHeldAlloc(A & alloc) : GenerationHeldBase(alloc.size()), _alloc() { _alloc.swap(alloc); }
    virtual ~GenerationHeldAlloc() { }
private:
    A _alloc;
};

/*
 * GenerationHolder is meant to hold large elements until readers can
 * no longer access them.
 */
class GenerationHolder
{
private:
    typedef GenerationHandler::generation_t generation_t;
    typedef GenerationHandler::sgeneration_t sgeneration_t;

    typedef std::vector<GenerationHeldBase::SP> HoldList;

    HoldList _hold1List;
    HoldList _hold2List;
    size_t   _heldBytes;

    /**
     * Transfer holds from hold1 to hold2 lists, assigning generation.
     */
    void transferHoldListsSlow(generation_t generation);

    /**
     * Remove all data elements from this holder where generation < usedGen.
     **/
    void trimHoldListsSlow(generation_t usedGen);

public:
    GenerationHolder();
    ~GenerationHolder();

    /**
     * Add the given data pointer to this holder.
     **/
    void hold(GenerationHeldBase::UP data);

    /**
     * Transfer holds from hold1 to hold2 lists, assigning generation.
     */
    void transferHoldLists(generation_t generation) {
        if (!_hold1List.empty()) {
            transferHoldListsSlow(generation);
        }
    }

    /**
     * Remove all data elements from this holder where generation < usedGen.
     **/
    void trimHoldLists(generation_t usedGen) {
        if (!_hold2List.empty() && static_cast<sgeneration_t>(_hold2List.front()->_generation - usedGen) < 0) {
            trimHoldListsSlow(usedGen);
        }
    }

    void clearHoldLists();
    size_t getHeldBytes() const { return _heldBytes; }
};

}

