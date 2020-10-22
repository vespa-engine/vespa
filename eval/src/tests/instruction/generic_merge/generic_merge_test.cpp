// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> merge_layouts = {
    {},                                                 {},
    {x(5)},                                             {x(5)},
    {x(3),y(5)},                                        {x(3),y(5)},
    float_cells({x(3),y(5)}),                           {x(3),y(5)},
    {x(3),y(5)},                                        float_cells({x(3),y(5)}),
    {x({"a","b","c"})},                                 {x({"a","b","c"})},
    {x({"a","b","c"})},                                 {x({"c","d","e"})},
    {x({"a","c","e"})},                                 {x({"b","c","d"})},
    {x({"b","c","d"})},                                 {x({"a","c","e"})},
    {x({"a","b","c"})},                                 {x({"c","d"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              {x({"b","c"}),y({"any","foo","bar"})},
    {x(3),y({"foo", "bar"})},                           {x(3),y({"baz", "bar"})},
    {x({"a","b","c"}),y(5)},                            {x({"b","c","d"}),y(5)}
};


TensorSpec reference_merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    ValueType res_type = ValueType::merge(ValueType::from_spec(a.type()),
                                          ValueType::from_spec(b.type()));
    EXPECT_FALSE(res_type.is_error());
    TensorSpec result(res_type.to_spec());
    for (const auto &cell: a.cells()) {
        auto other = b.cells().find(cell.first);
        if (other == b.cells().end()) {
            result.add(cell.first, cell.second);
        } else {
            result.add(cell.first, fun(cell.second, other->second));
        }
    }
    for (const auto &cell: b.cells()) {
        auto other = a.cells().find(cell.first);
        if (other == a.cells().end()) {
            result.add(cell.first, cell.second);
        }
    }
    return result;
}

TensorSpec perform_generic_merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun, const ValueBuilderFactory &factory) {
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto my_op = GenericMerge::make_instruction(lhs->type(), rhs->type(), fun, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs, *rhs})));
}

void test_generic_merge_with(const ValueBuilderFactory &factory) {
    ASSERT_TRUE((merge_layouts.size() % 2) == 0);
    for (size_t i = 0; i < merge_layouts.size(); i += 2) {
        TensorSpec lhs = spec(merge_layouts[i], N());
        TensorSpec rhs = spec(merge_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f, operation::Max::f}) {
            auto expect = reference_merge(lhs, rhs, fun);
            auto actual = perform_generic_merge(lhs, rhs, fun, factory);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(GenericMergeTest, generic_merge_works_for_simple_values) {
    test_generic_merge_with(SimpleValueBuilderFactory::get());
}

TEST(GenericMergeTest, generic_merge_works_for_fast_values) {
    test_generic_merge_with(FastValueBuilderFactory::get());
}

TensorSpec immediate_generic_merge(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto up = GenericMerge::perform_merge(*lhs, *rhs, fun, factory);
    return spec_from_value(*up);
}

TEST(GenericMergeTest, immediate_generic_merge_works) {
    ASSERT_TRUE((merge_layouts.size() % 2) == 0);
    for (size_t i = 0; i < merge_layouts.size(); i += 2) {
        TensorSpec lhs = spec(merge_layouts[i], N());
        TensorSpec rhs = spec(merge_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f, operation::Max::f}) {
            auto expect = reference_merge(lhs, rhs, fun);
            auto actual = immediate_generic_merge(lhs, rhs, fun);
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
