// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <limits>

namespace vespalib::datastore {

inline constexpr size_t dynamic_alignment = std::numeric_limits<size_t>::max();

/*
 * Class used to align offsets to specified alignment.
 *
 * Alignment template parameter must be a power of 2 or the
 * dynamic_alignment value (which triggers specialization below).
 */
template <size_t alignment_v = dynamic_alignment>
class Aligner {
public:
    explicit constexpr Aligner() = default;
    explicit constexpr Aligner(size_t); // Never used but must be declared
    static size_t align(size_t unaligned) noexcept { return (unaligned + alignment_v - 1) & (- alignment_v); }
    static size_t pad(size_t unaligned) noexcept { return (- unaligned & (alignment_v - 1)); }
    static size_t alignment() noexcept { return alignment_v; }
};

/*
 * Specialization when alignment template argument is dynamic_alignment.
 * The constructor argument must be a power of 2.
 */
template <>
class Aligner<dynamic_alignment> {
    size_t _alignment;
public:
    explicit constexpr Aligner(size_t alignment_)
        : _alignment(alignment_)
    {
    }
    size_t align(size_t unaligned) const noexcept { return (unaligned + _alignment - 1) & (- _alignment); }
    size_t pad(size_t unaligned) const noexcept { return (- unaligned & (_alignment - 1)); }
    size_t alignment() const noexcept { return _alignment; }
};

}
