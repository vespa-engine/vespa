// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <concepts>
#include <cstdint>
#include <string>
#include <vector>

namespace vespalib::fuzzy {

// Concept that all DFA matcher implementations must satisfy
template <typename T>
concept DfaMatcher = requires(T a, std::string u8str, std::vector<uint32_t> u32str) {
    typename T::StateType;
    typename T::StateParamType;
    typename T::EdgeType;

    // Whether the matching is case-sensitive or not. If false, all source string code points will
    // be implicitly lower-cased prior to state stepping. For case-insensitive (i.e. uncased)
    // matching to have the expected semantics, the actual target string must be pre-lowercased.
    { a.is_cased() } -> std::same_as<bool>;

    // Initial (starting) state of the DFA
    { a.start() } -> std::same_as<typename T::StateType>;

    // Whether a given state constitutes a string match within the maximum number of edits
    { a.is_match(typename T::StateType{}) } -> std::same_as<bool>;

    // Whether a given state _may_ result in a match, either in the given state or in the
    // future if the remaining string input is within the max edit distance
    { a.can_match(typename T::StateType{}) } -> std::same_as<bool>;

    // Whether the given state is a valid state. Used for invariant checking.
    { a.valid_state(typename T::StateType{}) } -> std::same_as<bool>;

    // Iff the given state represents a terminal matching state, returns the number of
    // edits required to reach the state. Otherwise, returns max edits + 1.
    { a.match_edit_distance(typename T::StateType{}) } -> std::same_as<uint8_t>;

    // Returns the state that is the result of matching the single logical Levenshtein
    // matrix row represented by the given state with the input u32 character value.
    { a.match_input(typename T::StateType{}, uint32_t{}) } -> std::same_as<typename T::StateType>;

    // Returns the state that is the result of matching the single logical Levenshtein
    // matrix row represented by the given state with a sentinel character that cannot
    // match any character in the target string (i.e. is always a mismatch).
    { a.match_wildcard(typename T::StateType{}) } -> std::same_as<typename T::StateType>;

    // Whether there exists an out edge from the given state that can accept a
    // _higher_ UTF-32 code point value (character) than the input u32 value. Such an
    // edge _may_ be a wildcard edge, which accepts any character.
    { a.has_higher_out_edge(typename T::StateType{}, uint32_t{}) } -> std::same_as<bool>;

    // Whether there exists an out edge from the given state whose u32 character value
    // _exactly_ matches the input u32 value.
    { a.has_exact_explicit_out_edge(typename T::StateType{}, uint32_t{}) } -> std::same_as<bool>;

    // Returns the out edge `e` from the given state that satisfies _both_:
    //   1. higher than the given u32 value
    //   2. no other out edges are lower than `e`
    // Only called in a context where the caller already knows that such an edge must exist.
    { a.lowest_higher_explicit_out_edge(typename T::StateType{}, uint32_t{}) } -> std::same_as<typename T::EdgeType>;

    // Returns the out edge from the given state that has the lowest character value
    { a.smallest_explicit_out_edge(typename T::StateType{}) } -> std::same_as<typename T::EdgeType>;

    // Whether the given edge is a valid edge. Used for invariant checking.
    { a.valid_edge(typename T::EdgeType{}) } -> std::same_as<bool>;

    // For a given edge, returns the UTF-32 code point value the edge represents
    { a.edge_to_u32char(typename T::EdgeType{}) } -> std::same_as<uint32_t>;

    // Returns the state that is the result of following the given edge from the given state.
    { a.edge_to_state(typename T::StateType{}, typename T::EdgeType{}) } -> std::same_as<typename T::StateType>;

    // Returns true iff the only way for the remaining input string to match the target string
    // is for each subsequent character to match exactly. More precisely, this means that it is
    // not possible to perform any more edits on the string. This will be the case if the
    // current row of the Levenshtein matrix contains only 1 entry <= max_edits, and its cost
    // is equal to max_edits.
    // It is OK for an implementation to always return false. In this case, a slower code path
    // (per-state stepping and character output) will be used for emitting the suffix.
    { a.implies_exact_match_suffix(typename T::StateType{}) } -> std::same_as<bool>;

    // Verbatim emit a suffix of the target string that will turn the prefix represented
    // by the input state concatenated with the suffix into a matching string.
    // Precondition: implies_match_suffix(state) == true, i.e. the state is guaranteed to
    //               afford no edits anywhere.
    { a.emit_exact_match_suffix(typename T::StateType{}, u8str) } -> std::same_as<void>;

    // Same as above, but for raw UTF-32 code point output
    { a.emit_exact_match_suffix(typename T::StateType{}, u32str) } -> std::same_as<void>;
};

}
