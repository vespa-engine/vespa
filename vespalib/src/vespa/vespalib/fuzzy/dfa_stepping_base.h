// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sparse_state.h"
#include <span>

namespace vespalib::fuzzy {

template <typename Traits>
struct DfaSteppingBase {
    using StateType       = typename Traits::StateType;
    using TransitionsType = typename Traits::TransitionsType;

    std::span<const uint32_t> _u32_str; // TODO std::u32string_view

    DfaSteppingBase(std::span<const uint32_t> str) noexcept
        : _u32_str(str)
    {
    }

    [[nodiscard]] static constexpr uint8_t max_edits() noexcept {
        return Traits::max_edits();
    }

    /**
     * Returns the initial state of the DFA. This represents the first row in the
     * Levenshtein matrix.
     */
    [[nodiscard]] StateType start() const {
        StateType ret;
        const auto j = std::min(static_cast<uint32_t>(max_edits()),
                                static_cast<uint32_t>(_u32_str.size())); // e.g. the empty string as target
        for (uint32_t i = 0; i <= j; ++i) {
            ret.append(i, i);
        }
        return ret;
    }

    /**
     * DFA stepping function that takes an input (sparse) state and a 32-bit character value
     * (does not have to be valid UTF-32, but usually is) and generates a resulting state
     * that represents applying the Levenshtein algorithm on a particular matrix row using
     * the provided source string character.
     *
     * The returned state only includes elements where the edit distance (cost) is within
     * the maximum number of edits. All other elements are implicitly beyond the max
     * edit distance. It doesn't matter _how_ far beyond they are, since we have a fixed
     * maximum to consider.
     *
     * Stepping a non-matching state S (can_match(S) == false) results in another non-
     * matching state.
     *
     * As an example, this is a visualization of stepping through all source characters of
     * the string "fxod" when matching the target string "food" with max edits k=1.
     * Note: the actual internal representation is logical <column#, cost> tuples, but
     * rendering as a matrix makes things easier to understand. Elements _not_ part of the
     * state are rendered as '-'.
     *
     *             f o o d
     * start(): [0 1 - - -]
     * 'f':     [1 0 1 - -]
     * 'x':     [- 1 1 - -]
     * 'o':     [- - 1 1 -]
     * 'd':     [- - - - 1]
     *
     * In this case, the resulting edit distance is 1, with one substitution 'x' -> 'o'.
     *
     * If we pull out our trusty pen & paper and do the full matrix calculations, we see
     * that the above is equivalent to the full matrix with all costs > k pruned away:
     *
     *             f o o d
     *          [0 1 2 3 4]
     *        f [1 0 1 2 3]
     *        x [2 1 1 2 3]
     *        o [3 2 1 1 2]
     *        d [4 3 2 2 1]
     *
     * Since we're working on sparse states, stepping requires a bit of manual edge case
     * handling when compared to a dense representation.
     *
     * We first have to handle the case where our state includes the 0th matrix column.
     * In an explicit Levenshtein matrix of target string length n, source string length m,
     * the first column is always the values [0, m], increasing with 1 per row (the first
     * _row_ is handled by start()).
     *
     * To mirror this, if our sparse state includes column 0 we have to increment it by 1,
     * unless doing so would bring the cost beyond our max number of edits, in which case
     * we don't bother including the column in the new state at all. These correspond to
     * the start() -> 'f' -> 'x' transitions in the example above.
     *
     * What remains is then to do the actual Levenshtein insert/delete/substitute formula
     * for matching positions in the matrix. Let d represent the logical (full) Levenshtein
     * distance matrix and cell d[i, j] be the minimum number of edits between source string
     * character at i+1 and target string character at j+1:
     *
     * Insertion cost:    d[i, j-1]   + 1
     * Deletion cost:     d[i-1, j]   + 1
     * Substitution cost: d[i-1, j-1] + (s[i-1] == t[j-1] ? 1 :0)
     *
     * d[i, j] = min(Insertion cost, Deletion cost, Substitution cost)
     *
     * We have to turn this slightly on the head, as instead of going through a matrix row
     * and "pulling" values from the previous row, we have to go through a state representing
     * the previous row and "push" new values instead (iff these values are within max edits).
     * This also means we compute costs for indexes offset by 1 from the source state index
     * (can be visualized as the element one down diagonally to the right).
     *
     * Insertion considers the current row only, i.e. the state being generated. We always
     * work left to right in column order, so we can check if the last element (if any)
     * in our _new_ sparse state is equal to the index of our source state element. If not,
     * we know that it was beyond max edits. Max edits + 1 is inherently beyond max edits
     * and need not be included.
     *
     * Deletion considers the cell directly above our own, which is part of the input state
     * if it exists. Since we're computing the costs of cells at index + 1, we know that the
     * only way for this cell to be present in the state is if the _next_ element of our
     * input state exists and has an index equal to index + 1. If so, the deletion cost is
     * the cost recorded for this element + 1.
     *
     * Substitution considers the cell diagonally up to the left. This very conveniently
     * happens to be the input state cell we're currently working from, so it's therefore
     * always present.
     *
     * Example stepping with c='x', max edits k=1:
     *
     * ====== Initially ======
     *
     *              f o o d
     * state_in: [1 0 1 - -]  (0:1, 1:0, 2:1)
     * out:      []           ()
     *
     * We have a 0th column in state_in, but incrementing it results in 2 > k, so not
     * appended to out.
     *
     * ====== State (0:1), computing for index 1 ======
     *
     * Insertion:    out state is empty (no cell to our left), so implicit insertion cost
     *               is > k
     * Deletion:     state_in[1] is (1:0), which means it represents the cell just above
     *               index 1. Deletion cost is therefore 0+1 = 1
     * Substitution: (t[0] = 'f') != (c = 'x'), so substitution cost is 1+1 = 2
     *
     * Min cost is 1, which is <= k. Appending to output.
     *
     * out: [- 1] (1:1)
     *
     * ====== State (1:0), computing for index 2 ======
     *
     * Insertion:    last element in out has index 1 (cell to our immediate left) with cost
     *               1, so insertion cost is 1+1 = 2
     * Deletion:     state_in[2] is (2:1), which means it represents the cell just above
     *               index 2. Deletion cost is therefore 1+1 = 2
     * Substitution: (t[1] = 'o') != (c = 'x'), so substitution cost is 0+1 = 1
     *
     * Min cost is 1, which is <= k. Appending to output.
     *
     * out: [- 1 1] (1:1, 2:1)
     *
     * ====== State (2:1), computing for index 3 ======
     *
     * Insertion:    last element in out has index 2 (cell to our immediate left) with cost
     *               1, so insertion cost is 1+1 = 2
     * Deletion:     state_in[3] does not exist, so implicit deletion cost is > k
     * Substitution: (t[2] = 'o') != (c = 'x'), so substitution cost is 1+1 = 2
     *
     * Min cost is 2, which is > k. Not appending to output.
     *
     * Resulting output state (right-padded for clarity):
     *
     *   [- 1 1 - -] (1:1, 2:1)
     *
     */
    [[nodiscard]] StateType step(const StateType& state_in, uint32_t c) const {
        if (state_in.empty()) {
            return state_in; // A non-matching state can only step to another equally non-matching state
        }
        StateType new_state;
        if ((state_in.index(0) == 0) && (state_in.cost(0) < max_edits())) {
            new_state.append(0, state_in.cost(0) + 1);
        }
        for (uint32_t i = 0; i < state_in.size(); ++i) {
            const auto idx = state_in.index(i);
            if (idx == _u32_str.size()) [[unlikely]] {
                break; // Can't process beyond matrix width
            }
            const uint8_t sub_cost = (_u32_str[idx] == c) ? 0 : 1;
            // For our Levenshtein insert/delete/sub ops, we know that if a particular index is _not_
            // in the sparse state, its implicit distance is beyond the max edits, and need not be
            // considered.
            auto dist = state_in.cost(i) + sub_cost; // (Substitution)
            if (!new_state.empty() && (new_state.last_index() == idx)) { // (Insertion) anything to our immediate left?
                dist = std::min(dist, new_state.last_cost() + 1);
            }
            if ((i < state_in.size() - 1) && (state_in.index(i + 1) == idx + 1)) { // (Deletion) anything immediately above?
                dist = std::min(dist, state_in.cost(i + 1) + 1);
            }
            if (dist <= max_edits()) {
                new_state.append(idx + 1, dist);
            }
        }
        return new_state;
    }

    /**
     * Simplified version of step() which does not assemble a new state, but only checks
     * whether _any_ mismatching character can be substituted in and still result in a
     * potentially matching state. This is the case if the resulting state would contain
     * _at least one_ entry (recalling that we only retain entries that are within the
     * max number of edits).
     *
     * Consider using this directly instead of `can_match(step(state, WILDCARD))`,
     * which has the exact same semantics, but requires computing the full (sparse)
     * state before checking if it has any element at all. can_wildcard_step() just
     * jumps straight to the last part.
     */
    [[nodiscard]] bool can_wildcard_step(const StateType& state_in) const noexcept {
        if (state_in.empty()) {
            return false; // by definition
        }
        if ((state_in.index(0) == 0) && (state_in.cost(0) < max_edits())) {
            return true;
        }
        for (uint32_t i = 0; i < state_in.size(); ++i) {
            const auto idx = state_in.index(i);
            if (idx == _u32_str.size()) [[unlikely]] {
                break;
            }
            const uint8_t sub_cost = 1; // by definition
            auto dist = state_in.cost(i) + sub_cost;
            // Insertion only looks at the entries already computed in the current row
            // and always increases the cost by 1. Since we always bail out immediately if
            // there would have been at least one entry within max edits, we transitively
            // know that since we have not bailed out yet there is no way we can get here
            // and have insertion actually yield a match. So skip computing it entirely.
            if ((i < state_in.size() - 1) && (state_in.index(i + 1) == idx + 1)) {
                dist = std::min(dist, state_in.cost(i + 1) + 1);
            }
            if (dist <= max_edits()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given state represents a terminal state within the max number of edits
     */
    [[nodiscard]] bool is_match(const StateType& state) const noexcept {
        // If the last index is equal to the string's length, it means we were able to match
        // the entire string and still be within the max edit distance.
        return (!state.empty() && state.last_index() == static_cast<uint32_t>(_u32_str.size()));
    }

    /**
     * Iff the input state represents a terminal matching state, returns the number of
     * edits required to reach the state. Otherwise, returns max edits + 1.
     */
    [[nodiscard]] uint8_t match_edit_distance(const StateType& state) const noexcept {
        if (!is_match(state)) {
            return max_edits() + 1;
        }
        return state.last_cost();
    }

    /**
     * Returns whether the given state _may_ end up matching the target string,
     * depending on the remaining source string characters.
     *
     * Note: is_match(s)  => can_match(s) is true, but
     *       can_match(s) => is_match(s)  is false
     */
    [[nodiscard]] bool can_match(const StateType& state) const noexcept {
        // The presence of any entries at all indicates that we may still potentially match
        // the target string if the remaining input is within the maximum number of edits.
        return !state.empty();
    }

    /**
     * All valid character transitions from this state are those that are reachable
     * within the max edit distance.
     */
    TransitionsType transitions(const StateType& state) const {
        TransitionsType t;
        for (size_t i = 0; i < state.size(); ++i) {
            const auto idx = state.index(i);
            if (idx < _u32_str.size()) [[likely]] {
                t.add_char(_u32_str[idx]);
            }
        }
        // We must ensure transitions are in increasing character order, so that the
        // lowest possible higher char than any candidate char can be found with a
        // simple "first-fit" linear scan.
        t.sort();
        return t;
    }

};

}
