// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "levenshtein_dfa.h"
#include <vector>

namespace vespalib::fuzzy {

/**
 * This implementation is based on the paper 'Fast string correction
 * with Levenshtein automata' from 2002 by Klaus U. Schulz and Stoyan
 * Mihov.
 *
 * Given the maximal distance N, a generic parameterized transition
 * table is calculated up-front. When a specific word is given, a
 * simple lookup structure is created to enumerate the possible
 * characteristic vectors for each position in the given
 * word. Together, these structures can be used to simulate the
 * traversal of a hypothetical Levenshtein dfa that will never be
 * created.
 *
 * Approaching the end of the word is handled by padding the
 * characteristic vectors with 0 bits for everything after the word
 * ends. In addition, a unit test verifies that there is no possible
 * sequence of events that leads to the minimal boundary of the state
 * exceeding the boundary of the word itself. This means that the
 * simulated dfa can be stepped freely without checking for word size.
 **/
template <uint8_t N>
class TableDfa final : public LevenshteinDfa::Impl
{
public:
    // characteristic vector for a specific input value indicating how
    // it matches the window starting at the minimal boundary.
    struct CV {
        uint32_t input;
        uint32_t match;
        CV() noexcept : input(0), match(0) {}
    };
    static constexpr size_t window_size() { return 2 * N + 1; }
    struct Lookup {
        std::array<CV, window_size()> list;
        Lookup() noexcept : list() {}
    };
    
private:
    const std::vector<Lookup> _lookup;
    const bool                _is_cased;

    static std::vector<Lookup> make_lookup(const std::vector<uint32_t> &str);
    
public:
    using MatchResult = LevenshteinDfa::MatchResult;
    TableDfa(std::vector<uint32_t> str, bool is_cased);
    ~TableDfa() override;
    [[nodiscard]] MatchResult match(std::string_view source) const override;
    [[nodiscard]] MatchResult match(std::string_view source, std::string& successor_out) const override;
    [[nodiscard]] MatchResult match(std::string_view source, std::vector<uint32_t>& successor_out) const override;
    [[nodiscard]] size_t memory_usage() const noexcept override;
    void dump_as_graphviz(std::ostream& os) const override;
};

}
