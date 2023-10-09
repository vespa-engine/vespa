// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dfa_stepping_base.h"
#include "implicit_levenshtein_dfa.h"
#include "match_algorithm.hpp"
#include "sparse_state.h"
#include <cassert>
#include <stdexcept>

namespace vespalib::fuzzy {

// DfaMatcher adapter for implicit DFA implementation
template <typename Traits>
struct ImplicitDfaMatcher : public DfaSteppingBase<Traits> {
    using Base = DfaSteppingBase<Traits>;

    using StateType = typename Base::StateType;
    using EdgeType  = uint32_t; // Just the raw u32 character value

    using StateParamType = const StateType&;

    using Base::_u32_str;
    using Base::max_edits;
    using Base::start;
    using Base::match_edit_distance;
    using Base::step;
    using Base::can_wildcard_step;
    using Base::is_match;
    using Base::can_match;

    std::span<const char>     _target_as_utf8;
    std::span<const uint32_t> _target_utf8_char_offsets;
    const bool                _is_cased;

    ImplicitDfaMatcher(std::span<const uint32_t> u32_str,
                       std::span<const char>     target_as_utf8,
                       std::span<const uint32_t> target_utf8_char_offsets,
                       bool is_cased) noexcept
        : Base(u32_str),
          _target_as_utf8(target_as_utf8),
          _target_utf8_char_offsets(target_utf8_char_offsets),
          _is_cased(is_cased)
    {}

    // start, is_match, can_match, match_edit_distance are all provided by base type

    bool is_cased() const noexcept { return _is_cased; }

    template <typename F>
    bool has_any_char_matching(const StateType& state, F&& f) const noexcept(noexcept(f(uint32_t{}))) {
        for (uint32_t i = 0; i < state.size(); ++i) {
            const auto idx = state.index(i);
            if ((idx < _u32_str.size()) && f(_u32_str[idx])) {
                return true;
            }
        }
        return false;
    }

    template <typename F>
    void for_each_char(const StateType& state, F&& f) const noexcept(noexcept(f(uint32_t{}))) {
        for (uint32_t i = 0; i < state.size(); ++i) {
            const auto idx = state.index(i);
            if ((idx < _u32_str.size())) [[likely]] {
                f(_u32_str[idx]);
            }
        }
    }

    bool has_explicit_higher_out_edge(const StateType& state, uint32_t ch) const noexcept {
        return has_any_char_matching(state, [ch](uint32_t state_ch) noexcept {
            return state_ch > ch;
        });
    }

    bool has_higher_out_edge(const StateType& state, uint32_t mch) const noexcept {
        return (has_explicit_higher_out_edge(state, mch) || can_wildcard_step(state));
    }
    StateType match_input(const StateType& state, uint32_t mch) const noexcept {
        return step(state, mch);
    }
    bool valid_state(const StateType& state) const noexcept {
        return !state.empty();
    }
    StateType match_wildcard(const StateType& state) const noexcept {
        return step(state, WILDCARD);
    }
    bool has_exact_explicit_out_edge(const StateType& state, uint32_t ch) const noexcept {
        return has_any_char_matching(state, [ch](uint32_t state_ch) noexcept {
            return state_ch == ch;
        });
    }
    EdgeType lowest_higher_explicit_out_edge(const StateType& state, uint32_t ch) const noexcept {
        uint32_t min_ch = UINT32_MAX;
        for_each_char(state, [ch, &min_ch](uint32_t state_ch) noexcept {
            if ((state_ch > ch) && (state_ch < min_ch)) {
                min_ch = state_ch;
            }
        });
        return min_ch;
    }
    EdgeType smallest_explicit_out_edge(const StateType& state) const noexcept {
        uint32_t min_ch = UINT32_MAX;
        for_each_char(state, [&min_ch](uint32_t state_ch) noexcept {
            min_ch = std::min(min_ch, state_ch);
        });
        return min_ch;
    }
    bool valid_edge(EdgeType edge) const noexcept {
        return edge != UINT32_MAX;
    }
    uint32_t edge_to_u32char(EdgeType edge) const noexcept {
        return edge;
    }
    StateType edge_to_state(const StateType& state, EdgeType edge) const noexcept {
        return step(state, edge);
    }
    bool implies_exact_match_suffix(const StateType& state) const noexcept {
        // Only one entry in the sparse matrix row and it implies no edits can be done.
        // I.e. only way to match the target string suffix is to match it _exactly_.
        return (state.size() == 1 && state.cost(0) == max_edits());
    }
    // Precondition: implies_match_suffix(state)
    void emit_exact_match_suffix(const StateType& state, std::string& u8_out) const {
        const uint32_t target_u8_offset = _target_utf8_char_offsets[state.index(0)];
        u8_out.append(_target_as_utf8.data() + target_u8_offset, _target_as_utf8.size() - target_u8_offset);
    }
    void emit_exact_match_suffix(const StateType& state, std::vector<uint32_t>& u32_out) const {
        // TODO ranged insert
        for (uint32_t i = state.index(0); i < _u32_str.size(); ++i) {
            u32_out.push_back(_u32_str[i]);
        }
    }
};

template <typename Traits>
LevenshteinDfa::MatchResult
ImplicitLevenshteinDfa<Traits>::match(std::string_view u8str) const {
    ImplicitDfaMatcher<Traits> matcher(_u32_str_buf, _target_as_utf8, _target_utf8_char_offsets, _is_cased);
    return MatchAlgorithm<Traits::max_edits()>::match(matcher, u8str);
}

template <typename Traits>
LevenshteinDfa::MatchResult
ImplicitLevenshteinDfa<Traits>::match(std::string_view u8str, std::string& successor_out) const {
    ImplicitDfaMatcher<Traits> matcher(_u32_str_buf, _target_as_utf8, _target_utf8_char_offsets, _is_cased);
    return MatchAlgorithm<Traits::max_edits()>::match(matcher, u8str, successor_out);
}

template <typename Traits>
LevenshteinDfa::MatchResult
ImplicitLevenshteinDfa<Traits>::match(std::string_view u8str, std::vector<uint32_t>& successor_out) const {
    ImplicitDfaMatcher<Traits> matcher(_u32_str_buf, _target_as_utf8, _target_utf8_char_offsets, _is_cased);
    return MatchAlgorithm<Traits::max_edits()>::match(matcher, u8str, successor_out);
}

template <typename Traits>
void ImplicitLevenshteinDfa<Traits>::dump_as_graphviz(std::ostream&) const {
    throw std::runtime_error("Graphviz output not available for implicit Levenshtein DFA");
}

}
