// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    explicit ImplicitDfaMatcher(std::span<const uint32_t> u32_str) noexcept
        : Base(u32_str)
    {}

    // start, is_match, can_match, match_edit_distance are all provided by base type

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
};

template <typename Traits>
LevenshteinDfa::MatchResult
ImplicitLevenshteinDfa<Traits>::match(std::string_view u8str, std::string* successor_out) const {
    ImplicitDfaMatcher<Traits> matcher(_u32_str_buf);
    return MatchAlgorithm<Traits::max_edits()>::match(matcher, u8str, successor_out);
}

template <typename Traits>
void ImplicitLevenshteinDfa<Traits>::dump_as_graphviz(std::ostream&) const {
    throw std::runtime_error("Graphviz output not available for implicit Levenshtein DFA");
}

}
