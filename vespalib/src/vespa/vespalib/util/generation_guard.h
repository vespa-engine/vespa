// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generation_hold.h"

namespace vespalib {

/**
 * Class that keeps a reference to a generation until destroyed. It is used by a reader to signal that memory accessed
 * by this reader should be kept type stable and mostly value stable.
 **/
class GenerationGuard {
private:
    GenerationHold* _hold;
    void cleanup() noexcept {
        if (_hold != nullptr) {
            _hold->release();
            _hold = nullptr;
        }
    }

public:
    GenerationGuard() noexcept : _hold(nullptr) {}
    GenerationGuard(GenerationHold* hold) noexcept : _hold(hold->acquire()) {} // hold is never nullptr
    GenerationGuard(const GenerationGuard& rhs) noexcept : _hold(GenerationHold::copy(rhs._hold)) {}
    GenerationGuard(GenerationGuard&& rhs) noexcept : _hold(rhs._hold) { rhs._hold = nullptr; }
    ~GenerationGuard() { cleanup(); }
    GenerationGuard& operator=(const GenerationGuard& rhs) noexcept;
    GenerationGuard& operator=(GenerationGuard&& rhs) noexcept;
    bool valid() const noexcept { return _hold != nullptr; }
    Generation getGeneration() const { return _hold->_generation.load(std::memory_order_relaxed); }
};

} // namespace vespalib
