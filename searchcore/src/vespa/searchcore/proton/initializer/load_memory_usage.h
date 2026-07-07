// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstddef>

namespace proton::initializer {

/*
 * Mmeory usage for an initializer task. Transient memory is freed before task is completed. Permanent
 * memory is owned by the loaded data structure.
 */
class LoadMemoryUsage {
    size_t _transient;
    size_t _permanent;

public:
    constexpr LoadMemoryUsage() noexcept : LoadMemoryUsage(0, 0) {}
    constexpr LoadMemoryUsage(size_t transient_, size_t permanent_) noexcept
        : _transient(transient_), _permanent(permanent_) {}
    [[nodiscard]] size_t transient() const noexcept { return _transient; }
    [[nodiscard]] size_t permanent() const noexcept { return _permanent; }
};

} // namespace proton::initializer
