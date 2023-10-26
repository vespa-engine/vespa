// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/document/update/tensor_partial_update.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace document;
using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<std::pair<vespalib::string,vespalib::string>> remove_layouts = {
    {     "x4_1",     "x4_2" },
    { "x4_2y4_1", "x4_1y4_2" },
    {   "x3y4_1",     "y4_2" }
};

TensorSpec::Address only_sparse(const TensorSpec::Address &input) {
    TensorSpec::Address output;
    for (const auto & kv : input) {
        if (kv.second.is_mapped()) {
            output.emplace(kv.first, kv.second);
        }
    }
    return output;
}

TensorSpec reference_remove(const TensorSpec &a, const TensorSpec &b) {
    TensorSpec result(a.type());
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        auto iter = b.cells().find(only_sparse(cell.first));
        if (iter == end_iter) {
            result.add(cell.first, cell.second);
        }
    }
    return result.normalize();
}

Value::UP try_partial_remove(const TensorSpec &a, const TensorSpec &b) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    return TensorPartialUpdate::remove(*lhs, *rhs, factory);
}

TensorSpec perform_partial_remove(const TensorSpec &a, const TensorSpec &b) {
    auto up = try_partial_remove(a, b);
    EXPECT_TRUE(up);
    return spec_from_value(*up);
}

TEST(PartialRemoveTest, partial_remove_works_for_simple_values) {
    for (const auto &layouts: remove_layouts) {
        for (auto lhs_ct: CellTypeUtils::list_types()) {
            for (auto rhs_ct: CellTypeUtils::list_types()) {
                TensorSpec lhs = GenSpec::from_desc(layouts.first).cells(lhs_ct).seq(N());
                TensorSpec rhs = GenSpec::from_desc(layouts.second).cells(rhs_ct).seq(Div16(N()));
                SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
                auto expect = reference_remove(lhs, rhs);
                auto actual = perform_partial_remove(lhs, rhs);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

std::vector<std::pair<vespalib::string,vespalib::string>> bad_layouts = {
    {      "x3",       "x3" },
    {  "x3y4_1",       "x3" },
    {  "x3y4_1",   "x3y4_2" },
    {    "x4_1",     "y4_1" },
    {    "x4_1", "x4_2y4_1" }
};

TEST(PartialRemoveTest, partial_remove_returns_nullptr_on_invalid_inputs) {
    for (const auto &layouts: bad_layouts) {
        TensorSpec lhs = GenSpec::from_desc(layouts.first).seq(N());
        TensorSpec rhs = GenSpec::from_desc(layouts.second).seq(Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto actual = try_partial_remove(lhs, rhs);
        auto expect = Value::UP();
        EXPECT_EQ(actual, expect);
    }
}

void
expect_partial_remove(const TensorSpec& input, const TensorSpec& remove, const TensorSpec& exp)
{
    auto act = perform_partial_remove(input, remove);
    EXPECT_EQ(exp, act);
}

TEST(PartialRemoveTest, remove_where_address_is_not_fully_specified) {
    auto input_sparse = TensorSpec("tensor(x{},y{})").
            add({{"x", "a"},{"y", "c"}}, 3.0).
            add({{"x", "a"},{"y", "d"}}, 5.0).
            add({{"x", "b"},{"y", "c"}}, 7.0);

    expect_partial_remove(input_sparse, TensorSpec("tensor(x{})").add({{"x", "a"}}, 1.0),
                          TensorSpec("tensor(x{},y{})").add({{"x", "b"},{"y", "c"}}, 7.0));

    expect_partial_remove(input_sparse, TensorSpec("tensor(y{})").add({{"y", "c"}}, 1.0),
                          TensorSpec("tensor(x{},y{})").add({{"x", "a"},{"y", "d"}}, 5.0));

    expect_partial_remove(input_sparse, TensorSpec("tensor(y{})").add({{"y", "d"}}, 1.0),
                          TensorSpec("tensor(x{},y{})").add({{"x", "a"},{"y", "c"}}, 3.0)
                                  .add({{"x", "b"},{"y", "c"}}, 7.0));

    auto input_mixed = TensorSpec("tensor(x{},y{},z[1])").
            add({{"x", "a"},{"y", "c"},{"z", 0}}, 3.0).
            add({{"x", "a"},{"y", "d"},{"z", 0}}, 5.0).
            add({{"x", "b"},{"y", "c"},{"z", 0}}, 7.0);

    expect_partial_remove(input_mixed,TensorSpec("tensor(x{})").add({{"x", "a"}}, 1.0),
                          TensorSpec("tensor(x{},y{},z[1])").add({{"x", "b"},{"y", "c"},{"z", 0}}, 7.0));

    expect_partial_remove(input_mixed, TensorSpec("tensor(y{})").add({{"y", "c"}}, 1.0),
                          TensorSpec("tensor(x{},y{},z[1])").add({{"x", "a"},{"y", "d"},{"z", 0}}, 5.0));
}

GTEST_MAIN_RUN_ALL_TESTS()
