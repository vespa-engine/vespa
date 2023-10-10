// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dfa_stepping_base.h"
#include "levenshtein_dfa.h"
#include "sparse_state.h"
#include "unicode_utils.h"
#include <vector>

namespace vespalib::fuzzy {

// A doomed state is one that cannot possibly match the target string
constexpr const uint32_t DOOMED = UINT32_MAX;

template <uint8_t MaxEdits>
struct DfaNode {
    static constexpr uint8_t MaxCharOutEdges = diag(MaxEdits); // Not counting wildcard edge

    struct Edge {
        uint32_t u32ch;
        uint32_t node;
    };

    std::array<Edge, MaxCharOutEdges> match_out_edges_buf;
    uint32_t wildcard_edge_to = DOOMED;
    uint8_t num_match_out_edges = 0;
    uint8_t edits = UINT8_MAX;

    [[nodiscard]] bool has_wildcard_edge() const noexcept {
        return wildcard_edge_to != DOOMED;
    }

    [[nodiscard]] uint32_t wildcard_edge_to_or_doomed() const noexcept {
        return wildcard_edge_to;
    }

    [[nodiscard]] std::span<const Edge> match_out_edges() const noexcept {
        return std::span(match_out_edges_buf.begin(), num_match_out_edges);
    }

    [[nodiscard]] uint32_t match_or_doomed(uint32_t ch) const noexcept {
        // Always prefer the exact matching edges
        for (const auto& e : match_out_edges()) {
            if (e.u32ch == ch) {
                return e.node;
            }
        }
        // Fallback to wildcard edge if possible (could be doomed)
        return wildcard_edge_to;
    }

    [[nodiscard]] bool has_exact_match(uint32_t ch) const noexcept {
        for (const auto& e : match_out_edges()) {
            if (e.u32ch == ch) {
                return true;
            }
        }
        return false;
    }

    [[nodiscard]] size_t has_higher_out_edge(uint32_t ch) const noexcept {
        if (has_wildcard_edge()) {
            return true; // implicitly possible to substitute a higher out edge char
        }
        return lowest_higher_explicit_out_edge(ch) != nullptr;
    }

    [[nodiscard]] const Edge* lowest_higher_explicit_out_edge(uint32_t ch) const noexcept {
        // Important: these _must_ be sorted in increasing code point order
        for (const auto& e : match_out_edges()) {
            if (e.u32ch > ch) {
                return &e;
            }
        }
        return nullptr;
    }

    void add_match_out_edge(uint32_t out_char, uint32_t out_node) noexcept {
        assert(num_match_out_edges < MaxCharOutEdges);
        match_out_edges_buf[num_match_out_edges] = Edge{out_char, out_node};
        ++num_match_out_edges;
    }

    void set_wildcard_out_edge(uint32_t out_node) noexcept {
        assert(wildcard_edge_to == DOOMED);
        wildcard_edge_to = out_node;
    }
};

template <uint8_t MaxEdits>
class ExplicitLevenshteinDfaImpl final : public LevenshteinDfa::Impl {
public:
    static_assert(MaxEdits > 0 && MaxEdits <= UINT8_MAX/2);

    using DfaNodeType = DfaNode<MaxEdits>;
    using MatchResult = LevenshteinDfa::MatchResult;
private:
    std::vector<DfaNodeType> _nodes;
    const bool               _is_cased;
public:
    explicit ExplicitLevenshteinDfaImpl(bool is_cased) noexcept
        : _is_cased(is_cased)
    {}
    ~ExplicitLevenshteinDfaImpl() override = default;

    static constexpr uint8_t max_edits() noexcept { return MaxEdits; }

    void ensure_node_array_large_enough_for_index(uint32_t node_index) {
        if (node_index >= _nodes.size()) {
            _nodes.resize(node_index + 1);
        }
    }

    void set_node_edit_distance(uint32_t node_index, uint8_t edits) {
        _nodes[node_index].edits = edits;
    }

    void add_outgoing_edge(uint32_t from_node_idx, uint32_t to_node_idx, uint32_t out_char) {
        _nodes[from_node_idx].add_match_out_edge(out_char, to_node_idx);
    }

    void set_wildcard_edge(uint32_t from_node_idx, uint32_t to_node_idx) {
        _nodes[from_node_idx].set_wildcard_out_edge(to_node_idx);
    }

    [[nodiscard]] MatchResult match(std::string_view u8str) const override;

    [[nodiscard]] MatchResult match(std::string_view u8str, std::string& successor_out) const override;

    [[nodiscard]] MatchResult match(std::string_view u8str, std::vector<uint32_t>& successor_out) const override;

    [[nodiscard]] size_t memory_usage() const noexcept override {
        return sizeof(DfaNodeType) * _nodes.size();
    }

    void dump_as_graphviz(std::ostream& os) const override;
};

template <typename Traits>
class ExplicitLevenshteinDfaBuilder {
    const std::vector<uint32_t> _u32_str_buf; // TODO std::u32string
    const bool                  _is_cased;
public:
    ExplicitLevenshteinDfaBuilder(std::vector<uint32_t> str, bool is_cased) noexcept
        : _u32_str_buf(std::move(str)),
          _is_cased(is_cased)
    {}

    [[nodiscard]] LevenshteinDfa build_dfa() const;
};

}
