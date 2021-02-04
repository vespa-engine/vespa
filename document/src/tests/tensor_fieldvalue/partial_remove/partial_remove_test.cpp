// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/tensor_model.h>
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

std::vector<Layout> remove_layouts = {
    {x({"a"})},                           {x({"b"})},
    {x({"a","b"})},                       {x({"a","c"})},
    {x({"a","b"})},                       {x({"a","b"})},
    float_cells({x({"a","b"})}),          {x({"a","c"})},
    {x({"a","b"})},                       float_cells({x({"a","c"})}),
    float_cells({x({"a","b"})}),          float_cells({x({"a","c"})}),
    {x({"a","b","c"}),y({"d","e"})},      {x({"b","f"}),y({"d","g"})},             
    {x(3),y({"a","b"})},                  {y({"b","c"})}
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
    return result;
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
    ASSERT_TRUE((remove_layouts.size() % 2) == 0);
    for (size_t i = 0; i < remove_layouts.size(); i += 2) {
        TensorSpec lhs = spec(remove_layouts[i], N());
        TensorSpec rhs = spec(remove_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto expect = reference_remove(lhs, rhs);
        auto actual = perform_partial_remove(lhs, rhs);
        EXPECT_EQ(actual, expect);
    }
}

std::vector<Layout> bad_layouts = {
    {x(3)},                               {x(3)},
    {x(3),y({"a"})},                      {x(3)},
    {x(3),y({"a"})},                      {x(3),y({"a"})},
    {x({"a"})},                           {y({"a"})},
    {x({"a"})},                           {x({"a"}),y({"b"})}
};

TEST(PartialRemoveTest, partial_remove_returns_nullptr_on_invalid_inputs) {
    ASSERT_TRUE((bad_layouts.size() % 2) == 0);
    for (size_t i = 0; i < bad_layouts.size(); i += 2) {
        TensorSpec lhs = spec(bad_layouts[i], N());
        TensorSpec rhs = spec(bad_layouts[i + 1], Div16(N()));
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
