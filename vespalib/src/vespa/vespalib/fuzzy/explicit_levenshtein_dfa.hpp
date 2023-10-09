// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "explicit_levenshtein_dfa.h"
#include "match_algorithm.hpp"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <iostream>
#include <span>
#include <queue>

namespace vespalib::fuzzy {

// DfaMatcher adapter for explicit DFA implementation
template <uint8_t MaxEdits>
struct ExplicitDfaMatcher {
    using DfaNodeType = typename ExplicitLevenshteinDfaImpl<MaxEdits>::DfaNodeType;
    using StateType   = const DfaNodeType*;
    using EdgeType    = const typename DfaNodeType::Edge*;

    using StateParamType = const DfaNodeType*;

    const std::span<const DfaNodeType> _nodes;
    const bool                         _is_cased;

    ExplicitDfaMatcher(const std::span<const DfaNodeType> nodes, bool is_cased) noexcept
        : _nodes(nodes),
          _is_cased(is_cased)
    {}

    static constexpr uint8_t max_edits() noexcept { return MaxEdits; }

    bool is_cased() const noexcept { return _is_cased; }

    StateType start() const noexcept {
        return &_nodes[0];
    }
    bool has_higher_out_edge(StateType node, uint32_t mch) const noexcept {
        return node->has_higher_out_edge(mch);
    }
    StateType match_input(StateType node, uint32_t mch) const noexcept {
        auto maybe_node_idx = node->match_or_doomed(mch);
        return ((maybe_node_idx != DOOMED) ? &_nodes[maybe_node_idx] : nullptr);
    }
    bool is_match(StateType node) const noexcept {
        return node->edits <= max_edits();
    }
    bool can_match(StateType node) const noexcept {
        return node != nullptr;
    }
    uint8_t match_edit_distance(StateType node) const noexcept {
        return node->edits;
    }
    bool valid_state(StateType node) const noexcept {
        return node != nullptr;
    }
    StateType match_wildcard(StateType node) const noexcept {
        auto edge_to = node->wildcard_edge_to_or_doomed();
        return ((edge_to != DOOMED) ? &_nodes[edge_to] : nullptr);
    }
    bool has_exact_explicit_out_edge(StateType node, uint32_t ch) const noexcept {
        return node->has_exact_match(ch);
    }
    EdgeType lowest_higher_explicit_out_edge(StateType node, uint32_t ch) const noexcept {
        return node->lowest_higher_explicit_out_edge(ch);
    }
    EdgeType smallest_explicit_out_edge(StateType node) const noexcept {
        // Out-edges are pre-ordered in increasing code point order, so the first
        // element is always the smallest possible matching character.
        assert(!node->match_out_edges().empty());
        return &node->match_out_edges().front();
    }
    bool valid_edge(EdgeType edge) const noexcept {
        return edge != nullptr;
    }
    uint32_t edge_to_u32char(EdgeType edge) const noexcept {
        return edge->u32ch;
    }
    StateType edge_to_state([[maybe_unused]] StateType node, EdgeType edge) const noexcept {
        return &_nodes[edge->node];
    }
    constexpr bool implies_exact_match_suffix(const StateType&) const noexcept {
        // TODO; caller will currently just fall back to explicit state stepping
        return false;
    }
    void emit_exact_match_suffix(const StateType&, std::string&) const {
        // TODO (will never be called as long as `implies_exact_match_suffix()` returns false)
        abort();
    }
    void emit_exact_match_suffix(const StateType&, std::vector<uint32_t>&) const {
        // TODO (will never be called as long as `implies_exact_match_suffix()` returns false)
        abort();
    }
};

template <uint8_t MaxEdits>
LevenshteinDfa::MatchResult
ExplicitLevenshteinDfaImpl<MaxEdits>::match(std::string_view u8str) const {
    ExplicitDfaMatcher<MaxEdits> matcher(_nodes, _is_cased);
    return MatchAlgorithm<MaxEdits>::match(matcher, u8str);
}

template <uint8_t MaxEdits>
LevenshteinDfa::MatchResult
ExplicitLevenshteinDfaImpl<MaxEdits>::match(std::string_view u8str, std::string& successor_out) const {
    ExplicitDfaMatcher<MaxEdits> matcher(_nodes, _is_cased);
    return MatchAlgorithm<MaxEdits>::match(matcher, u8str, successor_out);
}

template <uint8_t MaxEdits>
LevenshteinDfa::MatchResult
ExplicitLevenshteinDfaImpl<MaxEdits>::match(std::string_view u8str, std::vector<uint32_t>& successor_out) const {
    ExplicitDfaMatcher<MaxEdits> matcher(_nodes, _is_cased);
    return MatchAlgorithm<MaxEdits>::match(matcher, u8str, successor_out);
}

template <uint8_t MaxEdits>
void ExplicitLevenshteinDfaImpl<MaxEdits>::dump_as_graphviz(std::ostream& os) const {
    os << std::dec << "digraph levenshtein_dfa {\n";
    os << "    fontname=\"Helvetica,Arial,sans-serif\"\n";
    os << "    node [shape=circle, fontname=\"Helvetica,Arial,sans-serif\", fixedsize=true];\n";
    os << "    edge [fontname=\"Helvetica,Arial,sans-serif\"];\n";
    for (size_t i = 0; i < _nodes.size(); ++i) {
        const auto& node = _nodes[i];
        if (node.edits <= max_edits()) {
            os << "    " << i << " [label=\"" << i << "(" << static_cast<int>(node.edits) << ")\", style=\"filled\"];\n";
        }
        for (const auto& edge : node.match_out_edges()) {
            std::string as_utf8;
            append_utf32_char(as_utf8, edge.u32ch);
            os << "    " << i << " -> " << edge.node << " [label=\"" << as_utf8 << "\"];\n";
        }
        if (node.wildcard_edge_to != DOOMED) {
            os << "    " << i << " -> " << node.wildcard_edge_to << " [label=\"*\"];\n";
        }
    }
    os << "}\n";
}

namespace {

template <typename StateType>
struct ExploreState {
    using NodeIdAndExplored    = std::pair<uint32_t, bool>;
    using SparseExploredStates = vespalib::hash_map<StateType, NodeIdAndExplored, typename StateType::hash>;

    uint32_t             state_counter;
    SparseExploredStates explored_states;

    ExploreState();
    ~ExploreState();

    [[nodiscard]] typename SparseExploredStates::iterator node_of(const StateType& state) {
        auto maybe_explored = explored_states.find(state);
        if (maybe_explored != explored_states.end()) {
            return maybe_explored;
        }
        uint32_t this_node = state_counter;
        assert(state_counter < UINT32_MAX);
        ++state_counter;
        return explored_states.insert(std::make_pair(state, std::make_pair(this_node, false))).first; // not yet explored;
    }

    [[nodiscard]] bool already_explored(const typename SparseExploredStates::iterator& node) const noexcept {
        return node->second.second;
    }

    void tag_as_explored(typename SparseExploredStates::iterator& node) noexcept {
        node->second.second = true;
    }
};

template <typename StateType>
ExploreState<StateType>::ExploreState()
    : state_counter(0),
      explored_states()
{}

template <typename StateType>
ExploreState<StateType>::~ExploreState() = default;

template <typename Traits>
class ExplicitLevenshteinDfaBuilderImpl : public DfaSteppingBase<Traits> {
    using Base = DfaSteppingBase<Traits>;

    using StateType       = typename Base::StateType;
    using TransitionsType = typename Base::TransitionsType;

    using Base::_u32_str;
    using Base::max_edits;
    using Base::start;
    using Base::match_edit_distance;
    using Base::step;
    using Base::is_match;
    using Base::can_match;
    using Base::transitions;

    const bool _is_cased;
public:
    ExplicitLevenshteinDfaBuilderImpl(std::span<const uint32_t> str, bool is_cased) noexcept
        : DfaSteppingBase<Traits>(str),
          _is_cased(is_cased)
    {
        assert(str.size() < UINT32_MAX / max_out_edges_per_node());
    }

    [[nodiscard]] static constexpr uint8_t max_out_edges_per_node() noexcept {
        // Max possible out transition characters (2k+1) + one wildcard edge.
        return diag(max_edits()) + 1;
    }

    [[nodiscard]] LevenshteinDfa build_dfa() const;
};

template <typename Traits>
LevenshteinDfa ExplicitLevenshteinDfaBuilderImpl<Traits>::build_dfa() const {
    auto dfa = std::make_unique<ExplicitLevenshteinDfaImpl<max_edits()>>(_is_cased);
    ExploreState<StateType> exp;
    // Use BFS instead of DFS to ensure most node edges point to nodes that are allocated _after_
    // the parent node, which means the CPU can skip ahead instead of ping-ponging back and forth.
    // This does _not_ always hold, such as if you have A->B and A->C->B (i.e. both parent and
    // grandparent have a transition to the same state), in which case B may be allocated before C.
    std::queue<StateType> to_explore;
    to_explore.push(start());
    while (!to_explore.empty()) {
        auto state = std::move(to_explore.front());
        to_explore.pop();
        auto this_node = exp.node_of(state); // note: invalidated by subsequent calls to node_of
        if (exp.already_explored(this_node)) {
            continue;
        }
        exp.tag_as_explored(this_node);
        const auto this_node_idx = this_node->second.first;
        dfa->ensure_node_array_large_enough_for_index(this_node_idx);
        dfa->set_node_edit_distance(this_node_idx, match_edit_distance(state));
        auto t = transitions(state);
        for (uint32_t out_c : t.u32_chars()) {
            auto new_state = step(state, out_c);
            auto out_node = exp.node_of(new_state);
            dfa->add_outgoing_edge(this_node_idx, out_node->second.first, out_c);
            to_explore.push(std::move(new_state));
        }
        auto wildcard_state = step(state, WILDCARD);
        if (can_match(wildcard_state)) {
            auto out_node = exp.node_of(wildcard_state);
            dfa->set_wildcard_edge(this_node_idx, out_node->second.first);
            to_explore.push(std::move(wildcard_state));
        } // else: don't bother
    }
    return LevenshteinDfa(std::move(dfa));
}

} // anon ns

template <typename Traits>
LevenshteinDfa ExplicitLevenshteinDfaBuilder<Traits>::build_dfa() const {
    ExplicitLevenshteinDfaBuilderImpl<Traits> builder(_u32_str_buf, _is_cased);
    return builder.build_dfa();
}

}
