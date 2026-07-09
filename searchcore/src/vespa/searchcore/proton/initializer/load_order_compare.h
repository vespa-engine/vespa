// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "load_memory_usage.h"

namespace proton::initializer {

/*
 * Comparator used to determine load order. Used by initializer task runner when dispatching ready tasks.
 */
class LoadOrderCompare {
public:
    bool operator()(const LoadMemoryUsage& lhs, const LoadMemoryUsage& rhs) const noexcept {
        if (lhs.transient() != rhs.transient()) {
            return lhs.transient() > rhs.transient();
        }
        return lhs.permanent() > rhs.permanent();
    }
};

} // namespace proton::initializer
