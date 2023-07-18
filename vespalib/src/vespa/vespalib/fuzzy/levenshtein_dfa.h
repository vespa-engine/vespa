// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <iosfwd>
#include <memory>
#include <string>
#include <string_view>

namespace vespalib::fuzzy {

/**
 * Levenshtein Deterministic Finite Automata (DFA)
 *
 * The Levenshtein distance (or edit distance) is the minimum number of edits (additions,
 * deletions or substitutions) needed to transform a particular source string s to a
 * particular target string t.
 *
 * Let m be the length of the source string and n be the length of the target string.
 *
 * The classic dynamic programming algorithm uses a n x m cost matrix and is therefore
 * O(nm) in space and time. By observing that only 2 rows of the matrix are actually
 * needed, this is commonly reduced to O(n) space complexity (still O(nm) time complexity).
 * When the maximum number of allowed edits is constrained to k, some clever observations
 * about the nature of the cost matrix allows for reducing the time complexity down to
 * O(kn) (more specifically, O((2k+1) * n)). When k is fixed (e.g. k in {1, 2}), the
 * time complexity simplifies down to O(n).
 *
 * This implements code for building and evaluating Levenshtein Deterministic Finite
 * Automata, where the resulting DFA efficiently matches all possible source strings that
 * can be transformed to the target string within k max edits. This allows for easy linear
 * matching of strings.
 *
 * Inspiration:
 *   - http://blog.notdot.net/2010/07/Damn-Cool-Algorithms-Levenshtein-Automata
 *   - https://julesjacobs.com/2015/06/17/disqus-levenshtein-simple-and-fast.html
 *
 * The latter in particular was a close inspiration for the sparse DFA state management.
 *
 * ====== Dictionary skipping via successor string generation ======
 *
 * Scanning for edit distance matches frequently takes place against a sorted dictionary.
 * When matching using a DFA, in the case where the source string does _not_ match, we can
 * generate the _successor_ string; the next matching string that is lexicographically
 * _greater_ than the source string. This string has the invariant that there are no
 * possibly matching strings within k edits ordered after the source string but before
 * the successor.
 *
 * This lets us do possibly massive leaps forward in the dictionary, turning a dictionary
 * scan into a sublinear operation.
 *
 * Note that the implemented successor algorithm is slightly different from that described
 * in the above blog post. The implemented algorithm requires zero extra data structures
 * than the DFA itself and the target string and tries to be extra clever with reducing
 * the number of code point conversions required
 *
 * ====== Unicode support ======
 *
 * Matching and successor generation is fully Unicode-aware. All input strings are expected
 * to be in UTF-8, and the generated successor is also encoded as UTF-8 (with some caveats;
 * see the documentation for match()).
 *
 * Internally, matching is done on UTF-32 code points and the DFA itself is built around
 * UTF-32. This is unlike Lucene, which converts a UTF-32 DFA to an equivalent UTF-8 DFA.
 *
 * ====== Memory usage ======
 *
 * There is always a baseline DFA memory usage O(n) in the target string, as the
 * underlying DFA needs to convert the input UTF-8 string to explicit UTF-32 chars.
 *
 * Aside from the baseline, memory usage depends on whether an explicit or implicit DFA
 * is used.
 *
 * ------ Explicit DFA ------
 *
 * The explicit DFA graph takes up quite a bit more memory than the original string
 * representation (one reason is the use of UTF-32 characters under the hood).
 *
 * Expected upper bound memory usage for a string of length n with max edits k is
 *
 *   (2k+1) * N(k) * n * W(k)
 *
 * where N(1) is expected to be 32 and N(2) is 48, W(1) is 1.34 and W(2) is 3.2 (empirically
 * derived).
 *
 * Memory usage during building is higher due to keeping track of the set of generated
 * states in a hash table, but still linear in input size. This extra memory is freed
 * once building is complete.
 *
 * ------ Implicit DFA ------
 *
 * Implicit DFAs have a O(1) memory usage during evaluation, which all lives on the stack
 * or in registers (this does not include the successor string, which is provided by the
 * caller).
 *
 * Since the sparse state stepping is currently not as fast as explicit DFA node traversal,
 * string matching is slower than with the explicit DFA.
 *
 * ====== In short ======
 *
 *  - Immutable; build once, run many times.
 *  - Explicit DFA build time is amortized linear in target string size.
 *  - Implicit DFA build time is O(1) (aside from initial UTF-32 conversion)
 *  - Zero-allocation matching.
 *  - Matching takes in raw UTF-8 input, no need to pre-convert.
 *    - Streaming UTF-8 to UTF-32 conversion; fully unicode-aware (DFA uses UTF-32 code
 *      points internally).
 *    - If required, it's possible (but not currently implemented) to bake case
 *      insensitive matching semantics into the generated DFA itself.
 *  - Allows for dictionary forward-skipping via successor algorithm.
 *  - Amortized zero allocations for successor string building when reusing string
 *    between matches.
 *  - Successor string is generated in-place as UTF-8 and can be directly used as input
 *    to a byte-wise dictionary seek.
 */
class LevenshteinDfa {
public:
    class MatchResult {
        uint8_t _max_edits;
        uint8_t _edits;
    public:
        constexpr MatchResult(uint8_t max_edits, uint8_t edits) noexcept
            : _max_edits(max_edits),
              _edits(edits)
        {}

        static constexpr MatchResult make_match(uint8_t max_edits, uint8_t edits) noexcept {
            return {max_edits, edits};
        }

        static constexpr MatchResult make_mismatch(uint8_t max_edits) noexcept {
            return {max_edits, static_cast<uint8_t>(max_edits + 1)};
        }

        [[nodiscard]] constexpr bool matches() const noexcept { return _edits <= _max_edits; }
        [[nodiscard]] constexpr uint8_t edits() const noexcept { return _edits; }
        [[nodiscard]] constexpr uint8_t max_edits() const noexcept { return _max_edits; }
    };

    struct Impl {
        virtual ~Impl() = default;
        [[nodiscard]] virtual MatchResult match(std::string_view u8str, std::string* successor_out) const = 0;
        [[nodiscard]] virtual size_t memory_usage() const noexcept = 0;
        virtual void dump_as_graphviz(std::ostream& out) const = 0;
    };

private:
    std::unique_ptr<Impl> _impl;
public:
    explicit LevenshteinDfa(std::unique_ptr<Impl> impl) noexcept;
    LevenshteinDfa(LevenshteinDfa&&) noexcept;
    LevenshteinDfa& operator=(LevenshteinDfa&&) noexcept;
    LevenshteinDfa(const LevenshteinDfa&) = delete;
    LevenshteinDfa& operator=(const LevenshteinDfa&) = delete;
    ~LevenshteinDfa();

    /**
     * Attempts to match the source string `source` with the target string this DFA was
     * built with, emitting a successor string on mismatch if `successor_out` != nullptr.
     *
     * `source` must not contain any null UTF-8 chars.
     *
     * Match case:
     * Iff `source` is _within_ the maximum edit distance, returns a MatchResult with
     * matches() == true and edits() == the actual edit distance. If `successor_out`
     * is not nullptr, the string pointed to is _not_ modified.
     *
     * Mismatch case:
     * Iff `source` is _beyond_ the maximum edit distance, returns a MatchResult with
     * matches() == false.
     *
     * Iff `successor_out` is not nullptr, the following holds:
     *   - `successor_out` is modified to contain the next (in byte-wise ordering) possible
     *     _matching_ string S so that there exists no other matching string S' that is
     *     greater than `source` but smaller than S.
     *   - `successor_out` contains UTF-8 bytes that are within what UTF-8 can legally
     *     encode in bitwise form, but the _code points_ they encode may not be valid.
     *     In particular, surrogate pair ranges and U+10FFFF+1 may be encoded, neither of
     *     which are valid UTF-8.
     *
     * It is expected that the consumer of `successor_out` is only interested in the
     * memcmp()-ordering of strings and not whether they are technically valid Unicode.
     * This should be the case for low-level dictionary data structures etc.
     *
     * Memory allocation:
     * This function does not directly or indirectly allocate any heap memory if either:
     *
     *   - the input string is within the max edit distance, or
     *   - `successor_out` is nullptr, or
     *   - `successor_out` has sufficient capacity to hold the generated successor
     *
     * By reusing the successor string across many calls, this therefore amortizes memory
     * allocations down to near zero per invocation.
     */
    [[nodiscard]] MatchResult match(std::string_view source, std::string* successor_out) const;

    /**
     * Returns how much memory is used by the underlying DFA representation, in bytes.
     */
    [[nodiscard]] size_t memory_usage() const noexcept;

    enum class DfaType {
        Implicit,
        Explicit
    };

    /**
     * Builds and returns a Levenshtein DFA that matches all strings within `max_edits`
     * edits of `target_string`. The type of DFA returned is specified by dfa_type.
     *
     * `max_edits` must be in {1, 2}. Throws std::invalid_argument if outside range.
     *
     * `target_string` must not contain any null UTF-8 chars.
     */
    [[nodiscard]] static LevenshteinDfa build(std::string_view target_string,
                                              uint8_t max_edits,
                                              DfaType dfa_type);

    /**
     * Same as build() but currently always returns an implicit DFA.
     */
    [[nodiscard]] static LevenshteinDfa build(std::string_view target_string, uint8_t max_edits);

    /**
     * Dumps the DFA as a Graphviz graph in text format to the provided output stream.
     *
     * Note: Only supported for _explicit_ DFAs. Trying to call this function on an implicit
     * DFA will throw a std::runtime_error, as there is no concrete underlying graph
     * structure to dump.
     *
     * Note that only _matching_ state transitions are present in the DFA, and therefore only
     * such transitions are present in the generated graph. Overall this makes the graph for
     * longer strings much more manageable, as the number of out-edges from a particular depth
     * in the graph depends on the max number of edits and not on the length of the string
     * itself. Otherwise, you'd have a whole bunch of nodes with out-edges to the same terminal
     * non-matching state node.
     */
    void dump_as_graphviz(std::ostream& out) const;
};

std::ostream& operator<<(std::ostream& os, const LevenshteinDfa::MatchResult& mos);
std::ostream& operator<<(std::ostream& os, const LevenshteinDfa::DfaType& dt);

}
