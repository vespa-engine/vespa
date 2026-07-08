// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generation_hold.h"

#include <cassert>

namespace vespalib {

GenerationHold::GenerationHold() noexcept : _refCount(1), _generation(Generation(0)), _next(nullptr) {
}

GenerationHold::~GenerationHold() {
    assert(getRefCount() == 0);
}

void GenerationHold::setValid() noexcept {
    auto old = _refCount.fetch_sub(1, std::memory_order_release);
    assert(!valid(old));
}

bool GenerationHold::setInvalid() noexcept {
    uint32_t refs = 0;
    if (_refCount.compare_exchange_strong(refs, 1, std::memory_order_acq_rel, std::memory_order_relaxed)) {
        return true;
    } else {
        assert(valid(refs));
        return false;
    }
}

GenerationHold* GenerationHold::acquire() noexcept {
    if (valid(_refCount.fetch_add(2, std::memory_order_acq_rel))) {
        return this;
    } else {
        release();
        return nullptr;
    }
}

GenerationHold* GenerationHold::copy(GenerationHold* self) noexcept {
    if (self == nullptr) {
        return nullptr;
    } else {
        uint32_t oldRefCount = self->_refCount.fetch_add(2, std::memory_order_relaxed);
        (void)oldRefCount;
        assert(valid(oldRefCount));
        return self;
    }
}

} // namespace vespalib
