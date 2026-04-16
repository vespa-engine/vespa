// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationhandler.h"
#include <cassert>

namespace vespalib {

void
GenerationHandler::update_oldest_used_generation()
{
    for (;;) {
        if (_first == _last.load(std::memory_order_relaxed))
            break;			// No elements can be freed
        if (!_first->setInvalid()) {
            break;			// First element still in use
        }
        GenerationHold *toFree = _first;
        assert(toFree->_next != nullptr);
        _first = toFree->_next;
        toFree->_next = _free;
        _free = toFree;
    }
    _oldest_used_generation.store(_first->_generation, std::memory_order_relaxed);
}

GenerationHandler::GenerationHandler()
    : _generation(Generation(0)),
      _oldest_used_generation(Generation(0)),
      _last(nullptr),
      _first(nullptr),
      _free(nullptr),
      _numHolds(0u)
{
    _last = _first = new GenerationHold;
    ++_numHolds;
    _first->_generation.store(getCurrentGeneration(), std::memory_order_relaxed);
    _first->setValid();
}

GenerationHandler::~GenerationHandler()
{
    update_oldest_used_generation();
    assert(_first == _last.load(std::memory_order_relaxed));
    while (_free != nullptr) {
        GenerationHold *toFree = _free;
        _free = toFree->_next;
        --_numHolds;
        delete toFree;
    }
    assert(_numHolds == 1);
    delete _first;
}

GenerationGuard
GenerationHandler::takeGuard() const
{
    GenerationGuard guard(_last.load(std::memory_order_acquire));
    for (;;) {
        // Must check valid() after increasing refcount
        if (guard.valid())
            break;		// Might still be marked invalid, that's OK
        /*
         * Clashed with writer freeing entry.  Must abandon current
         * guard and try again.
         */
        guard = GenerationGuard(_last.load(std::memory_order_acquire));
    }
    // Guard has been valid after bumping refCount
    return guard;
}

void
GenerationHandler::incGeneration()
{
    Generation ngen = getNextGeneration();

    auto last = _last.load(std::memory_order_relaxed);
    if (last->getRefCountAcqRel() == 0) {
        // Last generation is unused, morph it to new generation.  This is
        // the typical case when no readers are present.
        set_generation(ngen);
        last->_generation.store(ngen, std::memory_order_relaxed);
        update_oldest_used_generation();
        return;
    }
    GenerationHold *nhold = nullptr;
    if (_free == nullptr) {
        nhold = new GenerationHold;
        ++_numHolds;
    } else {
        nhold = _free;
        _free = nhold->_next;
    }
    nhold->_generation.store(ngen, std::memory_order_relaxed);
    nhold->_next = nullptr;
    nhold->setValid();
    last->_next = nhold;
    set_generation(ngen);
    _last.store(nhold, std::memory_order_release);
    update_oldest_used_generation();
}

uint32_t
GenerationHandler::getGenerationRefCount(Generation gen) const
{
    if (gen > getCurrentGeneration() || get_oldest_used_generation() > gen) {
        return 0u;
    }
    for (GenerationHold *hold = _first; hold != nullptr; hold = hold->_next) {
        if (hold->_generation.load(std::memory_order_relaxed) == gen)
            return hold->getRefCount();
    }
    return 0u;
}

uint64_t
GenerationHandler::getGenerationRefCount() const
{
    uint64_t ret = 0;
    for (GenerationHold *hold = _first; hold != nullptr; hold = hold->_next) {
        ret += hold->getRefCount();
    }
    return ret;
}

}
