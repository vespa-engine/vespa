// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dfa_matcher.h"
#include "levenshtein_dfa.h"
#include "unicode_utils.h"
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <cassert>
#include <concepts>

namespace vespalib::fuzzy {

/**
 * Implementation of algorithm for linear-time k-max edits string matching and successor
 * string generation over an abstract DFA representation.
 *
 * The implementation is agnostic to how the underlying DFA is implemented, but requires
 * an appropriate adapter that satisfies the DfaMatcher concept contracts.
 */
template <uint8_t MaxEdits>
struct MatchAlgorithm {
    using MatchResult = LevenshteinDfa::MatchResult;

    static constexpr uint8_t max_edits() noexcept { return MaxEdits; }

    /**
     * Matches UTF-8 source string `source` against the target DFA, optionally generating
     * the successor string iff the source string is not within the maximum number of edits
     * of the target string.
     *
     * The actual match loop is very simple: we try to match the DFA as far as we can
     * before either consuming all input (source string) characters or ending up in a non-
     * matching state before we have consumed all input. In the former case, we may be in
     * a matching state (consider matching "foo" with the target string "food"; after
     * consuming all input we'll be in a matching state with 1 edit). In the latter case,
     * the input string cannot possible match.
     *
     * If we end up in a matching state, all is well. We simply return a MatchResult with
     * the number of edits the state represents.
     *
     * The interesting bit happens the string does _not_ match and we are asked to provide a
     * _successor_ string that _does_ match and is strictly greater in lexicographic order.
     *
     * We lean on some core invariants:
     *
     *  - The m x n (|source| x |target|) Levenshtein matrix provides, for any m[i, j] with
     *    i in [1, m], j in [1, n], the _minimum possible_ number of edits that can transform
     *    the source string prefix of length `i` to the target string prefix of length `j`.
     *    This means there is no way of transforming said source prefix using _fewer_ edits.
     *
     *  - Any given DFA state corresponds to a unique row in the Levenshtein matrix, thus
     *    transitively inheriting the invariants of the matrix row elements themselves, such
     *    as representing the minimum number of edits.
     *
     * We have two mismatch cases:
     *
     * 1. We've matched the entire source string without ending in an accepting state.
     *
     * This can only happen if the input is a (possibly edited) prefix of the target string.
     * Any and all _longer_ strings with this prefix is inherently lexicographically greater,
     * so we emit the smallest possible suffix that turns prefix || suffix into a matching
     * string.
     *
     * See emit_smallest_matching_suffix() for details.
     *
     * 2. We've matched a prefix of the source string without ending in an accepting state.
     *
     * This case is trickier than when the entire source string is a prefix, as we cannot
     * just emit a suffix to the source to create a matching, lexicographically greater string.
     *
     * Consider source "foxx" and target "food". There exists no suffix S in "food" that can
     * turn "foxx" || S into a matching string within k=1 edits.
     *
     * So we have to backtrack to somewhere.
     *
     * That "somewhere" is the state that maximizes the size of the source prefix while
     * allowing us to emit a greater suffix.
     *
     * For each state we visit, we check if there exists at least one higher out edge than the
     * one taken out from that state (this is possibly a wildcard edge). If one exists, we
     * copy the state to `last_state_with_higher_out` and remember the state's source string
     * prefix as well as the source string character that transitions us away from the state
     * (this will be our candidate for building a greater suffix).
     *
     * When we fail to match the entire source string, we know that last_state_with_higher_out
     * represents the last possible branching point (and therefore the longest prefix) where
     * we can substitute in or insert a higher character, in turn creating a greater suffix.
     *
     * Proof by contradiction: let `last_state_with_higher_out` be S and assume there exists
     * a state S' that has a greater source string prefix than S while still allowing for
     * emitting a lexicographically greater suffix that is within max edits k. We terminate
     * the match loop once can_match(X) is false for any state X, where X subsumes S by
     * definition. For S' to exist, it must be possible for a transition to exist from X to
     * a later state that can have a higher out edge. However, edit distance costs can
     * never decrease, only stay constant (with matching substitutions) or increase (with
     * insertions, deletions or non-matching substitutions), so it's impossible to follow
     * an out-edge from X to any later potentially matching state. Thus, S' can not exist
     * and we have a contradiction.
     *
     * Since we want to generate the smallest possible larger string that matches, we ideally
     * want to emit a character that is +1 of the source character after the shared prefix.
     * This is using the "higher out"-character we remembered earlier. We do this if we have
     * a wildcard out edge (or if there exists an explicit out-edge for value char+1).
     * Otherwise, we have to follow the highest explicitly present out-edge.
     *
     * Once we have emitted one single character that gets us lexicographically higher than
     * the source string, we then emit the smallest possible suffix to this. This uses the
     * same minimal suffix generation logic as mismatch case 1).
     *
     * See `backtrack_and_emit_greater_suffix()` for details.
     *
     * Example:
     * (This is easiest to follow by looking at examples/food_dfa.svg)
     *
     * Source "foxx", target "food" and k=1:
     *
     * After matching "fo" with 0 edits we reach a state with out-edges {d, o, *}. This state
     * has an implicitly higher out-edge (*) and we remember it and the char 'x' for later.
     * Edge 'x' can only happen via *, so we take that path.
     *
     * After matching "fox" with 1 edit we reach a state with out-edges {d, o}. There is
     * no out-edge for 'x' and the state is not a matching state, so we need to backtrack
     * and generate a successor.
     *
     * We backtrack to the state representing "fo" and emit it as a successor prefix. We
     * observe that this state has a wildcard out-edge and emit 'x'+1 == 'y' to the successor
     * string and continue with emitting the smallest suffix. We now have a successor
     * prefix of "foy", with which we reach the same logical state as we did with "fox"
     * previously. The smallest out-edge here is 'd', so we take it. This leaves us in an
     * accepting (matching) state, so suffix generation completes.
     *
     *   "foxx" -> "foyd"
     *
     * Note that it's possible for the prefix to be empty, which results in a successor
     * that has nothing in common with the source altogether.
     *   Example: "gp" -> "hfood" (+1 char value case)
     *
     * Note for cased vs. uncased matching: when uncased matching is specified, we always
     * match "as if" both the target and source strings are lowercased. This means that
     * successor strings are generated based on this form, _not_ on the original form.
     * Example: uncased matching for target "food" with input "FOXX". This generates the
     * successor "foyd" (and _not_ "FOyd"), as the latter would imply a completely different
     * ordering when compared byte-wise against an implicitly lowercased dictionary.
     *
     * TODO let matcher know if source string is pre-normalized (i.e. lowercased).
     */
    template <DfaMatcher Matcher, typename SuccessorT>
    static MatchResult match(const Matcher& matcher,
                             std::string_view source,
                             SuccessorT& successor_out)
    {
        using StateType = typename Matcher::StateType;
        Utf8Reader u8_reader(source.data(), source.size());
        uint32_t n_prefix_chars    = static_cast<uint32_t>(successor_out.size()); // Don't touch any existing prefix
        uint32_t char_after_prefix = 0;
        StateType last_state_with_higher_out = StateType{};

        StateType state = matcher.start();
        while (u8_reader.hasMore()) {
            const auto pos_before_char = static_cast<uint32_t>(successor_out.size());
            const uint32_t raw_mch     = u8_reader.getChar();
            const uint32_t mch         = normalized_match_char(raw_mch, matcher.is_cased());
            append_utf32_char(successor_out, mch);
            if (matcher.has_higher_out_edge(state, mch)) {
                last_state_with_higher_out = state;
                n_prefix_chars = pos_before_char;
                char_after_prefix = mch;
            }
            auto maybe_next = matcher.match_input(state, mch);
            if (matcher.can_match(maybe_next)) {
                state = maybe_next;
            } else {
                // Can never match; find the successor
                successor_out.resize(n_prefix_chars); // Always <= successor_out.size()
                assert(matcher.valid_state(last_state_with_higher_out));
                backtrack_and_emit_greater_suffix(matcher, last_state_with_higher_out,
                                                  char_after_prefix, successor_out);
                return MatchResult::make_mismatch(max_edits());
            }
        }
        const auto edits = matcher.match_edit_distance(state);
        if (edits <= max_edits()) {
            return MatchResult::make_match(max_edits(), edits);
        }
        // Successor prefix already filled, just need to emit the suffix
        emit_smallest_matching_suffix(matcher, state, successor_out);
        return MatchResult::make_mismatch(max_edits());
    }

    /**
     * Simplified match loop which does _not_ emit a successor on mismatch. Otherwise the
     * exact same semantics as the successor-emitting `match()` overload.
     */
    template <DfaMatcher Matcher>
    static MatchResult match(const Matcher& matcher, std::string_view source) {
        using StateType = typename Matcher::StateType;
        Utf8Reader u8_reader(source.data(), source.size());
        StateType state = matcher.start();
        while (u8_reader.hasMore()) {
            const uint32_t mch = normalized_match_char(u8_reader.getChar(), matcher.is_cased());
            auto maybe_next    = matcher.match_input(state, mch);
            if (matcher.can_match(maybe_next)) {
                state = maybe_next;
            } else {
                return MatchResult::make_mismatch(max_edits());
            }
        }
        const auto edits = matcher.match_edit_distance(state);
        if (edits <= max_edits()) {
            return MatchResult::make_match(max_edits(), edits);
        }
        return MatchResult::make_mismatch(max_edits());
    }

    /**
     * Instantly backtrack to the last possible branching point in the DFA where we can
     * choose some higher outgoing edge character value and still match the DFA. If the node
     * has a wildcard edge, we can bump the input char by one and generate the smallest
     * possible matching suffix to that. Otherwise, choose the smallest out edge that is
     * greater than the input character at that location and _then_ emit the smallest
     * matching prefix.
     *
     * precondition: `last_node_with_higher_out` has either a wildcard edge or a char match
     *    edge that compares greater than `input_at_branch`.
     */
    template <DfaMatcher Matcher, typename SuccessorT>
    static void backtrack_and_emit_greater_suffix(
            const Matcher& matcher,
            typename Matcher::StateParamType last_state_with_higher_out,
            const uint32_t input_at_branch,
            SuccessorT& successor)
    {
        auto wildcard_state = matcher.match_wildcard(last_state_with_higher_out);
        if (matcher.can_match(wildcard_state)) {
            // `input_at_branch` may be U+10FFFF, with +1 being outside legal Unicode _code point_
            // range but _within_ what UTF-8 can technically _encode_.
            // We assume that successor-consumers do not care about anything except byte-wise
            // ordering. This is similar to what RE2's PossibleMatchRange emits to represent a
            // UTF-8 upper bound, so not without precedent.
            // If the resulting character corresponds to an existing out-edge we _must_ take it
            // instead of the wildcard edge, or we'll end up in the wrong state.
            const auto next_char = input_at_branch + 1;
            if (!matcher.has_exact_explicit_out_edge(last_state_with_higher_out, next_char)) {
                append_utf32_char(successor, next_char);
                emit_smallest_matching_suffix(matcher, wildcard_state, successor);
                return;
            } // else: handle exact match below (it will be found as the first higher out edge)
        }
        const auto first_highest_edge = matcher.lowest_higher_explicit_out_edge(last_state_with_higher_out, input_at_branch);
        assert(matcher.valid_edge(first_highest_edge));
        append_utf32_char(successor, matcher.edge_to_u32char(first_highest_edge));
        emit_smallest_matching_suffix(matcher, matcher.edge_to_state(last_state_with_higher_out, first_highest_edge), successor);
    }

    /**
     * The smallest possible suffix is generated by following the smallest out-edge per state,
     * until we reach a state that is a match. It is possible that the smallest out edge is a
     * "wildcard" edge (our terminology), which means that we can insert/substitute an arbitrary
     * character and still have `can_match(resulting state)` be true. In this case we emit the
     * smallest possible non-null UTF-8 character (0x01).
     *
     * Examples:
     *   (These are easiest to follow by looking at examples/food_dfa.svg)
     *
     *   Source "fo", target "food" and k=1:
     *
     *   After matching "fo" we have 1 edit to spare. The smallest valid, non-empty UTF-8 suffix
     *   to this string must necessarily begin with 0x01, so that's what we emit. The smallest
     *   edge we can follow from the resulting state is 'd', and that is a accepting (matching)
     *   state.
     *
     *     "fo" -> "fo\x01d"
     *
     *   Source "fx", target "food" and k=1:
     *
     *   After matching "fx" we have no edits to spare. The smallest character reachable from
     *   the state is 'o' (in fact, it is the only out edge available since we're down to zero
     *   available edits). The next state has an out-edge to 'd' and 'o', and we choose 'd'
     *   since it is smallest. This leaves us in an accepting (matching) state and we terminate
     *   the loop.
     *
     *     "fx" -> "fxod"
     */
     // TODO consider variant for only emitting _prefix of suffix_ to avoid having to generate
     //  the full string? Won't generate a matching string, but will be lexicographically greater.
    template <DfaMatcher Matcher, typename SuccessorT>
    static void emit_smallest_matching_suffix(
            const Matcher& matcher,
            typename Matcher::StateParamType from,
            SuccessorT& str)
    {
        auto state = from;
        while (!matcher.is_match(state)) {
            // Optimization: if the only way for the remaining suffix to match is for it to be
            // exactly equal to the suffix of the target string, emit it directly instead of
            // stepping the state per character. This allows for matcher-specific shortcuts.
            if (matcher.implies_exact_match_suffix(state)) {
                matcher.emit_exact_match_suffix(state, str);
                return;
            }
            // If we can take a wildcard path, emit the smallest possible valid UTF-8 character (0x01).
            // Otherwise, find the smallest char that can eventually lead us to a match.
            auto wildcard_state = matcher.match_wildcard(state);
            if (matcher.can_match(wildcard_state)) {
                str.push_back(0x01);
                state = wildcard_state;
            } else {
                const auto smallest_out_edge = matcher.smallest_explicit_out_edge(state);
                assert(matcher.valid_edge(smallest_out_edge));
                append_utf32_char(str, matcher.edge_to_u32char(smallest_out_edge));
                state = matcher.edge_to_state(state, smallest_out_edge);
            }
        }
    }

    static uint32_t normalized_match_char(uint32_t in_ch, bool is_cased) noexcept {
        return (is_cased ? in_ch : LowerCase::convert(in_ch));
    }
};

}
