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

std::vector<Layout> add_layouts = {
    {x({"a"})},                           {x({"b"})},
    {x({"a","b"})},                       {x({"a","c"})},
    float_cells({x({"a","b"})}),          {x({"a","c"})},
    {x({"a","b"})},                       float_cells({x({"a","c"})}),
    float_cells({x({"a","b"})}),          float_cells({x({"a","c"})}),
    {x({"a","b","c"}),y({"d","e"})},      {x({"b","f"}),y({"d","g"})},             
    {x(3),y({"a","b"})},                  {x(3),y({"b","c"})}
};

TensorSpec reference_add(const TensorSpec &a, const TensorSpec &b) {
    TensorSpec result(a.type());
    for (const auto &cell: b.cells()) {
        result.add(cell.first, cell.second);
    }
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        auto iter = b.cells().find(cell.first);
        if (iter == end_iter) {
            result.add(cell.first, cell.second);
        }
    }
    return result;
}

Value::UP try_partial_add(const TensorSpec &a, const TensorSpec &b) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    return TensorPartialUpdate::add(*lhs, *rhs, factory);
}

TensorSpec perform_partial_add(const TensorSpec &a, const TensorSpec &b) {
    auto up = try_partial_add(a, b);
    EXPECT_TRUE(up);
    return spec_from_value(*up);
}

TEST(PartialAddTest, partial_add_works_for_simple_values) {
    ASSERT_TRUE((add_layouts.size() % 2) == 0);
    for (size_t i = 0; i < add_layouts.size(); i += 2) {
        TensorSpec lhs = spec(add_layouts[i], N());
        TensorSpec rhs = spec(add_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto expect = reference_add(lhs, rhs);
        auto actual = perform_partial_add(lhs, rhs);
        EXPECT_EQ(actual, expect);
    }
}

std::vector<Layout> bad_layouts = {
    {x(3)},                               {x(3),y(1)},
    {x(3),y(1)},                          {x(3)},
    {x(3),y(3)},                          {x(3),y({"a"})},
    {x(3),y({"a"})},                      {x(3),y(3)},
    {x({"a"})},                           {x({"a"}),y({"b"})},
    {x({"a"}),y({"b"})},                  {x({"a"})},
    {x({"a"})},                           {x({"a"}),y(1)}
};

TEST(PartialAddTest, partial_add_returns_nullptr_on_invalid_inputs) {
    ASSERT_TRUE((bad_layouts.size() % 2) == 0);
    for (size_t i = 0; i < bad_layouts.size(); i += 2) {
        TensorSpec lhs = spec(bad_layouts[i], N());
        TensorSpec rhs = spec(bad_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto actual = try_partial_add(lhs, rhs);
        auto expect = Value::UP();
        EXPECT_EQ(actual, expect);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
