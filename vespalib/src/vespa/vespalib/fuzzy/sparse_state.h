// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sanitizers.h>
#include <algorithm>
#include <array>
#include <cassert>
#include <cstdint>
#include <ostream>
#include <span>
#include <xxh3.h> // TODO factor out?

namespace vespalib::fuzzy {

// Sentinel U32 char for state stepping that cannot match any target string characters
constexpr const uint32_t WILDCARD = UINT32_MAX;

/**
 * diag(n) is the width of the diagonal of the cost matrix that can possibly be
 * within k edits. This means that for a fixed k, it suffices to maintain state
 * for up to and including diag(k) consecutive cells for any given matrix row.
 */
constexpr inline uint8_t diag(uint8_t k) noexcept {
    return k*2 + 1;
}

template <uint8_t MaxEdits>
struct FixedSparseState {
private:
    static_assert(MaxEdits > 0 && MaxEdits <= UINT8_MAX/2);

    std::array<uint32_t, diag(MaxEdits)> indices;
    std::array<uint8_t,  diag(MaxEdits)> costs; // elems are 1-1 with indices vector
    uint8_t sz;
public:
    constexpr FixedSparseState() noexcept : indices(), costs(), sz(0) {}

    [[nodiscard]] constexpr bool empty() const noexcept {
        return (sz == 0);
    }

    [[nodiscard]] constexpr uint32_t size() const noexcept {
        return sz;
    }

    [[nodiscard]] constexpr uint32_t index(uint32_t entry_idx) const noexcept {
        return indices[entry_idx];
    }

    [[nodiscard]] constexpr uint8_t cost(uint32_t entry_idx) const noexcept {
        return costs[entry_idx];
    }

    // Precondition: !empty()
    [[nodiscard]] constexpr uint32_t last_index() const noexcept {
        return indices[sz - 1];
    }

    // Precondition: !empty()
    [[nodiscard]] constexpr uint8_t last_cost() const noexcept {
        return costs[sz - 1];
    }

    void append(uint32_t index, uint8_t cost) noexcept {
        assert(sz < diag(MaxEdits));
        indices[sz] = index;
        costs[sz] = cost;
        ++sz;
    }

    constexpr bool operator==(const FixedSparseState& rhs) const noexcept {
        if (sz != rhs.sz) {
            return false;
        }
        return (std::equal(indices.begin(), indices.begin() + sz, rhs.indices.begin()) &&
                std::equal(costs.begin(),   costs.begin()   + sz, rhs.costs.begin()));
    }

    struct hash {
        size_t operator()(const FixedSparseState& s) const noexcept {
            static_assert(std::is_same_v<uint32_t, std::decay_t<decltype(s.indices[0])>>);
            static_assert(std::is_same_v<uint8_t,  std::decay_t<decltype(s.costs[0])>>);
            // FIXME GCC 12.2 worse-than-useless(tm) warning false positives :I
#pragma GCC diagnostic push
#ifdef VESPA_USE_SANITIZER
#  pragma GCC diagnostic ignored "-Wstringop-overread" // https://gcc.gnu.org/bugzilla/show_bug.cgi?id=98465 etc.
#endif
#pragma GCC diagnostic ignored "-Warray-bounds"
            return (XXH3_64bits(s.indices.data(), s.sz * sizeof(uint32_t)) ^
                    XXH3_64bits(s.costs.data(), s.sz));
#pragma GCC diagnostic pop
        }
    };
};

/**
 * Prints sparse states as a single matrix row. Columns prior to any state index
 * are printed explicitly as '-' characters to make states line up when printed.
 *
 * Example output for the state (2:1, 3:1):
 *
 *   [-, -, 1, 1]
 *
 * Only meant as a debugging aid during development, as states with high indices
 * will emit very large strings.
 */
template <uint8_t MaxEdits> [[maybe_unused]]
std::ostream& operator<<(std::ostream& os, const FixedSparseState<MaxEdits>& s) {
    os << "[";
    size_t last_idx = 0;
    for (size_t i = 0; i < s.size(); ++i) {
        if (i != 0) {
            os << ", ";
        }
        for (size_t j = last_idx; j < s.indices[i]; ++j) {
            os << "-, ";
        }
        last_idx = s.indices[i] + 1;
        os << static_cast<uint32_t>(s.costs[i]);
    }
    os << "]";
    return os;
}

template <uint8_t MaxEdits>
struct FixedMaxEditsTransitions {
    static_assert(MaxEdits > 0 && MaxEdits <= UINT8_MAX/2);

    std::array<uint32_t, diag(MaxEdits)> out_u32_chars;
    uint8_t size;

    constexpr FixedMaxEditsTransitions() noexcept : out_u32_chars(), size(0) {}

    [[nodiscard]] constexpr bool has_char(uint32_t u32ch) const noexcept {
        for (uint8_t i = 0; i < size; ++i) {
            if (out_u32_chars[i] == u32ch) {
                return true;
            }
        }
        return false;
    }

    void add_char(uint32_t u32ch) noexcept {
        if (!has_char(u32ch)) {
            assert(size < diag(MaxEdits));
            out_u32_chars[size] = u32ch;
            ++size;
        }
    }

    constexpr std::span<const uint32_t> u32_chars() const noexcept {
        return {out_u32_chars.begin(), out_u32_chars.begin() + size};
    }

    constexpr std::span<uint32_t> u32_chars() noexcept {
        return {out_u32_chars.begin(), out_u32_chars.begin() + size};
    }

    void sort() noexcept {
        // TODO use custom sorting networks for fixed array sizes <= 5?
        // FIXME GCC 12.2 worse-than-useless(tm) warning false positives :I
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Warray-bounds"
        std::sort(out_u32_chars.begin(), out_u32_chars.begin() + size);
#pragma GCC diagnostic pop
    }
};

template <uint8_t MaxEdits>
struct FixedMaxEditDistanceTraits {
    static_assert(MaxEdits > 0 && MaxEdits <= UINT8_MAX/2);
    using StateType       = FixedSparseState<MaxEdits>;
    using TransitionsType = FixedMaxEditsTransitions<MaxEdits>;
    constexpr static uint8_t max_edits() noexcept {
        return MaxEdits;
    }
};

}
