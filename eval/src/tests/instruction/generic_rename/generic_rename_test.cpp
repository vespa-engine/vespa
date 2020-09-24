// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> rename_layouts = {
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

struct FromTo {
    std::vector<vespalib::string> from;
    std::vector<vespalib::string> to;
};

std::vector<FromTo> rename_from_to = {
    { {"x"}, {"x_renamed"} },
    { {"x"}, {"z_was_x"} },
    { {"x", "y"}, {"y", "x"} },
    { {"x", "z"}, {"z", "x"} },
    { {"x", "y", "z"}, {"a", "b", "c"} },
    { {"z"}, {"a"} },
    { {"y"}, {"z_was_y"} },
    { {"y"}, {"b"} }
};


TEST(GenericRenameTest, dense_rename_plan_can_be_created_and_executed) {
    auto lhs = ValueType::from_spec("tensor(a[2],c[3],d{},e[5],g[7],h{})");
    std::vector<vespalib::string> from({"a", "c", "e"});
    std::vector<vespalib::string>   to({"f", "a", "b"});
    ValueType renamed = lhs.rename(from, to);
    auto plan = DenseRenamePlan(lhs, renamed, from, to);
    std::vector<size_t> expect_loop = {15,2,7};
    std::vector<size_t> expect_stride = {7,105,1};
    EXPECT_EQ(plan.subspace_size, 210);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.stride, expect_stride);
    std::vector<int> out;
    int want[3][5][2][7];
    size_t counter = 0;
    for (size_t a = 0; a < 2; ++a) {
        for (size_t c = 0; c < 3; ++c) {
            for (size_t e = 0; e < 5; ++e) {
                for (size_t g = 0; g < 7; ++g) {
                    want[c][e][a][g] = counter++;
                }
            }
        }
    }
    std::vector<int> expect(210);
    memcpy(&expect[0], &want[0], 210*sizeof(int));
    auto move_cell = [&](size_t offset) { out.push_back(offset); };
    plan.execute(0, move_cell);
    EXPECT_EQ(out, expect);
}

TEST(GenericRenameTest, sparse_rename_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},c{},d[3],e{},g{},h[5])");
    std::vector<vespalib::string> from({"a", "c", "e"});
    std::vector<vespalib::string>   to({"f", "a", "b"});
    ValueType renamed = lhs.rename(from, to);
    auto plan = SparseRenamePlan(lhs, renamed, from, to);
    EXPECT_EQ(plan.mapped_dims, 4);
    std::vector<size_t> expect = {2,0,1,3};
    EXPECT_EQ(plan.output_dimensions, expect);
}

TensorSpec simple_tensor_rename(const TensorSpec &a, const FromTo &ft) {
    Stash stash;
    const auto &engine = SimpleTensorEngine::ref();
    auto lhs = engine.from_spec(a);
    const auto &result = engine.rename(*lhs, ft.from, ft.to, stash);
    return engine.to_spec(result);
}

TensorSpec perform_generic_rename(const TensorSpec &a, const ValueType &res_type,
                              const FromTo &ft, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto my_op = GenericRename::make_instruction(lhs->type(), res_type, ft.from, ft.to, factory, stash);
    InterpretedFunction::EvalSingle single(my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void test_generic_rename(const ValueBuilderFactory &factory) {
    for (const auto & layout : rename_layouts) {
        TensorSpec lhs = spec(layout, N());
        ValueType lhs_type = ValueType::from_spec(lhs.type());
        // printf("lhs_type: %s\n", lhs_type.to_spec().c_str());
        for (const auto & from_to : rename_from_to) {
            ValueType renamed_type = lhs_type.rename(from_to.from, from_to.to);
            if (renamed_type.is_error()) continue;
            // printf("type %s -> %s\n", lhs_type.to_spec().c_str(), renamed_type.to_spec().c_str());
            SCOPED_TRACE(fmt("\n===\nLHS: %s\n===\n", lhs.to_string().c_str()));
            auto expect = simple_tensor_rename(lhs, from_to);
            auto actual = perform_generic_rename(lhs, renamed_type, from_to, factory);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(GenericRenameTest, generic_rename_works_for_simple_values) {
    test_generic_rename(SimpleValueBuilderFactory::get());
}

GTEST_MAIN_RUN_ALL_TESTS()
