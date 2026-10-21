// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/correlated_rows.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/doom.h>

#include <algorithm>
#include <functional>
#include <limits>
#include <optional>
#include <random>
#include <stdexcept>

using namespace search::queryeval;
using namespace std::literals;

namespace {

std::vector<BindingRow> reference_join(const std::vector<BindingTable>& tables, uint32_t columns) {
    std::vector<std::vector<const BindingRow*>> combinations(1);
    for (const auto& table : tables) {
        std::vector<std::vector<const BindingRow*>> next;
        for (const auto& combination : combinations) {
            for (const auto& row : table.rows) {
                auto copy = combination;
                copy.push_back(&row);
                next.push_back(std::move(copy));
            }
        }
        combinations = std::move(next);
    }
    std::vector<BindingRow> result;
    for (const auto& combination : combinations) {
        std::vector<std::optional<BindingValue>> binding(columns);
        bool conflict = false;
        for (size_t i = 0; i < tables.size(); ++i) {
            for (size_t j = 0; j < tables[i].columns.size(); ++j) {
                auto& slot = binding[tables[i].columns[j]];
                const auto& value = (*combination[i])[j];
                if (slot && *slot != value) conflict = true;
                slot = value;
            }
        }
        if (conflict) continue;
        BindingRow row;
        for (const auto& slot : binding) row.push_back(slot.value());
        result.push_back(std::move(row));
    }
    std::sort(result.begin(), result.end());
    result.erase(std::unique(result.begin(), result.end()), result.end());
    return result;
}

// Three clauses that bind the pairs (x, y), (y, z) and (x, z) of one unit, where
// the two values of each pair have different parity. No x, y and z can differ in
// parity pairwise, so the join finds nothing, but only after visiting many rows.
std::vector<BindingTable> unsatisfiable_cycle(int64_t values) {
    std::vector<BindingTable> tables{{{0, 1, 2}, {}}, {{0, 2, 3}, {}}, {{0, 1, 3}, {}}};
    for (int64_t a = 0; a < values; ++a) {
        for (int64_t b = 0; b < values; ++b) {
            if ((a % 2) != (b % 2)) {
                for (auto& table : tables) table.rows.push_back({int64_t(7), a, b});
            }
        }
    }
    return tables;
}

// A deadline whose soft and hard parts are the given time from now.
struct TestDoom {
    std::atomic<vespalib::steady_time> now{vespalib::steady_clock::now()};
    vespalib::Doom doom;
    TestDoom(vespalib::duration soft, vespalib::duration hard)
        : doom(now, now.load() + soft, now.load() + hard, false) {}
};

// Memory that the join counts for this many collected rows of the given width.
size_t memory_of_rows(size_t rows, size_t width) {
    return rows * (sizeof(BindingRow) + width * sizeof(BindingValue));
}

} // namespace

TEST(CorrelatedRowsTest, matches_a_shared_unit_and_zone_with_a_zone_free_clause) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{"sku1"sv, "WAREHOUSE_A"sv}, {"sku2"sv, "WAREHOUSE_B"sv}}},
        {{0, 1}, {{"sku2"sv, "WAREHOUSE_B"sv}}},
        {{0}, {{"sku2"sv}}}
    };
    CorrelatedRows join(2);
    EXPECT_TRUE(join.has_match(tables));
    EXPECT_EQ(join.find_matches(tables), (std::vector<BindingRow>{{"sku2"sv, "WAREHOUSE_B"sv}}));
}

TEST(CorrelatedRowsTest, rejects_price_and_size_in_different_zones_of_the_same_unit) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{"sku1"sv, "WAREHOUSE_A"sv}}},
        {{0, 1}, {{"sku1"sv, "WAREHOUSE_B"sv}}},
        {{0}, {{"sku1"sv}}}
    };
    CorrelatedRows join(2);
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_TRUE(join.find_matches(tables).empty());
}

TEST(CorrelatedRowsTest, rejects_matching_values_in_different_units_of_the_same_zone) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{"sku1"sv, "WAREHOUSE_A"sv}}},
        {{0, 1}, {{"sku2"sv, "WAREHOUSE_A"sv}}},
        {{0}, {{"sku1"sv}, {"sku2"sv}}}
    };
    CorrelatedRows join(2);
    EXPECT_FALSE(join.has_match(tables));
}

TEST(CorrelatedRowsTest, rejects_a_zone_free_clause_matching_only_another_unit) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{int64_t(1), "A"sv}}},
        {{0, 1}, {{int64_t(1), "A"sv}}},
        {{0}, {{int64_t(2)}}}
    };
    CorrelatedRows join(2);
    EXPECT_FALSE(join.has_match(tables));
}

TEST(CorrelatedRowsTest, collects_every_distinct_unit_zone_pair_once) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{int64_t(2), "B"sv}, {int64_t(1), "A"sv}, {int64_t(1), "A"sv}, {int64_t(1), "B"sv}}},
        {{0, 1}, {{int64_t(1), "A"sv}, {int64_t(2), "B"sv}, {int64_t(1), "B"sv}}},
        {{0}, {{int64_t(1)}, {int64_t(2)}, {int64_t(2)}}}
    };
    CorrelatedRows join(2);
    EXPECT_EQ(join.find_matches(tables), (std::vector<BindingRow>{
        {int64_t(1), "A"sv}, {int64_t(1), "B"sv}, {int64_t(2), "B"sv}}));
}

TEST(CorrelatedRowsTest, correlates_independent_zone_namespaces_through_the_same_unit) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{"sku1"sv, "stock_A"sv}, {"sku2"sv, "stock_B"sv}}},
        {{0, 2}, {{"sku2"sv, "price_A"sv}, {"sku3"sv, "price_B"sv}}},
        {{0}, {{"sku2"sv}}}
    };
    CorrelatedRows join(3);
    EXPECT_TRUE(join.has_match(tables));
    EXPECT_EQ(join.find_matches(tables), (std::vector<BindingRow>{{"sku2"sv, "stock_B"sv, "price_A"sv}}));
}

TEST(CorrelatedRowsTest, clears_collected_bindings_when_reused_for_another_document) {
    std::vector<BindingTable> matching{{{0}, {{int64_t(1)}}}, {{0}, {{int64_t(1)}}}};
    std::vector<BindingTable> failing{{{0}, {{int64_t(2)}}}, {{0}, {{int64_t(1)}}}};
    CorrelatedRows join(1);
    EXPECT_EQ(join.find_matches(matching).size(), 1);
    EXPECT_FALSE(join.has_match(failing));
    EXPECT_TRUE(join.find_matches(failing).empty());
    EXPECT_TRUE(join.has_match(matching));
}

TEST(CorrelatedRowsTest, projected_bindings_keep_requested_order_and_deduplicate_other_variables) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{int64_t(1), "A"sv}, {int64_t(1), "B"sv}, {int64_t(2), "B"sv}}},
        {{0, 2}, {{int64_t(1), "C"sv}, {int64_t(1), "D"sv}, {int64_t(2), "C"sv}}}
    };
    CorrelatedRows join(3);
    std::vector<uint32_t> projection{2, 0};
    EXPECT_EQ(join.find_matches(tables, projection), (std::vector<BindingRow>{
        {"C"sv, int64_t(1)}, {"C"sv, int64_t(2)}, {"D"sv, int64_t(1)}}));
    projection = {0};
    EXPECT_EQ(join.find_matches(tables, projection), (std::vector<BindingRow>{{int64_t(1)}, {int64_t(2)}}));
    EXPECT_EQ(join.find_matches(tables, {}), (std::vector<BindingRow>{{}}));
    tables.back().rows.clear();
    EXPECT_TRUE(join.find_matches(tables, {}).empty());
}

TEST(CorrelatedRowsTest, projection_rejects_duplicate_and_out_of_range_columns) {
    std::vector<BindingTable> tables{{{0, 1}, {{int64_t(1), "A"sv}}}};
    CorrelatedRows join(2);
    std::vector<uint32_t> duplicate{1, 1}, out_of_range{2};
    EXPECT_THROW(join.find_matches(tables, duplicate), std::invalid_argument);
    EXPECT_THROW(join.find_matches(tables, out_of_range), std::invalid_argument);
}

TEST(CorrelatedRowsTest, unit_projection_does_not_materialize_independent_zone_combinations) {
    constexpr uint32_t zones = 12;
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= zones; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t unit = 1; unit <= 100; ++unit) {
            for (int64_t zone = 0; zone < 5; ++zone) table.rows.push_back({unit, zone});
        }
        tables.push_back(std::move(table));
    }
    CorrelatedRows join(zones + 1);
    std::vector<uint32_t> projection{0};
    const auto& matches = join.find_matches(tables, projection);
    ASSERT_EQ(100u, matches.size());
    for (size_t i = 0; i < matches.size(); ++i) EXPECT_EQ(matches[i], (BindingRow{int64_t(i + 1)}));
}

TEST(CorrelatedRowsTest, a_shared_variable_that_is_not_the_first_column_is_still_looked_up) {
    // Variables sorted by name: priceZone = 0, stockZone = 1, unit = 2.
    std::vector<BindingTable> tables{{{0, 2}, {}}, {{1, 2}, {}}};
    for (int64_t unit = 0; unit < 100; ++unit) {
        for (int64_t zone = 0; zone < 5; ++zone) {
            tables[0].rows.push_back({zone, unit});
            tables[1].rows.push_back({zone + 10, unit});
        }
    }
    CorrelatedRows join(3);
    EXPECT_TRUE(join.has_match(tables));
    // The zones are bound by one clause each, so only the unit is joined.
    EXPECT_LE(join.visited_rows(), 2u);
    const auto& matches = join.find_matches(tables);
    EXPECT_EQ(2500u, matches.size());
    EXPECT_EQ(matches.front(), (BindingRow{int64_t(0), int64_t(10), int64_t(0)}));
    // Each price row looks up the five stock rows of its unit, instead of scanning all 500.
    EXPECT_LE(join.visited_rows(), 500u + 500u * 5u);
}

TEST(CorrelatedRowsTest, variables_bound_by_one_clause_do_not_multiply_the_work) {
    // Variables sorted by name: mode = 0, priceZone = 1, stockZone = 2, unit = 3, w = 4.
    std::vector<BindingTable> tables{{{1, 3}, {}}, {{2, 3}, {}}, {{0, 3}, {}}, {{3, 4}, {{int64_t(8), int64_t(1)}}}};
    for (int64_t value = 0; value < 20; ++value) {
        for (size_t i = 0; i < 3; ++i) tables[i].rows.push_back({value, int64_t(7)});
    }
    CorrelatedRows join(5);
    EXPECT_FALSE(join.has_match(tables));
    // Unit 7 has no w row, which is found without trying every zone and mode combination.
    EXPECT_LE(join.visited_rows(), 4u);
    std::vector<uint32_t> projection{3};
    EXPECT_TRUE(join.find_matches(tables, projection).empty());
    EXPECT_LE(join.visited_rows(), 4u);
    tables[3].rows.push_back({int64_t(7), int64_t(2)});
    EXPECT_TRUE(join.has_match(tables));
    EXPECT_EQ(join.find_matches(tables, projection), (std::vector<BindingRow>{{int64_t(7)}}));
    EXPECT_LE(join.visited_rows(), 4u);
}

TEST(CorrelatedRowsTest, an_empty_clause_cannot_match) {
    std::vector<BindingTable> tables{{{0}, {{int64_t(1)}}}, {{0}, {}}};
    CorrelatedRows join(1);
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_TRUE(join.find_matches(tables).empty());
}

TEST(CorrelatedRowsTest, string_bindings_use_exact_equality_and_do_not_coerce_integers) {
    CorrelatedRows join(1);
    std::vector<BindingTable> tables{{{0}, {{"SKU"sv}}}, {{0}, {{"sku"sv}}}};
    EXPECT_FALSE(join.has_match(tables));
    tables = {{{0}, {{int64_t(42)}}}, {{0}, {{"42"sv}}}};
    EXPECT_FALSE(join.has_match(tables));
    tables = {{{0}, {{""sv}, {"北"sv}}}, {{0}, {{""sv}, {"北"sv}}}};
    EXPECT_EQ(join.find_matches(tables).size(), 2);
}

TEST(CorrelatedRowsTest, all_integer_values_remain_distinct) {
    CorrelatedRows join(1);
    std::vector<BindingTable> tables{
        {{0}, {{std::numeric_limits<int64_t>::min()}, {int64_t(0)}, {std::numeric_limits<int64_t>::max()}}},
        {{0}, {{std::numeric_limits<int64_t>::max()}, {std::numeric_limits<int64_t>::min()}, {int64_t(0)}}}
    };
    EXPECT_EQ(join.find_matches(tables).size(), 3);
}

TEST(CorrelatedRowsTest, rejects_invalid_binding_tables) {
    EXPECT_THROW(CorrelatedRows(0), std::invalid_argument);
    CorrelatedRows join(2);
    for (auto tables : std::vector<std::vector<BindingTable>>{
        {}, {{{}, {}}}, {{{0, 0}, {{int64_t(1), int64_t(1)}}}},
        {{{1, 0}, {{int64_t(1), int64_t(1)}}}}, {{{0, 2}, {}}},
        {{{0, 1}, {{int64_t(1)}}}}, {{{0}, {{int64_t(1)}}}}
    }) {
        EXPECT_THROW(join.has_match(tables), std::invalid_argument);
    }
}

TEST(CorrelatedRowsTest, an_earlier_present_zone_suppresses_a_later_matching_value) {
    BindingTable presence{{0, 1}, {{"sku1"sv, "A"sv}, {"sku1"sv, "B"sv}, {"sku2"sv, "B"sv}}};
    BindingTable candidates{{0, 1}, {{"sku1"sv, "B"sv}, {"sku2"sv, "B"sv}}};
    std::vector<BindingValue> priority{"A"sv, "B"sv};
    select_first_present_rows(presence, candidates, 1, priority);
    EXPECT_EQ(candidates.rows, (std::vector<BindingRow>{{"sku2"sv, "B"sv}}));
}

TEST(CorrelatedRowsTest, fallback_uses_each_signals_presence_and_not_another_signals_zone) {
    BindingTable presence{{0, 1, 2}, {{"sku"sv, "A"sv, "price"sv}, {"sku"sv, "B"sv, "size"sv}}};
    BindingTable candidates = presence;
    std::vector<BindingValue> priority{"A"sv, "B"sv};
    select_first_present_rows(presence, candidates, 1, priority);
    EXPECT_EQ(candidates.rows, presence.rows);
}

TEST(CorrelatedRowsTest, fallback_rejects_unlisted_zones_and_units_without_presence) {
    BindingTable presence{{0, 1}, {{"sku1"sv, "Z"sv}}};
    BindingTable candidates{{0, 1}, {{"sku1"sv, "A"sv}, {"sku2"sv, "B"sv}}};
    std::vector<BindingValue> priority{"A"sv, "B"sv};
    select_first_present_rows(presence, candidates, 1, priority);
    EXPECT_TRUE(candidates.rows.empty());
}

TEST(CorrelatedRowsTest, fallback_preserves_first_occurrence_priority_when_zones_repeat) {
    BindingTable presence{{0, 1}, {{int64_t(1), "B"sv}, {int64_t(1), "A"sv}, {int64_t(1), "A"sv}}};
    BindingTable candidates{{0, 1}, {{int64_t(1), "B"sv}, {int64_t(1), "A"sv}}};
    std::vector<BindingValue> priority{"A"sv, "B"sv, "A"sv};
    select_first_present_rows(presence, candidates, 1, priority);
    EXPECT_EQ(candidates.rows, (std::vector<BindingRow>{{int64_t(1), "A"sv}}));
}

TEST(CorrelatedRowsTest, empty_priority_or_presence_cannot_match) {
    BindingTable presence{{0, 1}, {{int64_t(1), "A"sv}}};
    BindingTable candidates = presence;
    select_first_present_rows(presence, candidates, 1, {});
    EXPECT_TRUE(candidates.rows.empty());
    candidates = presence;
    presence.rows.clear();
    std::vector<BindingValue> priority{"A"sv};
    select_first_present_rows(presence, candidates, 1, priority);
    EXPECT_TRUE(candidates.rows.empty());
}

TEST(CorrelatedRowsTest, fallback_rejects_malformed_tables_and_an_unbound_priority_variable) {
    BindingTable presence{{0, 1}, {{int64_t(1), "A"sv}}};
    BindingTable candidates = presence;
    std::vector<BindingValue> priority{"A"sv};
    EXPECT_THROW(select_first_present_rows(presence, candidates, 2, priority), std::invalid_argument);
    candidates.columns = {0};
    EXPECT_THROW(select_first_present_rows(presence, candidates, 1, priority), std::invalid_argument);
    candidates = presence;
    candidates.rows[0].pop_back();
    EXPECT_THROW(select_first_present_rows(presence, candidates, 1, priority), std::invalid_argument);
    candidates = presence;
    presence.rows[0].pop_back();
    EXPECT_THROW(select_first_present_rows(presence, candidates, 1, priority), std::invalid_argument);
}

TEST(CorrelatedRowsTest, rejects_disjoint_shared_values_at_a_check_instead_of_expanding_independent_variables) {
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= 4; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t zone = 0; zone < 30; ++zone) table.rows.push_back({int64_t(1), zone});
        tables.push_back(table);
        tables.push_back(std::move(table));
    }
    BindingTable absent{{0, 5}, {}};
    for (int64_t value = 0; value < 1000; ++value) absent.rows.push_back({int64_t(2), value});
    tables.push_back(absent);
    tables.push_back(std::move(absent));
    CorrelatedRows join(6);
    // Without the check, each join would try the 30^4 zone combinations of unit 1.
    // Checking for a match checks early, collecting matches checks late.
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_LT(join.visited_rows(), 2 * CorrelatedRows::shared_values_check_rows);
    EXPECT_TRUE(join.find_matches(tables, std::vector<uint32_t>{0}).empty());
    EXPECT_LT(join.visited_rows(), CorrelatedRows::deadline_check_interval + CorrelatedRows::shared_values_check_rows);
    std::reverse(tables.begin(), tables.end());
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_LT(join.visited_rows(), CorrelatedRows::deadline_check_interval + CorrelatedRows::shared_values_check_rows);
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, a_join_with_common_shared_values_continues_after_the_check_and_finds_every_match) {
    // Three clauses share unit 1 and bind 12 zones each, so the join has 12^3 matches.
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= 3; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t zone = 0; zone < 12; ++zone) table.rows.push_back({int64_t(1), zone});
        tables.push_back(std::move(table));
    }
    CorrelatedRows join(4);
    const auto& matches = join.find_matches(tables);
    EXPECT_EQ(12u * 12u * 12u, matches.size());
    EXPECT_GT(join.visited_rows(), CorrelatedRows::deadline_check_interval);
    EXPECT_EQ(matches, reference_join(tables, 4));
}

TEST(CorrelatedRowsTest, a_small_join_rejects_shared_values_without_a_common_value_before_any_check) {
    std::vector<BindingTable> tables{
        {{0, 1}, {{"a"sv, int64_t(1)}, {"b"sv, int64_t(1)}}},
        {{0, 1}, {{"a"sv, int64_t(1)}, {"b"sv, int64_t(1)}}},
        {{0, 2}, {{"b"sv, int64_t(2)}, {"c"sv, int64_t(2)}}},
        {{0, 2}, {{"b"sv, int64_t(2)}, {"c"sv, int64_t(2)}}},
        {{0}, {{"a"sv}, {"c"sv}}}
    };
    CorrelatedRows join(3);
    // A small join finds this out by itself, before any check of the shared values.
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_LT(join.visited_rows(), CorrelatedRows::deadline_check_interval);
    tables[4].rows.push_back({"b"sv});
    EXPECT_EQ((std::vector<BindingRow>{{"b"sv, int64_t(1), int64_t(2)}}), join.find_matches(tables));
}

TEST(CorrelatedRowsTest, a_projected_unit_needs_a_complete_cyclic_join) {
    std::vector<BindingTable> tables{
        {{0, 1, 2}, {{int64_t(7), int64_t(0), int64_t(1)}, {int64_t(7), int64_t(1), int64_t(0)}}},
        {{0, 2, 3}, {{int64_t(7), int64_t(0), int64_t(1)}, {int64_t(7), int64_t(1), int64_t(0)}}},
        {{0, 1, 3}, {{int64_t(7), int64_t(0), int64_t(1)}, {int64_t(7), int64_t(1), int64_t(0)}}}
    };
    CorrelatedRows join(4);
    std::vector<uint32_t> unit{0};
    // Each pair has unequal values, which cannot hold for all three pairs at once.
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_TRUE(join.find_matches(tables, unit).empty());
    tables[2].rows = {{int64_t(7), int64_t(0), int64_t(0)}, {int64_t(7), int64_t(1), int64_t(1)}};
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(7)}}), join.find_matches(tables, unit));
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(7), int64_t(0), int64_t(1), int64_t(0)},
                                      {int64_t(7), int64_t(1), int64_t(0), int64_t(1)}}),
              join.find_matches(tables));
    std::reverse(tables.begin(), tables.end());
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(7)}}), join.find_matches(tables, unit));
}

TEST(CorrelatedRowsTest, an_unsatisfiable_cycle_runs_to_completion_before_the_deadline) {
    auto tables = unsatisfiable_cycle(64);
    TestDoom doom(std::chrono::hours(1), std::chrono::hours(1));
    CorrelatedRows join(4, doom.doom);
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    // Each of the 2048 rows of the first clause looks up the 32 rows of the second clause.
    EXPECT_EQ(2048u + 2048u * 32u, join.visited_rows());
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, checking_for_a_match_stops_at_the_soft_deadline) {
    auto tables = unsatisfiable_cycle(64);
    TestDoom doom(-std::chrono::seconds(1), std::chrono::hours(1));
    CorrelatedRows join(4, doom.doom);
    EXPECT_FALSE(join.has_match(tables));
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(CorrelatedRows::deadline_check_interval, join.visited_rows());
}

TEST(CorrelatedRowsTest, collecting_matches_ignores_a_passed_soft_deadline) {
    auto tables = unsatisfiable_cycle(64);
    TestDoom doom(-std::chrono::seconds(1), std::chrono::hours(1));
    CorrelatedRows join(4, doom.doom);
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    EXPECT_EQ(2048u + 2048u * 32u, join.visited_rows());
}

TEST(CorrelatedRowsTest, collecting_matches_after_the_hard_deadline_returns_no_partial_matches_but_checking_for_a_match_finds_the_first) {
    // One unit with 5 zones in each of 12 clauses has 5^12 matches, so the join stops partway.
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= 12; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t zone = 0; zone < 5; ++zone) table.rows.push_back({int64_t(7), zone});
        tables.push_back(std::move(table));
    }
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    CorrelatedRows join(13, doom.doom);
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(CorrelatedRows::deadline_check_interval, join.visited_rows());
    // Checking for a match stops at the first complete binding, before the next deadline check.
    EXPECT_TRUE(join.has_match(tables));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, after_the_hard_deadline_collecting_1023_rows_completes_and_1024_rows_stop) {
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    for (int64_t rows : {1023, 1024}) {
        SCOPED_TRACE(rows);
        std::vector<BindingTable> tables{{{0, 1}, {}}};
        for (int64_t unit = 0; unit < rows; ++unit) tables[0].rows.push_back({unit, int64_t(0)});
        CorrelatedRows join(2, doom.doom);
        EXPECT_EQ(rows == 1023 ? 1023u : 0u, join.find_matches(tables).size());
        EXPECT_EQ(rows == 1023 ? CorrelationStop::none : CorrelationStop::deadline, join.stop_reason());
        EXPECT_EQ(size_t(rows), join.visited_rows());
    }
}

TEST(CorrelatedRowsTest, the_deadline_is_checked_across_many_small_joins) {
    // Each join visits 600 rows, fewer than the check interval, so only the
    // count across joins reaches a check: the second join stops at row 1024.
    std::vector<BindingTable> tables{{{0}, {}}};
    for (int64_t unit = 0; unit < 600; ++unit) tables[0].rows.push_back({unit});
    std::vector<uint32_t> unit{0};
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    CorrelatedRows join(1, doom.doom);
    EXPECT_EQ(600u, join.find_matches(tables, unit).size());
    EXPECT_TRUE(join.find_matches(tables, unit).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(1024u - 600u, join.visited_rows());
}

TEST(CorrelatedRowsTest, a_stopped_join_does_not_affect_the_next_evaluation) {
    auto cycle = unsatisfiable_cycle(64);
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    CorrelatedRows join(4, doom.doom);
    EXPECT_FALSE(join.has_match(cycle));
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    std::vector<BindingTable> small{{{0, 1, 2}, {{int64_t(7), int64_t(0), int64_t(0)}}},
                                    {{0, 2, 3}, {{int64_t(7), int64_t(0), int64_t(0)}}}};
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(7), int64_t(0), int64_t(0), int64_t(0)}}), join.find_matches(small));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, distinct_matches_may_fill_the_memory_limit_but_not_pass_it) {
    std::vector<BindingTable> tables{{{0, 1}, {}}};
    for (int64_t unit = 0; unit < 10; ++unit) tables[0].rows.push_back({unit, int64_t(0)});
    CorrelatedRows at_limit(2, vespalib::Doom::never(), memory_of_rows(10, 2));
    EXPECT_EQ(10u, at_limit.find_matches(tables).size());
    EXPECT_EQ(CorrelationStop::none, at_limit.stop_reason());
    CorrelatedRows below_limit(2, vespalib::Doom::never(), memory_of_rows(10, 2) - 1);
    EXPECT_TRUE(below_limit.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::memory_limit, below_limit.stop_reason());
    // Checking for a match collects nothing, so the memory limit does not apply.
    EXPECT_TRUE(below_limit.has_match(tables));
    EXPECT_EQ(CorrelationStop::none, below_limit.stop_reason());
}

TEST(CorrelatedRowsTest, duplicate_matches_are_removed_before_the_memory_limit_stops_a_join) {
    // Both clauses bind unit 7 with 10 shared zones. Projected to the unit, the
    // join finds unit 7 once per zone, which is 10 matches but one distinct match.
    std::vector<BindingTable> tables{{{0, 1}, {}}, {{0, 1}, {}}};
    for (int64_t zone = 0; zone < 10; ++zone) {
        for (auto& table : tables) table.rows.push_back({int64_t(7), zone});
    }
    std::vector<uint32_t> unit{0};
    CorrelatedRows join(2, vespalib::Doom::never(), memory_of_rows(3, 1));
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(7)}}), join.find_matches(tables, unit));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, independent_zones_pass_the_memory_limit_but_a_unit_projection_still_works) {
    constexpr uint32_t zones = 12;
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= zones; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t unit = 1; unit <= 100; ++unit) {
            for (int64_t zone = 0; zone < 5; ++zone) table.rows.push_back({unit, zone});
        }
        tables.push_back(std::move(table));
    }
    // Every unit has 5^12 combinations of zones, far more than 1000 rows.
    CorrelatedRows join(zones + 1, vespalib::Doom::never(), memory_of_rows(1000, zones + 1));
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::memory_limit, join.stop_reason());
    // The join stops soon after 1000 matches instead of enumerating every combination.
    EXPECT_LT(join.visited_rows(), 2000u);
    std::vector<uint32_t> unit{0};
    EXPECT_EQ(100u, join.find_matches(tables, unit).size());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, valid_queries_work_after_rejected_tables_and_projections) {
    std::vector<BindingTable> tables{{{0, 1}, {{int64_t(7), "A"sv}}}, {{0}, {{int64_t(7)}}}};
    CorrelatedRows join(2);
    const std::vector<BindingRow> expected{{int64_t(7), "A"sv}};
    EXPECT_EQ(expected, join.find_matches(tables));
    auto malformed = tables;
    malformed[1].rows[0].push_back("extra"sv);
    EXPECT_THROW(join.find_matches(malformed), std::invalid_argument);
    EXPECT_EQ(expected, join.find_matches(tables));
    for (const auto& projection : {std::vector<uint32_t>{0, 0}, {2}}) {
        EXPECT_THROW(join.find_matches(tables, projection), std::invalid_argument);
        EXPECT_EQ(expected, join.find_matches(tables));
    }
}

TEST(CorrelatedRowsTest, agrees_with_cartesian_product_oracle_on_random_catalogues_and_clause_orders) {
    std::mt19937 random(104729);
    const std::vector<BindingValue> values{int64_t(-1), int64_t(0), int64_t(1),
                                          std::numeric_limits<int64_t>::max(), ""sv, "A"sv, "a"sv, "å"sv};
    auto value = [&] { return values[random() % values.size()]; };
    for (size_t run = 0; run < 3000; ++run) {
        SCOPED_TRACE(run);
        std::vector<BindingTable> tables;
        uint32_t column_count = 1 + random() % 5;
        size_t clause_count = 1 + random() % 5;
        for (size_t i = 0; i < clause_count; ++i) {
            BindingTable table;
            for (uint32_t c = 0; c < column_count; ++c) {
                if (random() % 2) table.columns.push_back(c);
            }
            if (table.columns.empty()) table.columns.push_back(random() % column_count);
            size_t row_count = random() % 5;
            for (size_t r = 0; r < row_count; ++r) {
                BindingRow row;
                for (size_t c = 0; c < table.columns.size(); ++c) row.push_back(value());
                table.rows.push_back(std::move(row));
            }
            tables.push_back(std::move(table));
        }
        // Bind missing variables without forcing one clause to contain every variable.
        for (uint32_t column = 0; column < column_count; ++column) {
            if (std::none_of(tables.begin(), tables.end(), [column](const auto& table) {
                    return std::binary_search(table.columns.begin(), table.columns.end(), column);
                })) {
                auto& table = tables[random() % tables.size()];
                auto position = std::lower_bound(table.columns.begin(), table.columns.end(), column);
                size_t offset = position - table.columns.begin();
                table.columns.insert(position, column);
                for (auto& row : table.rows) row.insert(row.begin() + offset, value());
            }
        }
        auto expected = reference_join(tables, column_count);
        CorrelatedRows join(column_count);
        EXPECT_EQ(join.has_match(tables), !expected.empty());
        EXPECT_EQ(join.find_matches(tables), expected);
        for (uint32_t mask = 0; mask < (1u << column_count); ++mask) {
            SCOPED_TRACE(mask);
            std::vector<uint32_t> projection;
            for (uint32_t column = 0; column < column_count; ++column)
                if (mask & (1u << column)) projection.push_back(column);
            if (run % 2) std::reverse(projection.begin(), projection.end());
            std::vector<BindingRow> projected;
            for (const auto& binding : expected) {
                BindingRow row;
                for (uint32_t column : projection) row.push_back(binding[column]);
                projected.push_back(std::move(row));
            }
            std::sort(projected.begin(), projected.end());
            projected.erase(std::unique(projected.begin(), projected.end()), projected.end());
            EXPECT_EQ(join.find_matches(tables, projection), projected);
        }
        std::shuffle(tables.begin(), tables.end(), random);
        EXPECT_EQ(join.has_match(tables), !expected.empty());
        EXPECT_EQ(join.find_matches(tables), expected);
    }
}

namespace {

// Two clauses that bind the same variable to disjoint values, so the first clause
// visits all of its rows and the join finds nothing.
std::vector<BindingTable> disjoint_units(int64_t rows) {
    std::vector<BindingTable> tables{{{0}, {}}, {{0}, {}}};
    for (int64_t unit = 0; unit < rows; ++unit) {
        tables[0].rows.push_back({unit});
        tables[1].rows.push_back({unit + rows});
    }
    return tables;
}

// One clause with the given number of distinct rows over all variables.
std::vector<BindingTable> distinct_rows(int64_t rows, uint32_t columns) {
    std::vector<BindingTable> tables{{{}, {}}};
    for (uint32_t column = 0; column < columns; ++column) tables[0].columns.push_back(column);
    for (int64_t row = 0; row < rows; ++row) tables[0].rows.push_back(BindingRow(columns, row));
    return tables;
}

// Ten clauses share unit 1, and eight of them bind 30 zones each. The last two
// bind the unit `last_unit` with 1000 other values. With last_unit 2 the unit
// has no common value, with last_unit 1 every clause agrees on unit 1.
std::vector<BindingTable> shared_unit_tables(int64_t last_unit) {
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= 4; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t zone = 0; zone < 30; ++zone) table.rows.push_back({int64_t(1), zone});
        tables.push_back(table);
        tables.push_back(std::move(table));
    }
    BindingTable last{{0, 5}, {}};
    for (int64_t value = 0; value < 1000; ++value) last.rows.push_back({last_unit, value});
    tables.push_back(last);
    tables.push_back(std::move(last));
    return tables;
}

} // namespace

// A join rejected for having no common shared value must not reject the next document.
TEST(CorrelatedRowsTest, a_join_without_a_common_shared_value_does_not_reject_the_next_join) {
    CorrelatedRows join(6);
    auto disjoint = shared_unit_tables(2);
    EXPECT_FALSE(join.has_match(disjoint));
    EXPECT_EQ(CorrelatedRows::shared_values_check_rows, join.visited_rows());
    auto overlapping = shared_unit_tables(1);
    EXPECT_TRUE(join.has_match(overlapping));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(1)}}), join.find_matches(overlapping, std::vector<uint32_t>{0}));
    EXPECT_FALSE(join.has_match(disjoint));
    EXPECT_TRUE(join.has_match(overlapping));
}

// Checking for a match checks the shared values at the first check point after
// 64 rows of this join. Check points come every 64 rows counted across joins, so
// earlier joins of the same object decide which row that is.
TEST(CorrelatedRowsTest, checking_for_a_match_checks_shared_values_at_the_first_check_point_after_64_rows) {
    auto disjoint = shared_unit_tables(2);
    // Earlier rows, and the row of this join where the check runs.
    for (auto [earlier, expected] : std::vector<std::pair<int64_t, size_t>>{{0, 64}, {960, 64}, {1023, 65}, {1000, 88}}) {
        SCOPED_TRACE(earlier);
        CorrelatedRows join(6);
        auto filler = distinct_rows(earlier, 6);
        if (earlier > 0) {
            EXPECT_EQ(size_t(earlier), join.find_matches(filler).size());
        }
        EXPECT_FALSE(join.has_match(disjoint));
        EXPECT_EQ(expected, join.visited_rows());
        EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    }
}

// Collecting matches reads documents that already matched, so it checks the
// shared values only after 1024 rows of this join.
TEST(CorrelatedRowsTest, collecting_matches_checks_shared_values_after_1024_rows) {
    auto disjoint = shared_unit_tables(2);
    CorrelatedRows join(6);
    EXPECT_TRUE(join.find_matches(disjoint).empty());
    EXPECT_EQ(CorrelatedRows::deadline_check_interval, join.visited_rows());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

// When the shared values and the deadline are due at the same row, a join that
// cannot match reports no match without a deadline stop.
TEST(CorrelatedRowsTest, a_join_without_a_common_shared_value_reports_no_deadline_stop_when_both_checks_are_due) {
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    CorrelatedRows join(6, doom.doom);
    // 960 earlier rows put row 1024 at the 64th row of the next join.
    auto filler = distinct_rows(960, 6);
    EXPECT_EQ(960u, join.find_matches(filler).size());
    auto disjoint = shared_unit_tables(2);
    EXPECT_FALSE(join.has_match(disjoint));
    EXPECT_EQ(CorrelatedRows::shared_values_check_rows, join.visited_rows());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, after_the_deadline_joins_of_1023_rows_complete_and_joins_of_1024_or_1025_rows_stop_at_row_1024) {
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    for (int64_t rows : {1023, 1024, 1025}) {
        SCOPED_TRACE(rows);
        auto tables = disjoint_units(rows);
        auto expected_stop = rows == 1023 ? CorrelationStop::none : CorrelationStop::deadline;
        CorrelatedRows checking(1, doom.doom);
        EXPECT_FALSE(checking.has_match(tables));
        EXPECT_EQ(expected_stop, checking.stop_reason());
        EXPECT_EQ(size_t(std::min<int64_t>(rows, 1024)), checking.visited_rows());
        CorrelatedRows collecting(1, doom.doom);
        auto one_clause = distinct_rows(rows, 1);
        EXPECT_EQ(rows == 1023 ? 1023u : 0u, collecting.find_matches(one_clause).size());
        EXPECT_EQ(expected_stop, collecting.stop_reason());
        EXPECT_EQ(size_t(std::min<int64_t>(rows, 1024)), collecting.visited_rows());
    }
}

// The deadline passes between joins of the same object, and each check comes
// 1024 rows after the previous one, also in the middle of a join.
TEST(CorrelatedRowsTest, a_deadline_that_passes_between_joins_stops_the_next_join_at_the_next_check) {
    TestDoom doom(std::chrono::hours(1), std::chrono::hours(1));
    CorrelatedRows join(1, doom.doom);
    auto large = distinct_rows(1000, 1);
    EXPECT_EQ(1000u, join.find_matches(large).size());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    doom.now = doom.now.load() + std::chrono::hours(2);
    auto small = disjoint_units(100);
    EXPECT_FALSE(join.has_match(small));
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(24u, join.visited_rows());
    // The next check is at row 2048, which this join does not reach.
    EXPECT_TRUE(join.find_matches(small).empty());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    EXPECT_EQ(100u, join.visited_rows());
    // This join reaches row 2048 at its 924th row.
    EXPECT_TRUE(join.find_matches(large).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(924u, join.visited_rows());
}

// A document with a single row can still be the one that reaches a check.
TEST(CorrelatedRowsTest, after_the_soft_deadline_a_one_row_join_at_row_1024_stops_and_the_next_one_matches) {
    TestDoom doom(-std::chrono::seconds(1), std::chrono::hours(1));
    CorrelatedRows join(1, doom.doom);
    auto filler = distinct_rows(1023, 1);
    EXPECT_EQ(1023u, join.find_matches(filler).size());
    std::vector<BindingTable> one_row{{{0}, {{int64_t(7)}}}, {{0}, {{int64_t(7)}}}};
    EXPECT_FALSE(join.has_match(one_row));
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    EXPECT_EQ(1u, join.visited_rows());
    EXPECT_TRUE(join.has_match(one_row));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

// The memory limit always leaves room for one match.
TEST(CorrelatedRowsTest, a_memory_limit_of_0_or_1_byte_keeps_one_match_and_stops_at_the_second) {
    for (size_t memory : {size_t(0), size_t(1)}) {
        SCOPED_TRACE(memory);
        CorrelatedRows join(1, vespalib::Doom::never(), memory);
        EXPECT_EQ(memory, join.memory_limit());
        std::vector<BindingTable> one{{{0}, {{int64_t(7)}}}};
        EXPECT_EQ((std::vector<BindingRow>{{int64_t(7)}}), join.find_matches(one));
        EXPECT_EQ(CorrelationStop::none, join.stop_reason());
        std::vector<BindingTable> two{{{0}, {{int64_t(7)}, {int64_t(8)}}}};
        EXPECT_TRUE(join.find_matches(two).empty());
        EXPECT_EQ(CorrelationStop::memory_limit, join.stop_reason());
        // Checking for a match collects nothing, so the limit does not apply.
        EXPECT_TRUE(join.has_match(two));
        EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    }
}

// The limit counts the projected variables. When it is full, the join removes
// duplicates and continues while at most half of the limit is used.
TEST(CorrelatedRowsTest, a_full_memory_limit_continues_with_half_distinct_matches_and_stops_with_more) {
    // Room for 4 projected units. Both clauses bind the same unit and zone rows,
    // and each row adds its unit, in unit order.
    auto tables_for = [](std::vector<std::pair<int64_t, int64_t>> unit_zones) {
        std::vector<BindingTable> tables{{{0, 1}, {}}, {{0, 1}, {}}};
        for (auto [unit, zone] : unit_zones) {
            for (auto& table : tables) table.rows.push_back({unit, zone});
        }
        return tables;
    };
    std::vector<uint32_t> unit{0};
    CorrelatedRows join(2, vespalib::Doom::never(), memory_of_rows(4, 1));
    // Units 1, 1, 2, 2 fill the limit with 2 distinct units, exactly half, so unit 3 is added.
    auto half = tables_for({{1, 0}, {1, 1}, {2, 0}, {2, 1}, {3, 0}});
    EXPECT_EQ((std::vector<BindingRow>{{int64_t(1)}, {int64_t(2)}, {int64_t(3)}}), join.find_matches(half, unit));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    // Units 1, 1, 2, 3 fill the limit with 3 distinct units, more than half, so the join stops.
    auto more = tables_for({{1, 0}, {1, 1}, {2, 0}, {3, 0}, {4, 0}});
    EXPECT_TRUE(join.find_matches(more, unit).empty());
    EXPECT_EQ(CorrelationStop::memory_limit, join.stop_reason());
}

// Every evaluation reports its own stop reason, also one that returns before joining.
TEST(CorrelatedRowsTest, after_a_stopped_join_an_empty_clause_or_a_join_of_single_clause_variables_reports_no_stop) {
    auto cycle = unsatisfiable_cycle(64);
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    CorrelatedRows join(4, doom.doom);
    EXPECT_TRUE(join.find_matches(cycle).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    std::vector<BindingTable> empty_clause{{{0, 1, 2, 3}, {{int64_t(7), int64_t(0), int64_t(0), int64_t(0)}}},
                                           {{0}, {}}};
    EXPECT_FALSE(join.has_match(empty_clause));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
    EXPECT_TRUE(join.find_matches(cycle).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    std::vector<BindingTable> single{{{0, 1, 2, 3}, {{int64_t(7), int64_t(0), int64_t(0), int64_t(0)}}}};
    EXPECT_TRUE(join.has_match(single));
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, a_join_can_stop_at_the_memory_limit_then_at_the_deadline_and_then_complete) {
    constexpr uint32_t zones = 12;
    std::vector<BindingTable> tables;
    for (uint32_t column = 1; column <= zones; ++column) {
        BindingTable table{{0, column}, {}};
        for (int64_t unit = 1; unit <= 100; ++unit) {
            for (int64_t zone = 0; zone < 5; ++zone) table.rows.push_back({unit, zone});
        }
        tables.push_back(std::move(table));
    }
    TestDoom doom(std::chrono::hours(1), std::chrono::hours(1));
    CorrelatedRows join(zones + 1, doom.doom, memory_of_rows(1000, zones + 1));
    EXPECT_TRUE(join.find_matches(tables).empty());
    EXPECT_EQ(CorrelationStop::memory_limit, join.stop_reason());
    doom.now = doom.now.load() + std::chrono::hours(2);
    // The unit projection fits in memory, but needs more rows than one check interval.
    std::vector<uint32_t> unit{0};
    EXPECT_TRUE(join.find_matches(tables, unit).empty());
    EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
    auto one_row = distinct_rows(1, zones + 1);
    EXPECT_EQ(1u, join.find_matches(one_row).size());
    EXPECT_EQ(CorrelationStop::none, join.stop_reason());
}

TEST(CorrelatedRowsTest, after_the_hard_deadline_1023_rows_of_extreme_and_non_ascii_values_complete_in_projection_order_and_1024_rows_stop) {
    using Limits = std::numeric_limits<int64_t>;
    const std::vector<BindingValue> values{Limits::min(), Limits::max(), int64_t(0), ""sv, "北"sv, "å"sv, "A"sv};
    TestDoom doom(-std::chrono::seconds(2), -std::chrono::seconds(1));
    std::vector<uint32_t> value_then_unit{1, 0};
    for (int64_t rows : {1023, 1024}) {
        SCOPED_TRACE(rows);
        std::vector<BindingTable> tables{{{0, 1}, {}}};
        std::vector<BindingRow> expected;
        for (int64_t unit = 0; unit < rows; ++unit) {
            const auto& value = values[unit % values.size()];
            tables[0].rows.push_back({unit, value});
            expected.push_back({value, unit});
        }
        std::sort(expected.begin(), expected.end());
        CorrelatedRows join(2, doom.doom);
        const auto& matches = join.find_matches(tables, value_then_unit);
        if (rows == 1023) {
            EXPECT_EQ(expected, matches);
            EXPECT_EQ(CorrelationStop::none, join.stop_reason());
        } else {
            EXPECT_TRUE(matches.empty());
            EXPECT_EQ(CorrelationStop::deadline, join.stop_reason());
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
