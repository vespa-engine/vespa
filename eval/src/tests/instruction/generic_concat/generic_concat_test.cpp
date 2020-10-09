// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> concat_layouts = {
    {},                                                 {},
    {},                                                 {y(5)},
    float_cells({y(5)}),                                {},
    {},                                                 float_cells({y(5)}),
    {y(5)},                                             {},
    {y(2)},                                             {y(3)},
    {y(2)},                                             {x(3)},
    {x(2)},                                             {z(3)},
    {x(2),y(3)},                                        {x(2),y(3)},
    {x(2),y(3)},                                        {x(2),y(4)},
    {y(3),z(5)},                                        {y(3),z(5)},
    {y(3),z(5)},                                        {y(4),z(5)},
    {x(2),y(3),z(5)},                                   {x(2),y(3),z(5)},
    {x(2),y(3),z(5)},                                   {x(2),y(4),z(5)},
    {x(2),y(3),z({"a","b"})},                           {x(2),y(3),z({"b","c"})},
    {x(2),y(3),z({"a","b"})},                           {x(2),y(4),z({"b","c"})},
    {y(5)},                                             {y(2),x(5)},
    {x(3)},                                             {y(2),z(3)},
    {y(2)},                                             {y(3),x(5),z(2)},
    {y(2),x(5),z(2)},                                   {y(3),x(5),z(2)},
    {y(3),x(5)},                                        {x(5),z(7)},
    float_cells({y(3),x(5)}),                           {x(5),z(7)},
    float_cells({y(3),x(5)}),                           {},
    {y(3),x(5)},                                        float_cells({x(5),z(7)}),
    float_cells({y(3),x(5)}),                           float_cells({x(5),z(7)}),
    {x({"a","b","c"})},                                 {x({"a","b","c"})},
    {x({"a","b","c"})},                                 {x({"a","b"})},
    {x({"a","b","c"})},                                 {x({"b","c","d"})},
    float_cells({x({"a","b","c"})}),                    {x({"b","c","d"})},
    {x({"a","b","c"})},                                 float_cells({x({"b","c","d"})}),
    float_cells({x({"a","b","c"})}),                    float_cells({z({"foo","bar","baz"})}),
    {x({"a","b","c"})},                                 {x({"a","b","c"}),z({"foo","bar","baz"})},
    {x({"a","b"}),z({"foo","bar","baz"})},              {x({"a","b","c"}),z({"foo","bar"})},
    {y(2),x({"a","b","c"})},                            {y(3),x({"b","c","d"})},
    {y(2),x({"a","b"})},                                {y(3),z({"c","d"})}
};

TensorSpec perform_simpletensor_concat(const TensorSpec &a, const TensorSpec &b, const std::string &dimension) {
    auto lhs = SimpleTensor::create(a);
    auto rhs = SimpleTensor::create(b);
    auto out = SimpleTensor::concat(*lhs, *rhs, dimension);
    return SimpleTensorEngine::ref().to_spec(*out);
}

bool concat_address(const TensorSpec::Address &me, const TensorSpec::Address &other,
                    const std::string &concat_dim, size_t my_offset,
                    TensorSpec::Address &my_out, TensorSpec::Address &other_out)
{
    my_out.insert_or_assign(concat_dim, my_offset);
    for (const auto &my_dim: me) {
        const auto & name = my_dim.first;
        const auto & label = my_dim.second;
        if (name == concat_dim) {
            my_out.insert_or_assign(name, label.index + my_offset);
        } else {
            auto pos = other.find(name);
            if ((pos == other.end()) || (pos->second == label)) {
                my_out.insert_or_assign(name, label);
                other_out.insert_or_assign(name, label);
            } else {
                return false;
            }
        }
    }
    return true;
}

bool concat_addresses(const TensorSpec::Address &a, const TensorSpec::Address &b,
                      const std::string &concat_dim, size_t b_offset,
                      TensorSpec::Address &a_out, TensorSpec::Address &b_out)
{
    return concat_address(a, b, concat_dim,        0, a_out, b_out) &&
           concat_address(b, a, concat_dim, b_offset, b_out, a_out);
}

TensorSpec reference_concat(const TensorSpec &a, const TensorSpec &b, const std::string &concat_dim) {
    ValueType a_type = ValueType::from_spec(a.type());
    ValueType b_type = ValueType::from_spec(b.type());
    ValueType res_type = ValueType::concat(a_type, b_type, concat_dim);
    EXPECT_FALSE(res_type.is_error());
    size_t b_offset = 1;
    size_t concat_dim_index = a_type.dimension_index(concat_dim);
    if (concat_dim_index != ValueType::Dimension::npos) {
        const auto &dim = a_type.dimensions()[concat_dim_index];
        EXPECT_TRUE(dim.is_indexed());
        b_offset = dim.size;
    }
    TensorSpec result(res_type.to_spec());
    for (const auto &cell_a: a.cells()) {
        for (const auto &cell_b: b.cells()) {
            TensorSpec::Address addr_a;
            TensorSpec::Address addr_b;
            if (concat_addresses(cell_a.first, cell_b.first, concat_dim, b_offset, addr_a, addr_b)) {
                result.add(addr_a, cell_a.second);
                result.add(addr_b, cell_b.second);
            }
        }
    }
    return result;
}

TensorSpec perform_generic_concat(const TensorSpec &a, const TensorSpec &b, const std::string &concat_dim) {
    Stash stash;
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto my_op = GenericConcat::make_instruction(lhs->type(), rhs->type(), concat_dim, factory, stash);
    InterpretedFunction::EvalSingle single(my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(GenericConcatTest, generic_reference_concat_works) {
    ASSERT_TRUE((concat_layouts.size() % 2) == 0);
    for (size_t i = 0; i < concat_layouts.size(); i += 2) {
        const TensorSpec lhs = spec(concat_layouts[i], N());
        const TensorSpec rhs = spec(concat_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nin LHS: %s\nin RHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto actual = reference_concat(lhs, rhs, "y");
        auto expect = perform_simpletensor_concat(lhs, rhs, "y");
        EXPECT_EQ(actual, expect);
    }
}

TEST(GenericConcatTest, generic_concat_works_for_simple_values) {
    ASSERT_TRUE((concat_layouts.size() % 2) == 0);
    for (size_t i = 0; i < concat_layouts.size(); i += 2) {
        const TensorSpec lhs = spec(concat_layouts[i], N());
        const TensorSpec rhs = spec(concat_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nin LHS: %s\nin RHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        auto actual = perform_generic_concat(lhs, rhs, "y");
        auto expect = reference_concat(lhs, rhs, "y");
        EXPECT_EQ(actual, expect);
    }
}

TEST(GenericConcatTest, dense_concat_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a[2],b[3],c[5],d{},f[2],g[3])");
    auto rhs = ValueType::from_spec("tensor(a[2],b[3],c[7],e{},h[3],i[4])");
    auto res_type = ValueType::concat(lhs, rhs, "c");
    auto plan = DenseConcatPlan(lhs, rhs, "c", res_type);
    EXPECT_EQ(plan.right_offset, 5*2*3*3*4);
    EXPECT_EQ(plan.output_size, 2*3*12*2*3*3*4);
    EXPECT_EQ(plan.left.input_size, 2*3*5*2*3);
    std::vector<size_t> expect_left_loop  = {    6,  5,  6, 12 };
    std::vector<size_t> expect_left_in_s  = {   30,  6,  1,  0 };
    std::vector<size_t> expect_left_out_s = {  864, 72, 12,  1 };
    EXPECT_EQ(plan.left.in_loop_cnt, expect_left_loop);
    EXPECT_EQ(plan.left.in_stride, expect_left_in_s);
    EXPECT_EQ(plan.left.out_stride, expect_left_out_s);
    EXPECT_EQ(plan.right.input_size, 2*3*7*3*4);
    std::vector<size_t> expect_right_loop =  {   6,  7,  6, 12 };
    std::vector<size_t> expect_right_in_s =  {  84, 12,  0,  1 };
    std::vector<size_t> expect_right_out_s = { 864, 72, 12,  1 };
    EXPECT_EQ(plan.right.in_loop_cnt, expect_right_loop);
    EXPECT_EQ(plan.right.in_stride, expect_right_in_s);
    EXPECT_EQ(plan.right.out_stride, expect_right_out_s);
}

GTEST_MAIN_RUN_ALL_TESTS()
