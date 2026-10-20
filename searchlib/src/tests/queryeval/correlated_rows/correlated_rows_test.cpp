// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/correlated_rows.h>
#include <vespa/vespalib/gtest/gtest.h>

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

TEST(CorrelatedRowsTest, agrees_with_cartesian_product_oracle_on_random_catalogues_and_clause_orders) {
    std::mt19937 random(104729);
    for (size_t run = 0; run < 3000; ++run) {
        SCOPED_TRACE(run);
        std::vector<BindingTable> tables;
        uint32_t column_count = 1 + random() % 3;
        size_t clause_count = 2 + random() % 3;
        for (size_t i = 0; i < clause_count; ++i) {
            BindingTable table;
            for (uint32_t c = 0; c < column_count; ++c) {
                if (i == 0 || c == 0 || random() % 2) table.columns.push_back(c);
            }
            size_t row_count = random() % 6;
            for (size_t r = 0; r < row_count; ++r) {
                BindingRow row;
                for (size_t c = 0; c < table.columns.size(); ++c) row.emplace_back(int64_t(random() % 4));
                table.rows.push_back(std::move(row));
            }
            tables.push_back(std::move(table));
        }
        // Removing column 2 from the first clause exercises joins without a full anchor.
        if (column_count == 3 && run % 2 && tables.size() >= 2) {
            tables[0].columns.pop_back();
            for (auto& row : tables[0].rows) row.pop_back();
            if (tables[1].columns.back() != 2) {
                tables[1].columns.push_back(2);
                for (auto& row : tables[1].rows) row.emplace_back(int64_t(random() % 4));
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

GTEST_MAIN_RUN_ALL_TESTS()
