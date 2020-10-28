// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_create.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdlib.h>
#include <algorithm>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> create_layouts = {
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

TensorSpec remove_each(const TensorSpec &a, size_t n) {
    TensorSpec b(a.type());
    for (const auto & kv : a.cells()) {
        size_t v = kv.second;
        if ((v % n) != 0) {
            b.add(kv.first, kv.second);
        }
    }
    return b;
}

struct NumberedCellSpec {
    long int num;
    TensorSpec::Address addr;
    double value;
};

bool operator< (const NumberedCellSpec &a, const NumberedCellSpec &b) {
    return a.num < b.num;
}

TensorSpec perform_generic_create(const TensorSpec &a, const ValueBuilderFactory &factory)
{
    ValueType res_type = ValueType::from_spec(a.type());
    EXPECT_FALSE(res_type.is_error());
    Stash stash;
    std::vector<NumberedCellSpec> scramble;
    for (const auto & kv : a.cells()) {
        NumberedCellSpec cell{random(), kv.first, kv.second};
        scramble.push_back(cell);
    }
    std::sort(scramble.begin(), scramble.end());
    std::vector<Value::CREF> my_stack;
    std::map<TensorSpec::Address,size_t> create_spec;
    for (size_t child_idx = 0; child_idx < scramble.size(); ++child_idx) {
        auto cell = scramble[child_idx];
        create_spec.emplace(cell.addr, child_idx);
        my_stack.push_back(stash.create<DoubleValue>(cell.value));
    }
    auto my_op = GenericCreate::make_instruction(res_type, create_spec, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(my_stack));
}

void test_generic_create_with(const ValueBuilderFactory &factory) {
    for (const auto & layout : create_layouts) {
        TensorSpec full = spec(layout, N());
        auto actual = perform_generic_create(full, factory);
        EXPECT_EQ(actual, full);
        for (size_t n : {2, 3, 4, 5}) {
            TensorSpec partial = remove_each(full, n);
            actual = perform_generic_create(partial, factory);
            auto filled = spec_from_value(*value_from_spec(partial, SimpleValueBuilderFactory::get()));
            EXPECT_EQ(actual, filled);
        }
    }
}

TEST(GenericCreateTest, generic_create_works_for_simple_values) {
    test_generic_create_with(SimpleValueBuilderFactory::get());
}

TEST(GenericCreateTest, generic_create_works_for_fast_values) {
    test_generic_create_with(FastValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
