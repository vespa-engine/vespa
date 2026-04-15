// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generation_guard.h"

namespace vespalib {

GenerationGuard &
GenerationGuard::operator=(const GenerationGuard& rhs) noexcept
{
    if (&rhs != this) {
        cleanup();
        _hold = GenerationHold::copy(rhs._hold);
    }
    return *this;
}

GenerationGuard &
GenerationGuard::operator=(GenerationGuard&& rhs) noexcept
{
    if (&rhs != this) {
        cleanup();
        _hold = rhs._hold;
        rhs._hold = nullptr;
    }
    return *this;
}

}
