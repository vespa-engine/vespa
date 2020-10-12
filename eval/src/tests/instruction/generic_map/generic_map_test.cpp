// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_map.h>
#include <vespa/eval/eval/interpreted_function.h>
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

TensorSpec reference_map(const TensorSpec &a, map_fun_t func) {
    ValueType res_type = ValueType::from_spec(a.type());
    EXPECT_FALSE(res_type.is_error());
    TensorSpec result(res_type.to_spec());
    for (const auto &cell: a.cells()) {
        result.add(cell.first, func(cell.second));
    }
    return result;
}

TensorSpec perform_generic_map(const TensorSpec &a, map_fun_t func, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto my_op = GenericMap::make_instruction(lhs->type(), func, stash);
    InterpretedFunction::EvalSingle single(my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_map(const ValueBuilderFactory &factory) {
    for (const auto & layout : map_layouts) {
        TensorSpec lhs = spec(layout, Div16(N()));
        ValueType lhs_type = ValueType::from_spec(lhs.type());
        for (auto func : {operation::Floor::f, operation::Fabs::f, operation::Square::f, operation::Inv::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.to_string().c_str()));
            auto expect = reference_map(lhs, func);
            auto actual = perform_generic_map(lhs, func, factory);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(GenericMapTest, generic_map_works_for_simple_values) {
    test_generic_map(SimpleValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
