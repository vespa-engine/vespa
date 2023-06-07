// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationhandler.h"
#include <cassert>

namespace vespalib {

GenerationHandler::GenerationHold::GenerationHold() noexcept
    : _refCount(1),
      _generation(0),
      _next(0)
{ }

GenerationHandler::GenerationHold::~GenerationHold() {
    assert(getRefCount() == 0);
}

void
GenerationHandler::GenerationHold::setValid() noexcept {
    auto old = _refCount.fetch_sub(1, std::memory_order_release);
    assert(!valid(old));
}

bool
GenerationHandler::GenerationHold::setInvalid() noexcept {
    uint32_t refs = 0;
    if (_refCount.compare_exchange_strong(refs, 1,
                                          std::memory_order_acq_rel,
                                          std::memory_order_relaxed))
    {
        return true;
    } else {
        assert(valid(refs));
        return false;
    }
}

GenerationHandler::GenerationHold *
GenerationHandler::GenerationHold::acquire() noexcept {
    if (valid(_refCount.fetch_add(2, std::memory_order_acq_rel))) {
        return this;
    } else {
        release();
        return nullptr;
    }
}

GenerationHandler::GenerationHold *
GenerationHandler::GenerationHold::copy(GenerationHold *self) noexcept {
    if (self == nullptr) {
        return nullptr;
    } else {
        uint32_t oldRefCount = self->_refCount.fetch_add(2, std::memory_order_relaxed);
        (void) oldRefCount;
        assert(valid(oldRefCount));
        return self;
    }
}

GenerationHandler::Guard &
GenerationHandler::Guard::operator=(const Guard & rhs) noexcept
{
    if (&rhs != this) {
        cleanup();
        _hold = GenerationHold::copy(rhs._hold);
    }
    return *this;
}

GenerationHandler::Guard &
GenerationHandler::Guard::operator=(Guard &&rhs) noexcept
{
    if (&rhs != this) {
        cleanup();
        _hold = rhs._hold;
        rhs._hold = nullptr;
    }
    return *this;
}

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
    : _generation(0),
      _oldest_used_generation(0),
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

GenerationHandler::~GenerationHandler(void)
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

GenerationHandler::Guard
GenerationHandler::takeGuard() const
{
    Guard guard(_last.load(std::memory_order_acquire));
    for (;;) {
        // Must check valid() after increasing refcount
        if (guard.valid())
            break;		// Might still be marked invalid, that's OK
        /*
         * Clashed with writer freeing entry.  Must abandon current
         * guard and try again.
         */
        guard = Guard(_last.load(std::memory_order_acquire));
    }
    // Guard has been valid after bumping refCount
    return guard;
}

void
GenerationHandler::incGeneration()
{
    generation_t ngen = getNextGeneration();

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
GenerationHandler::getGenerationRefCount(generation_t gen) const
{
    if (static_cast<sgeneration_t>(gen - getCurrentGeneration()) > 0)
        return 0u;
    if (static_cast<sgeneration_t>(get_oldest_used_generation() - gen) > 0)
        return 0u;
    for (GenerationHold *hold = _first; hold != nullptr; hold = hold->_next) {
        if (hold->_generation.load(std::memory_order_relaxed) == gen)
            return hold->getRefCount();
    }
    return 0u;
}

uint64_t
GenerationHandler::getGenerationRefCount(void) const
{
    uint64_t ret = 0;
    for (GenerationHold *hold = _first; hold != nullptr; hold = hold->_next) {
        ret += hold->getRefCount();
    }
    return ret;
}

}
