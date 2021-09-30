// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generationhandler.h"
#include <cassert>

namespace vespalib {

GenerationHandler::GenerationHold::GenerationHold(void)
    : _refCount(1),
      _generation(0),
      _next(0)
{ }

GenerationHandler::GenerationHold::~GenerationHold() {
    assert(getRefCount() == 0);
}

void
GenerationHandler::GenerationHold::setValid() {
    assert(!valid(_refCount));
    _refCount.fetch_sub(1);
}

bool
GenerationHandler::GenerationHold::setInvalid() {
    uint32_t refs = _refCount;
    assert(valid(refs));
    if (refs != 0) {
        return false;
    }
    return _refCount.compare_exchange_strong(refs, 1, std::memory_order_seq_cst);
}

void
GenerationHandler::GenerationHold::release() {
    _refCount.fetch_sub(2);
}

GenerationHandler::GenerationHold *
GenerationHandler::GenerationHold::acquire() {
    if (valid(_refCount.fetch_add(2))) {
        return this;
    } else {
        release();
        return nullptr;
    }
}

GenerationHandler::GenerationHold *
GenerationHandler::GenerationHold::copy(GenerationHold *self) {
    if (self == nullptr) {
        return nullptr;
    } else {
        uint32_t oldRefCount = self->_refCount.fetch_add(2);
        (void) oldRefCount;
        assert(valid(oldRefCount));
        return self;
    }
}

uint32_t
GenerationHandler::GenerationHold::getRefCount() const {
    return _refCount / 2;
}

GenerationHandler::Guard::Guard()
    : _hold(nullptr)
{
}

GenerationHandler::Guard::Guard(GenerationHold *hold)
    : _hold(hold->acquire())
{
}

GenerationHandler::Guard::~Guard()
{
    cleanup();
}

GenerationHandler::Guard::Guard(const Guard & rhs)
    : _hold(GenerationHold::copy(rhs._hold))
{
}

GenerationHandler::Guard::Guard(Guard &&rhs)
    : _hold(rhs._hold)
{
    rhs._hold = nullptr;
}

GenerationHandler::Guard &
GenerationHandler::Guard::operator=(const Guard & rhs)
{
    if (&rhs != this) {
        cleanup();
        _hold = GenerationHold::copy(rhs._hold);
    }
    return *this;
}

GenerationHandler::Guard &
GenerationHandler::Guard::operator=(Guard &&rhs)
{
    if (&rhs != this) {
        cleanup();
        _hold = rhs._hold;
        rhs._hold = nullptr;
    }
    return *this;
}

void
GenerationHandler::updateFirstUsedGeneration()
{
    for (;;) {
        if (_first == _last)
            break;			// No elements can be freed
        if (!_first->setInvalid()) {
            break;			// First element still in use
        }
        GenerationHold *toFree = _first;
        assert(toFree->_next != nullptr);
        _first = toFree->_next;
        // Must ensure _first is updated before changing next pointer to
        // avoid temporarily inconsistent state (breaks hasReaders())
        std::atomic_thread_fence(std::memory_order_release);
        toFree->_next = _free;
        _free = toFree;
    }
    _firstUsedGeneration = _first->_generation;
}

GenerationHandler::GenerationHandler()
    : _generation(0),
      _firstUsedGeneration(0),
      _last(nullptr),
      _first(nullptr),
      _free(nullptr),
      _numHolds(0u)
{
    _last = _first = new GenerationHold;
    ++_numHolds;
    _last->_generation = _generation;
    _last->setValid();
}

GenerationHandler::~GenerationHandler(void)
{
    updateFirstUsedGeneration();
    assert(_first == _last);
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
    Guard guard(_last);
    for (;;) {
        // Must check valid() after increasing refcount
        std::atomic_thread_fence(std::memory_order_acquire);
        if (guard.valid())
            break;		// Might still be marked invalid, that's OK
        /*
         * Clashed with writer freeing entry.  Must abandon current
         * guard and try again.
         */
        guard = Guard(_last);
    }
    // Guard has been valid after bumping refCount
    return guard;
}

void
GenerationHandler::incGeneration()
{
    generation_t ngen = getNextGeneration();

    std::atomic_thread_fence(std::memory_order_seq_cst);
    if (_last->getRefCount() == 0) {
        // Last generation is unused, morph it to new generation.  This is
        // the typical case when no readers are present.
        _generation = ngen;
        _last->_generation = ngen;
        std::atomic_thread_fence(std::memory_order_release);
        updateFirstUsedGeneration();
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
    nhold->_generation = ngen;
    nhold->_next = nullptr;
    nhold->setValid();

    // new hold must be updated before next pointer is updated
    std::atomic_thread_fence(std::memory_order_release);
    _last->_next = nhold;

    // next pointer must be updated before _last is updated
    std::atomic_thread_fence(std::memory_order_release);
    _generation = ngen;
    _last = nhold;

    // _last must be updated before _first is changed
    std::atomic_thread_fence(std::memory_order_release);
    updateFirstUsedGeneration();
}

uint32_t
GenerationHandler::getGenerationRefCount(generation_t gen) const
{
    if (static_cast<sgeneration_t>(gen - _generation) > 0)
        return 0u;
    if (static_cast<sgeneration_t>(_firstUsedGeneration - gen) > 0)
        return 0u;
    for (GenerationHold *hold = _first; hold != nullptr; hold = hold->_next) {
        if (hold->_generation == gen)
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

bool
GenerationHandler::hasReaders(void) const
{
    return (_first != _last) ? true : (_first->getRefCount() > 0);
}

}
