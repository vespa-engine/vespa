// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> map_layouts = {
    {x(3)},
    {x(3),y(5)},
    {x(3),y(5),z(7)},
    float_cells({x(3),y(5),z(7)}),
    {x({"a","b","c"})},
    {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
};

TensorSpec perform_generic_map(const TensorSpec &a, map_fun_t func, const ValueBuilderFactory &factory)
{
    auto lhs = value_from_spec(a, factory);
    auto my_op = GenericMap::make_instruction(lhs->type(), func);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_map_with(const ValueBuilderFactory &factory) {
    for (const auto & layout : map_layouts) {
        TensorSpec lhs = spec(layout, Div16(N()));
        ValueType lhs_type = ValueType::from_spec(lhs.type());
        for (auto func : {operation::Floor::f, operation::Fabs::f, operation::Square::f, operation::Inv::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.to_string().c_str()));
            auto expect = ReferenceOperations::map(lhs, func);
            auto actual = perform_generic_map(lhs, func, factory);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(GenericMapTest, generic_map_works_for_simple_values) {
    test_generic_map_with(SimpleValueBuilderFactory::get());
}

TEST(GenericMapTest, generic_map_works_for_fast_values) {
    test_generic_map_with(FastValueBuilderFactory::get());
}

TensorSpec immediate_generic_map(const TensorSpec &a, map_fun_t func, const ValueBuilderFactory &factory)
{
    auto lhs = value_from_spec(a, factory);
    auto up = GenericMap::perform_map(*lhs, func, factory);
    return spec_from_value(*up);
}

TEST(GenericMapTest, immediate_generic_map_works) {
    for (const auto & layout : map_layouts) {
        TensorSpec lhs = spec(layout, Div16(N()));
        ValueType lhs_type = ValueType::from_spec(lhs.type());
        for (auto func : {operation::Floor::f, operation::Fabs::f, operation::Square::f, operation::Inv::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.to_string().c_str()));
            auto expect = ReferenceOperations::map(lhs, func);
            auto actual = immediate_generic_map(lhs, func, SimpleValueBuilderFactory::get());
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
