// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "table_dfa.h"
#include "match_algorithm.hpp"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <stdexcept>
#include <algorithm>
#include <map>

namespace vespalib::fuzzy {

namespace {

using vespalib::make_string_short::fmt;

// It is useful to know the number of states compile time to be able
// to pack lookup tables better.
template <uint8_t N> constexpr size_t num_states();
template <> constexpr size_t num_states<1>() { return 6; }
template <> constexpr size_t num_states<2>() { return 31; }
template <> constexpr size_t num_states<3>() { return 197; }

template <uint8_t N> constexpr size_t window_size() { return 2 * N + 1; }
template <uint8_t N> constexpr size_t num_transitions() { return 1 << window_size<N>(); }


auto diff(auto a, auto b) { return (a > b) ? (a - b) : (b - a); }

// A Position combines an index into a word being matched with the
// number of edits needed to get there. This maps directly onto a
// specific state in the NFA used to match a word. Note that the sort
// order prefers low edits over low indexs. This is to ensure that a
// position that subsumes another position will always sort before it.
struct Position {
    uint32_t index;
    uint32_t edits;
    Position(uint32_t index_in, uint32_t edits_in) noexcept
      : index(index_in), edits(edits_in) {}
    static Position start() noexcept { return Position(0,0); }
    bool subsumes(const Position &rhs) const noexcept {
        if (edits >= rhs.edits) {
            return false;
        }
        return diff(index, rhs.index) <= (rhs.edits - edits);
    }
    Position materialize(uint32_t target_index) const noexcept {
        return Position(target_index, edits + diff(index, target_index));
    }
    bool operator==(const Position &rhs) const noexcept {
        return (index == rhs.index) && (edits == rhs.edits);
    }
    bool operator<(const Position &rhs) const noexcept {
        return std::tie(edits,index) < std::tie(rhs.edits, rhs.index);
    }
    template <uint8_t N>
    void add_elementary_transitions(const std::vector<bool> &bits, std::vector<Position> &dst) const {
        assert(bits.size() > index);
        if (!bits[index]) {
            dst.emplace_back(index, edits + 1);
            dst.emplace_back(index + 1, edits + 1);
        }
        for (uint32_t e = 0; (edits + e) <= N; ++e) {
            assert(bits.size() > (index + e));
            if (bits[index + e]) {
                dst.emplace_back(index + e + 1, edits + e);
            }
        }
    }
    vespalib::string to_string() const { return fmt("%u#%u", index, edits); }
};

// A State is a collection of different Positions that do not subsume
// each other. If the minimal boundary of a state is larger than 0, it
// can be lifted from the state in a normalizing operation that will
// renumber the position indexes such that the minimal boundary of the
// state becomes 0. This is to allow parameterized states where the
// general progress of matching the string (minimal boundary of
// non-normalized state) is untangled from the local competing edit
// alternatives (normalized state).
struct State {
    std::vector<Position> list;
    State() noexcept : list() {}
    static State failed() noexcept { return State(); }
    static State start() {
        State result;
        result.list.push_back(Position::start());
        return result;
    }
    bool operator<(const State &rhs) const {
        return list < rhs.list;
    }
    uint32_t minimal_boundary() const noexcept {
        if (list.empty()) {
            return 0;
        }
        uint32_t min = list[0].index;
        for (size_t i = 1; i < list.size(); ++i) {
            min = std::min(min, list[i].index);
        }
        return min;
    }
    uint32_t normalize() {
        uint32_t min = minimal_boundary();
        if (min > 0) {
            for (auto &entry: list) {
                entry.index -= min;
            }
        }
        return min;
    }
    template <uint8_t N>
    static State create(std::vector<Position> list_in) {
        State result;
        auto want = [&result](Position pos) {
                        if (pos.edits > N) {
                            return false;
                        }
                        for (const auto &old_pos: result.list) {
                            if (old_pos == pos || old_pos.subsumes(pos)) {
                                return false;
                            }
                        }
                        return true;
                    };
        std::sort(list_in.begin(), list_in.end());
        for (const auto &pos: list_in) {
            if (want(pos)) {
                result.list.push_back(pos);
            }
        }
        return result;
    }
    template <uint8_t N>
    State next(const std::vector<bool> &bits) const {
        std::vector<Position> tmp;
        for (const auto &pos: list) {
            pos.add_elementary_transitions<N>(bits, tmp);
        }
        return create<N>(std::move(tmp));
    }
    template <uint8_t N>
    std::vector<uint8_t> make_edit_vector() const {
        std::vector<uint8_t> result(window_size<N>(), N + 1);
        for (const auto &pos: list) {
            for (uint32_t i = 0; i < window_size<N>(); ++i) {
                result[i] = std::min(result[i], uint8_t(pos.materialize(i).edits));
            }
        }
        return result;
    }
    vespalib::string to_string() const {
        vespalib::string result = "{";
        for (size_t i = 0; i < list.size(); ++i) {
            if (i > 0) {
                result.append(",");
            }
            result.append(list[i].to_string());
        }
        result.append("}");
        return result;
    }
};

// Keeps track of unique states, assigning an integer value to each
// state. Only states with minimal boundary 0 is allowed to be
// inserted into a state repo. Each repo is seeded with the empty
// state (0) and the start state (1). An assigned integer value can be
// mapped back into the originating state.
struct StateRepo {
    using Map = std::map<State,uint32_t>;
    using Ref = Map::iterator;
    Map seen;
    std::vector<Ref> refs;
    StateRepo() noexcept : seen(), refs() {
        auto failed_idx = state_to_idx(State::failed());
        auto start_idx = state_to_idx(State::start());
        assert(failed_idx == 0);
        assert(start_idx == 1);
    }
    ~StateRepo();
    size_t size() const { return seen.size(); }
    uint32_t state_to_idx(const State &state) {
        assert(state.minimal_boundary() == 0);
        uint32_t next = refs.size();
        auto [pos, inserted] = seen.emplace(state, next);
        if (inserted) {
            refs.push_back(pos);
        }
        assert(seen.size() == refs.size());
        return pos->second;
    }
    const State &idx_to_state(uint32_t idx) const {
        assert(idx < refs.size());
        return refs[idx]->first;
    }
};
[[maybe_unused]] StateRepo::~StateRepo() = default;

template <uint8_t N>
std::vector<bool> expand_bits(uint32_t value) {
    static_assert(N < 10);
    std::vector<bool> result(window_size<N>());
    uint32_t look_for = num_transitions<N>();
    assert(value < look_for);
    for (size_t i = 0; i < result.size(); ++i) {
        look_for >>= 1;
        result[i] = (value & look_for);
    }
    return result;
}

template <uint8_t N>
StateRepo make_state_repo() {
    StateRepo repo;
    for (uint32_t idx = 0; idx < repo.size(); ++idx) {
        const State &state = repo.idx_to_state(idx);
        for (uint32_t i = 0; i < num_transitions<N>(); ++i) {
            State new_state = state.next<N>(expand_bits<N>(i));
            (void) new_state.normalize();
            (void) repo.state_to_idx(new_state);
        }
    }
    return repo;
}

struct Transition {
    uint8_t step;
    uint8_t state;
    constexpr Transition() noexcept : step(0), state(0) {}    
    constexpr Transition(uint8_t di, uint8_t ns) noexcept : step(di), state(ns) {}
};

template <uint8_t N> struct InlineTfa;
#include "inline_tfa.hpp"

template <uint8_t N>
struct Tfa {
    // what happens when following a transition from a state?
    std::array<std::array<Transition,num_transitions<N>()>,num_states<N>()> table;

    // how many edits did we use to match the target word?
    std::array<std::array<uint8_t,window_size<N>()>,num_states<N>()> edits;
};

template <uint8_t N>
std::unique_ptr<Tfa<N>> make_tfa() {
    auto tfa = std::make_unique<Tfa<N>>();
    StateRepo repo;
    uint32_t state_idx = 0;
    for (; state_idx < repo.size(); ++state_idx) {
        const State &state = repo.idx_to_state(state_idx);
        for (uint32_t i = 0; i < num_transitions<N>(); ++i) {
            State new_state = state.next<N>(expand_bits<N>(i));
            uint32_t step = new_state.normalize();
            uint32_t new_state_idx = repo.state_to_idx(new_state);
            assert(step < 256);
            assert(new_state_idx < 256);
            tfa->table[state_idx][i].step = step;
            tfa->table[state_idx][i].state = new_state_idx;
        }
        auto edits = state.make_edit_vector<N>();
        assert(edits.size() == window_size<N>());
        for (uint32_t i = 0; i < window_size<N>(); ++i) {
            tfa->edits[state_idx][i] = edits[i];
        }
    }
    assert(repo.size() == num_states<N>());
    assert(state_idx == num_states<N>());
    return tfa;
}

template <typename T>
vespalib::string format_vector(const std::vector<T> &vector, bool compact = false) {
    vespalib::string str = compact ? "" : "[";
    for (size_t i = 0; i < vector.size(); ++i) {
        if (i > 0 && !compact) {
            str.append(",");
        }
        str.append(fmt("%u", uint32_t(vector[i])));
    }
    if (!compact) {
        str.append("]");
    }
    return str;
}

template <uint8_t N>
struct TableMatcher {
    struct S {
        uint32_t index;
        uint32_t state;
        // needed by dfa matcher concept (should use std::declval instead)
        constexpr S() noexcept : index(0), state(0) {}
        constexpr S(uint32_t i, uint32_t s) noexcept : index(i), state(s) {}
        S next(uint32_t bits) noexcept {
            const auto &entry = InlineTfa<N>::table[state][bits];
            return S(index + entry.step, entry.state);
        }
        constexpr bool is_valid_edge(uint32_t bits) const noexcept {
            return InlineTfa<N>::table[state][bits].state != 0;
        }
    };
    using StateType = S;
    using StateParamType = StateType;
    using EdgeType = uint32_t;

    const TableDfa<N>::Lookup *lookup;
    const uint32_t             end;
    const bool                 cased;

    TableMatcher(const TableDfa<N>::Lookup *lookup_in, uint32_t end_in, bool cased_in)
      noexcept : lookup(lookup_in), end(end_in), cased(cased_in) {}
    
    bool is_cased() const noexcept { return cased; }
    static constexpr S start() noexcept { return S(0, 1); }

    uint8_t match_edit_distance(S s) const noexcept {
        uint32_t leap = (end - s.index);
        return (leap < window_size<N>()) ? InlineTfa<N>::edits[s.state][leap] : N + 1;
    }
    bool is_match(S s) const noexcept { return match_edit_distance(s) <= N; }

    static constexpr bool can_match(S s) noexcept { return (s.state != 0); }
    static constexpr bool valid_state(S s) noexcept { return (s.state != 0); }

    S match_wildcard(S s) const noexcept { return s.next(0); }
    S match_input(S s, uint32_t c) const noexcept {
        const auto *slice = lookup[s.index].list.data();
        for (size_t i = 0; i < window_size<N>() && slice[i].input != 0; ++i) {
            if (slice[i].input == c) {
                return s.next(slice[i].match);
            }
        }
        return match_wildcard(s);
    }

    bool has_higher_out_edge(S s, uint32_t c) const noexcept {
        if (s.is_valid_edge(0)) {
            return true;
        }
        const auto *slice = lookup[s.index].list.data();
        for (size_t i = 0; i < window_size<N>() && slice[i].input > c; ++i) {
            if (s.is_valid_edge(slice[i].match)) {
                return true;
            }
        }
        return false;
    }

    bool has_exact_explicit_out_edge(S s, uint32_t c) const noexcept {
        const auto *slice = lookup[s.index].list.data();
        for (size_t i = 0; i < window_size<N>() && slice[i].input >= c; ++i) {
            if (slice[i].input == c) {
                return s.is_valid_edge(slice[i].match);
            }
        }
        return false;
    }

    uint32_t lowest_higher_explicit_out_edge(S s, uint32_t c) const noexcept {        
        const auto *slice = lookup[s.index].list.data();
        size_t i = window_size<N>();
        while (i-- > 0) {
            if (slice[i].input > c && s.is_valid_edge(slice[i].match)) {
                return slice[i].input;
            }
        }
        return 0;
    }
    
    uint32_t smallest_explicit_out_edge(S s) const noexcept {
        const auto *slice = lookup[s.index].list.data();
        size_t i = window_size<N>();
        while (i-- > 0) {
            if (slice[i].input != 0 && s.is_valid_edge(slice[i].match)) {
                return slice[i].input;
            }
        }
        return 0;
    }
    
    static constexpr bool valid_edge(uint32_t c) noexcept { return c != 0; }
    static constexpr uint32_t edge_to_u32char(uint32_t c) noexcept { return c; }
    S edge_to_state(S s, uint32_t c) const noexcept { return match_input(s, c); }

    static constexpr bool implies_exact_match_suffix(S) noexcept { return false; }
    static constexpr void emit_exact_match_suffix(S, std::vector<uint32_t> &) {} // not called
    static constexpr void emit_exact_match_suffix(S, std::string &) {} // not called
};

} // unnamed

template <uint8_t N>
auto
TableDfa<N>::make_lookup(const std::vector<uint32_t> &str)->std::vector<Lookup>
{
    std::vector<Lookup> result(str.size() + 1);
    auto have_already = [&](uint32_t c, size_t i)noexcept{
                            for (size_t j = 0; j < window_size(); ++j) {
                                if (result[i].list[j].input == c) {
                                    return true;
                                }
                            }
                            return false;
                        };
    auto make_vector = [&](uint32_t c, size_t i)noexcept{
                           uint32_t bits = 0;
                           for (size_t j = 0; j < window_size(); ++j) {
                               bool found = ((i + j) < str.size()) && (str[i+j] == c);
                               bits = (bits << 1) + found;
                           }
                           return bits;
                       };
    for (size_t i = 0; i < str.size(); ++i) {
        for (size_t j = 0; j < window_size(); ++j) {
            assert(result[i].list[j].input == 0);
            assert(result[i].list[j].match == 0);
            if ((i + j) < str.size()) {
                uint32_t c = str[i + j];
                if (!have_already(c, i)) {
                    result[i].list[j].input = c;
                    result[i].list[j].match = make_vector(c, i);
                }
            }
        }
        std::sort(result[i].list.begin(), result[i].list.end(),
                  [](const auto &a, const auto &b){ return a.input > b.input; });
    }
    return result;
}

template <uint8_t N>
TableDfa<N>::TableDfa(std::vector<uint32_t> str, bool is_cased)
  : _lookup(make_lookup(str)),
    _is_cased(is_cased)
{
}

template <uint8_t N>
TableDfa<N>::~TableDfa() = default;

template <uint8_t N>
LevenshteinDfa::MatchResult
TableDfa<N>::match(std::string_view u8str) const
{
    TableMatcher<N> matcher(_lookup.data(), _lookup.size() - 1, _is_cased);
    return MatchAlgorithm<N>::match(matcher, u8str);
}

template <uint8_t N>
LevenshteinDfa::MatchResult
TableDfa<N>::match(std::string_view u8str, std::string& successor_out) const
{
    TableMatcher<N> matcher(_lookup.data(), _lookup.size() - 1, _is_cased);
    return MatchAlgorithm<N>::match(matcher, u8str, successor_out);
}

template <uint8_t N>
LevenshteinDfa::MatchResult
TableDfa<N>::match(std::string_view u8str, std::vector<uint32_t>& successor_out) const
{
    TableMatcher<N> matcher(_lookup.data(), _lookup.size() - 1, _is_cased);
    return MatchAlgorithm<N>::match(matcher, u8str, successor_out);
}

template <uint8_t N>
size_t
TableDfa<N>::memory_usage() const noexcept
{
    return _lookup.size() * sizeof(Lookup);
}

template <uint8_t N>
void
TableDfa<N>::dump_as_graphviz(std::ostream &os) const
{
    os << std::dec << "digraph table_dfa {\n";
    for (size_t i = 0; i < _lookup.size(); ++i) {
        for (size_t j = 0; j < window_size(); ++j) {
            if (_lookup[i].list[j].input != 0) {
                std::string as_utf8;
                append_utf32_char(as_utf8, _lookup[i].list[j].input);
                os << "    x" << i << " -> " << format_vector(expand_bits<N>(_lookup[i].list[j].match), true) << " [label=\"" << as_utf8 << "\"];\n";
            }
        }
        os << "    x" << i << " -> " << format_vector(expand_bits<N>(0), true) << " [label=\"*\"];\n";
    }
    os << "}\n";
}

}
