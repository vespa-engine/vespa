// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/fuzzy/table_dfa.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <set>

using namespace ::testing;
using namespace vespalib::fuzzy;

// test/experiment with low-level concepts underlying the construction
// of the tables used in the table-driven dfa implementation.

TEST(TableDfaTest, position) {
    Position pos1 = Position::start();
    EXPECT_EQ(pos1.index, 0);
    EXPECT_EQ(pos1.edits, 0);
    Position pos2(2, 3);
    EXPECT_EQ(pos2.index, 2);
    EXPECT_EQ(pos2.edits, 3);
}

TEST(TableDfaTest, position_equality) {
    Position pos1(0, 0);
    Position pos2(0, 1);
    Position pos3(1, 0);
    EXPECT_TRUE(pos1 == pos1);
    EXPECT_FALSE(pos1 == pos2);
    EXPECT_FALSE(pos1 == pos2);
}

TEST(TableDfaTest, position_sort_order) {
    std::vector<Position> list;
    list.emplace_back(0,1);
    list.emplace_back(0,0);
    list.emplace_back(1,0);
    list.emplace_back(1,1);
    std::sort(list.begin(), list.end());
    EXPECT_EQ(list[0].index, 0);
    EXPECT_EQ(list[0].edits, 0);
    EXPECT_EQ(list[1].index, 1);
    EXPECT_EQ(list[1].edits, 0);
    EXPECT_EQ(list[2].index, 0);
    EXPECT_EQ(list[2].edits, 1);
    EXPECT_EQ(list[3].index, 1);
    EXPECT_EQ(list[3].edits, 1);
}

TEST(TableDfaTest, position_subsumption) {
    Position pos1(0, 0);
    Position pos2(0, 1);
    Position pos3(0, 2);

    Position pos4(1, 0);
    Position pos5(1, 1);
    Position pos6(1, 2);
    
    Position pos7(2, 0);
    Position pos8(2, 1);
    Position pos9(2, 2);
    
    EXPECT_FALSE(pos1.subsumes(pos1));
    EXPECT_TRUE(pos1.subsumes(pos2));
    EXPECT_TRUE(pos1.subsumes(pos3));
    EXPECT_FALSE(pos1.subsumes(pos4));
    EXPECT_TRUE(pos1.subsumes(pos5));
    EXPECT_TRUE(pos1.subsumes(pos6));
    EXPECT_FALSE(pos1.subsumes(pos7));
    EXPECT_FALSE(pos1.subsumes(pos8));
    EXPECT_TRUE(pos1.subsumes(pos9));

    EXPECT_FALSE(pos5.subsumes(pos1));
    EXPECT_FALSE(pos5.subsumes(pos2));
    EXPECT_TRUE(pos5.subsumes(pos3));
    EXPECT_FALSE(pos5.subsumes(pos4));
    EXPECT_FALSE(pos5.subsumes(pos5));
    EXPECT_TRUE(pos5.subsumes(pos6));
    EXPECT_FALSE(pos5.subsumes(pos7));
    EXPECT_FALSE(pos5.subsumes(pos8));
    EXPECT_TRUE(pos5.subsumes(pos9));
}

TEST(TableDfaTest, position_materialization) {
    EXPECT_EQ(Position(1,1).materialize(0).index, 0);
    EXPECT_EQ(Position(1,1).materialize(1).index, 1);
    EXPECT_EQ(Position(1,1).materialize(2).index, 2);
    EXPECT_EQ(Position(1,1).materialize(0).edits, 2);
    EXPECT_EQ(Position(1,1).materialize(1).edits, 1);
    EXPECT_EQ(Position(1,1).materialize(2).edits, 2);
}

TEST(TableDfaTest, position_to_string) {
    Position pos1(0, 0);
    Position pos2(1, 2);
    Position pos3(2, 3);
    EXPECT_EQ(pos1.to_string(), fmt("0#0"));
    EXPECT_EQ(pos2.to_string(), fmt("1#2"));
    EXPECT_EQ(pos3.to_string(), fmt("2#3"));
}

TEST(TableDfaTest, state_creation_reorder) {
    EXPECT_EQ(State::create<5>({{0,1},{2,0}}).to_string(), fmt("{2#0,0#1}"));
    EXPECT_EQ(State::create<5>({{2,0},{0,0}}).to_string(), fmt("{0#0,2#0}"));
}

TEST(TableDfaTest, state_creation_duplicate_removal) {
    EXPECT_EQ(State::create<5>({{0,0},{0,0},{2,1},{2,1}}).to_string(), fmt("{0#0,2#1}"));
}

TEST(TableDfaTest, state_creation_edit_cutoff) {
    EXPECT_EQ(State::create<2>({{0,0},{5,2},{10,3}}).to_string(), fmt("{0#0,5#2}"));
}

TEST(TableDfaTest, state_creation_subsumption_collapsing) {
    EXPECT_EQ(State::create<2>({{0,0},{1,1}}).to_string(), fmt("{0#0}"));
    EXPECT_EQ(State::create<2>({{0,1},{1,0}}).to_string(), fmt("{1#0}"));
    EXPECT_EQ(State::create<2>({{0,0},{2,2}}).to_string(), fmt("{0#0}"));
    EXPECT_EQ(State::create<2>({{0,2},{2,0}}).to_string(), fmt("{2#0}"));
}

TEST(TableDfaTest, state_normalization) {
    auto state1 = State::create<2>({{2,1},{3,1}});
    auto state2 = State::create<2>({{5,0},{3,1}});
    EXPECT_EQ(state1.to_string(), fmt("{2#1,3#1}"));
    EXPECT_EQ(state2.to_string(), fmt("{5#0,3#1}"));
    EXPECT_EQ(state1.normalize(), 2);
    EXPECT_EQ(state2.normalize(), 3);
    EXPECT_EQ(state1.to_string(), fmt("{0#1,1#1}"));
    EXPECT_EQ(state2.to_string(), fmt("{2#0,0#1}"));
}

TEST(TableDfaTest, state_repo) {
    StateRepo repo;
    EXPECT_EQ(repo.state_to_idx(State::failed()), 0);
    EXPECT_EQ(repo.state_to_idx(State::start()), 1);
    EXPECT_EQ(repo.state_to_idx(State::create<2>({{0,0},{1,0}})), 2);
    EXPECT_EQ(repo.state_to_idx(State::create<2>({{0,0},{2,1}})), 3);
    EXPECT_EQ(repo.state_to_idx(State::create<2>({{0,0},{1,0}})), 2);
    EXPECT_EQ(repo.state_to_idx(State::create<2>({{0,0},{2,1}})), 3);
    EXPECT_EQ(repo.size(), 4);
    EXPECT_EQ(repo.idx_to_state(0).to_string(), fmt("{}"));
    EXPECT_EQ(repo.idx_to_state(1).to_string(), fmt("{0#0}"));
    EXPECT_EQ(repo.idx_to_state(2).to_string(), fmt("{0#0,1#0}"));
    EXPECT_EQ(repo.idx_to_state(3).to_string(), fmt("{0#0,2#1}"));
}

TEST(TableDfaTest, expand_bits) {
    auto yes = expand_bits<2>(0x1f);
    auto no = expand_bits<2>(0x00);
    auto odd = expand_bits<2>(0x0a);
    auto even = expand_bits<2>(0x15);
    ASSERT_EQ(yes.size(), 5);
    ASSERT_EQ(no.size(), 5);
    ASSERT_EQ(odd.size(), 5);
    ASSERT_EQ(even.size(), 5);
    for (size_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(yes[i]);
        EXPECT_FALSE(no[i]);
        EXPECT_EQ(odd[i], bool(i % 2 == 1));
        EXPECT_EQ(even[i], bool(i % 2 == 0));
    }
}

TEST(TableDfaTest, format_bits) {
    EXPECT_EQ(format_vector(expand_bits<1>(0)), fmt("[0,0,0]"));
    EXPECT_EQ(format_vector(expand_bits<1>(7)), fmt("[1,1,1]"));
    EXPECT_EQ(format_vector(expand_bits<1>(5)), fmt("[1,0,1]"));
    EXPECT_EQ(format_vector(expand_bits<1>(2)), fmt("[0,1,0]"));
    EXPECT_EQ(format_vector(expand_bits<2>(31)), fmt("[1,1,1,1,1]"));
    EXPECT_EQ(format_vector(expand_bits<2>(21)), fmt("[1,0,1,0,1]"));
    EXPECT_EQ(format_vector(expand_bits<2>(31), true), fmt("11111"));
    EXPECT_EQ(format_vector(expand_bits<2>(21), true), fmt("10101"));
}

template <uint8_t N>
void list_states() {
    auto repo = make_state_repo<N>();
    EXPECT_EQ(num_states<N>(), repo.size());
    fprintf(stderr, "max_edits: %u, number of states: %zu\n", N, repo.size());
    for (uint32_t i = 0; i < repo.size(); ++i) {
        fprintf(stderr, "  state %u: %s\n", i, repo.idx_to_state(i).to_string().c_str());
    }
}

TEST(TableDfaTest, list_states_for_max_edits_1) { list_states<1>(); }
TEST(TableDfaTest, list_states_for_max_edits_2) { list_states<2>(); }

template <uint8_t N>
void list_edits() {
    auto repo = make_state_repo<N>();
    fprintf(stderr,
            "per state, listing the minimal number of edits needed\n"
            "to reach offsets at and beyond its minimal boundary\n");
    for (uint32_t i = 0; i < repo.size(); ++i) {
        const State &state = repo.idx_to_state(i);
        fprintf(stderr, "%-23s : %s\n", state.to_string().c_str(),
                format_vector(state.make_edit_vector<N>()).c_str());
    }
}

TEST(TableDfaTest, list_edits_at_input_end_for_max_edits_1) { list_edits<1>(); }
TEST(TableDfaTest, list_edits_at_input_end_for_max_edits_2) { list_edits<2>(); }

template <uint8_t N>
void list_transitions() {
    auto repo = make_state_repo<N>();
    for (uint32_t idx = 0; idx < repo.size(); ++idx) {
        const State &state = repo.idx_to_state(idx);
        for (uint32_t i = 0; i < num_transitions<N>(); ++i) {
            auto bits = expand_bits<N>(i);
            State new_state = state.next<N>(bits);
            uint32_t step = new_state.normalize();
            uint32_t new_idx = repo.state_to_idx(new_state);
            ASSERT_LT(new_idx, repo.size());
            fprintf(stderr, "%u:%s,i --%s--> %u:%s,%s\n", idx, state.to_string().c_str(),
                    format_vector(bits).c_str(), new_idx, new_state.to_string().c_str(),
                    (step == 0) ? "i" : fmt("i+%u", step).c_str());
        }
    }
}

TEST(TableDfaTest, list_transitions_for_max_edits_1) { list_transitions<1>(); }

// Simulate all possible ways we can approach the end of the word we
// are matching. Verify that no transition taken can produce a state
// with a minimal boundary that exceeds the boundary of the word
// itself. Verifying this will enable us to not care about word size
// while simulating the dfa.
template <uint8_t N>
void verify_word_end_boundary() {
    auto repo = make_state_repo<N>();
    using StateSet = std::set<uint32_t>;
    std::vector<StateSet> active(window_size<N>() + 1);
    for (size_t i = 1; i < repo.size(); ++i) {
        active[0].insert(i);
    }
    EXPECT_EQ(active.size(), window_size<N>() + 1);
    EXPECT_EQ(active[0].size(), repo.size() - 1);
    fprintf(stderr, "verifying word end for max edits %u\n", N);
    uint32_t edge_shape = 0;
    for (uint32_t active_idx = 0; active_idx < active.size(); ++active_idx) {
        fprintf(stderr, "  edge shape: %s, max step: %zu, active_states: %zu\n",
                format_vector(expand_bits<N>(edge_shape)).c_str(), active.size() - active_idx - 1, active[active_idx].size());
        for (uint32_t idx: active[active_idx]) {
            const State &state = repo.idx_to_state(idx);
            for (uint32_t i = 0; i < num_transitions<N>(); ++i) {
                if ((i & edge_shape) == 0) {
                    State new_state = state.next<N>(expand_bits<N>(i));
                    uint32_t step = new_state.normalize();
                    uint32_t new_idx = repo.state_to_idx(new_state);
                    ASSERT_LT(new_idx, repo.size());
                    if (new_idx != 0) {
                        ASSERT_GT(active.size(), active_idx + step);
                        active[active_idx + step].insert(new_idx);
                    }
                }
            }
        }
        edge_shape = (edge_shape << 1) + 1;
    }
    EXPECT_EQ(edge_shape, (1 << (window_size<N>() + 1)) - 1);
    while (!active.back().empty()) {
        fprintf(stderr, "  residue states after word end: %zu\n", active.back().size());
        StateSet residue;
        for (uint32_t idx: active.back()) {
            const State &state = repo.idx_to_state(idx);
            State new_state = state.next<N>(expand_bits<N>(0));
            uint32_t step = new_state.normalize();
            uint32_t new_idx = repo.state_to_idx(new_state);
            ASSERT_LT(new_idx, repo.size());
            ASSERT_EQ(step, 0);
            if (new_idx != 0) {
                residue.insert(new_idx);
            }
        }
        active.back() = std::move(residue);
    }
}

TEST(TableDfaTest, minimal_boundary_will_never_exceed_word_end_with_max_edits_1) {
    verify_word_end_boundary<1>();
}

TEST(TableDfaTest, minimal_boundary_will_never_exceed_word_end_with_max_edits_2) {
    verify_word_end_boundary<2>();
}

template <uint8_t N>
void verify_inline_tfa() {
    auto tfa = make_tfa<N>();
    fprintf(stderr, "verifying TFA for N = %u (byte size: %zu)\n", N, sizeof(*tfa));
    ASSERT_EQ(tfa->table.size(), num_states<N>());
    ASSERT_EQ(tfa->edits.size(), num_states<N>());
    for (size_t state = 0; state < num_states<N>(); ++state) {
        ASSERT_EQ(tfa->table[state].size(), num_transitions<N>());
        for (size_t transition = 0; transition < num_transitions<N>(); ++transition) {
            EXPECT_EQ(tfa->table[state][transition].step, InlineTfa<N>::table[state][transition].step);
            EXPECT_EQ(tfa->table[state][transition].state, InlineTfa<N>::table[state][transition].state);
        }
        ASSERT_EQ(tfa->edits[state].size(), window_size<N>());
        for (size_t offset = 0; offset < window_size<N>(); ++offset) {
            EXPECT_EQ(tfa->edits[state][offset], InlineTfa<N>::edits[state][offset]);
        }
    }
}

TEST(TableDfaTest, verify_inline_tfa_with_max_edits_1) {
    verify_inline_tfa<1>();
}

TEST(TableDfaTest, verify_inline_tfa_with_max_edits_2) {
    verify_inline_tfa<2>();
}

template <uint8_t N>
void dump_tfa_as_code() {
    auto tfa = make_tfa<N>();
    fprintf(stderr, "// start of auto-generated code for N = %u\n", N);
    fprintf(stderr, "template <> struct InlineTfa<%u> {\n", N);
    fprintf(stderr, "    static constexpr Transition table[%zu][%zu] = {\n", num_states<N>(), num_transitions<N>());
    for (size_t state = 0; state < num_states<N>(); ++state) {
        fprintf(stderr, "        {");
        for (size_t transition = 0; transition < num_transitions<N>(); ++transition) {
            if (transition > 0) {
                fprintf(stderr, ",");
            }
            fprintf(stderr, "{%u,%u}", tfa->table[state][transition].step, tfa->table[state][transition].state);
        }
        fprintf(stderr, "}%s\n", ((state + 1) < num_states<N>()) ? "," : "");
    }
    fprintf(stderr, "    };\n");
    fprintf(stderr, "    static constexpr uint8_t edits[%zu][%zu] = {\n", num_states<N>(), window_size<N>());
    for (size_t state = 0; state < num_states<N>(); ++state) {
        fprintf(stderr, "        {");
        for (size_t offset = 0; offset < window_size<N>(); ++offset) {
            if (offset > 0) {
                fprintf(stderr, ",");
            }
            fprintf(stderr, "%u", tfa->edits[state][offset]);
        }
        fprintf(stderr, "}%s\n", ((state + 1) < num_states<N>()) ? "," : "");
    }
    fprintf(stderr, "    };\n");
    fprintf(stderr, "};\n");
    fprintf(stderr, "// end of auto-generated code for N = %u\n", N);
}

TEST(TableDfaTest, dump_tfa_with_max_edits_1_as_code) {
    dump_tfa_as_code<1>();
}

TEST(TableDfaTest, dump_tfa_with_max_edits_2_as_code) {
    dump_tfa_as_code<2>();
}

template <uint8_t N>
void dump_tfa_graph() {
    auto repo = make_state_repo<N>();
    fprintf(stderr, "digraph tfa {\n");
    for (uint32_t idx = 0; idx < repo.size(); ++idx) {
        fprintf(stderr, "    %u [label=\"%s\"];\n", idx,
                repo.idx_to_state(idx).to_string().c_str());
    }
    // omit transitions from the failure state to itself
    for (uint32_t idx = 1; idx < repo.size(); ++idx) {
        const State &state = repo.idx_to_state(idx);
        for (uint32_t i = 0; i < num_transitions<N>(); ++i) {
            auto bits = expand_bits<N>(i);
            State new_state = state.next<N>(bits);
            uint32_t step = new_state.normalize();
            uint32_t new_idx = repo.state_to_idx(new_state);
            ASSERT_LT(new_idx, repo.size());
            if (bits[0] && idx == new_idx && step == 1) {
                // omit simple transitions to yourself
            } else {
                fprintf(stderr, "    %u -> %u [label=\"%s,%u\"];\n", idx, new_idx,
                        format_vector(bits, true).c_str(), step);
            }
        }
    }
    fprintf(stderr, "}\n");
}

TEST(TableDfaTest, graphviz_for_tfa_with_max_edits_1) {
    dump_tfa_graph<1>();
}

TEST(TableDfaTest, graphviz_for_food_with_max_edits_1) {
    auto dfa = LevenshteinDfa::build("food", 1, LevenshteinDfa::Casing::Cased, LevenshteinDfa::DfaType::Table);
    std::ostringstream out;
    dfa.dump_as_graphviz(out);
    fprintf(stderr, "memory usage: %zu\n", dfa.memory_usage());
    fprintf(stderr, "%s", out.str().c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()
