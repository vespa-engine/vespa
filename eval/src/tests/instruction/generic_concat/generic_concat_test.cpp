// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec G() { return GenSpec(); }

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

const std::vector<GenSpec> concat_layouts = {
    G(),                                                         G(),
    G(),                                                         G().idx("y", 5),
    G().idx("y", 5),                                             G(),
    G().idx("y", 2),                                             G().idx("y", 3),
    G().idx("y", 2),                                             G().idx("x", 3),
    G().idx("x", 2),                                             G().idx("z", 3),
    G().idx("x", 2).idx("y", 3),                                 G().idx("x", 2).idx("y", 3),
    G().idx("x", 2).idx("y", 3),                                 G().idx("x", 2).idx("y", 4),
    G().idx("y", 3).idx("z", 5),                                 G().idx("y", 3).idx("z", 5),
    G().idx("y", 3).idx("z", 5),                                 G().idx("y", 4).idx("z", 5),
    G().idx("x", 2).idx("y", 3).idx("z", 5),                     G().idx("x", 2).idx("y", 3).idx("z", 5),
    G().idx("x", 2).idx("y", 3).idx("z", 5),                     G().idx("x", 2).idx("y", 4).idx("z", 5),
    G().idx("x", 2).idx("y", 3).map("z", {"a","b"}),             G().idx("x", 2).idx("y", 3).map("z", {"b","c"}),
    G().idx("x", 2).idx("y", 3).map("z", {"a","b"}),             G().idx("x", 2).idx("y", 4).map("z", {"b","c"}),
    G().idx("y", 5),                                             G().idx("x", 5).idx("y", 2),
    G().idx("x", 3),                                             G().idx("y", 2).idx("z", 3),
    G().idx("y", 2),                                             G().idx("x", 5).idx("y", 3).idx("z", 2),
    G().idx("x", 5).idx("y", 2).idx("z", 2),                     G().idx("x", 5).idx("y", 3).idx("z", 2),
    G().idx("x", 5).idx("y", 3),                                 G().idx("x", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"b","c","d"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}).map("z", {"foo","bar","baz"}),
    G().map("x", {"a","b"}).map("z", {"foo","bar","baz"}),       G().map("x", {"a","b","c"}).map("z", {"foo","bar"}),
    G().map("x", {"a","b","c"}).idx("y", 3),                     G().idx("y", 2),
    G().map("x", {"a","b","c"}).idx("y", 3),                     G().idx("z", 5),
    G().map("x", {"a","b","c"}).idx("y", 3),                     G().idx("y", 2).idx("z", 5),
    G().map("x", {"a","b","c"}).idx("y", 3),                     G().idx("y", 2),
    G().map("x", {"a","b","c"}).idx("y", 3).idx("z", 5),         G().idx("z", 5),
    G().idx("y", 2),                                             G().map("x", {"a","b","c"}).idx("y", 3),
    G().idx("z", 5),                                             G().map("x", {"a","b","c"}).idx("y", 3),
    G().idx("y", 2).idx("z", 5),                                 G().map("x", {"a","b","c"}).idx("y", 3),
    G().idx("y", 2),                                             G().map("x", {"a","b","c"}).idx("y", 3),
    G().idx("z", 5),                                             G().map("x", {"a","b","c"}).idx("y", 3).idx("z", 5),
    G().idx("y", 2).idx("z", 5),                                 G().map("x", {"a","b","c"}).idx("y", 3).idx("z", 5),
    G().map("x", {"a","b","c"}).idx("y", 2),                     G().map("x", {"b","c","d"}).idx("y", 3),
    G().map("x", {"a","b"}).idx("y", 2),                         G().idx("y", 3).map("z", {"c","d"})
};

TensorSpec perform_generic_concat(const TensorSpec &a, const TensorSpec &b,
                                  const std::string &concat_dim, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto res_type = ValueType::concat(lhs->type(), rhs->type(), concat_dim);
    auto my_op = GenericConcat::make_instruction(res_type, lhs->type(), rhs->type(), concat_dim, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

void test_generic_concat_with(const ValueBuilderFactory &factory) {
    ASSERT_TRUE((concat_layouts.size() % 2) == 0);
    for (size_t i = 0; i < concat_layouts.size(); i += 2) {
        const auto l = concat_layouts[i];
        const auto r = concat_layouts[i+1].cpy().seq(N_16ths);
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                SCOPED_TRACE(fmt("\n===\nin LHS: %s\nin RHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                auto actual = perform_generic_concat(lhs, rhs, "y", factory);
                auto expect = ReferenceOperations::concat(lhs, rhs, "y");
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericConcatTest, generic_concat_works_for_simple_values) {
    test_generic_concat_with(SimpleValueBuilderFactory::get());
}

TEST(GenericConcatTest, generic_concat_works_for_fast_values) {
    test_generic_concat_with(FastValueBuilderFactory::get());
}

TEST(GenericConcatTest, dense_concat_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a[2],b[3],c[5],d{},f[2],g[3])");
    auto rhs = ValueType::from_spec("tensor(a[2],b[3],c[7],e{},h[3],i[4])");
    auto res_type = ValueType::concat(lhs, rhs, "c");
    auto plan = DenseConcatPlan(lhs, rhs, "c", res_type);
    EXPECT_EQ(plan.right_offset, 5*2*3*3*4);
    EXPECT_EQ(plan.output_size, 2*3*12*2*3*3*4);
    EXPECT_EQ(plan.left.input_size, 2*3*5*2*3);
    SmallVector<size_t> expect_left_loop  = {    6,  5,  6, 12 };
    SmallVector<size_t> expect_left_in_s  = {   30,  6,  1,  0 };
    SmallVector<size_t> expect_left_out_s = {  864, 72, 12,  1 };
    EXPECT_EQ(plan.left.in_loop_cnt, expect_left_loop);
    EXPECT_EQ(plan.left.in_stride, expect_left_in_s);
    EXPECT_EQ(plan.left.out_stride, expect_left_out_s);
    EXPECT_EQ(plan.right.input_size, 2*3*7*3*4);
    SmallVector<size_t> expect_right_loop =  {   6,  7,  6, 12 };
    SmallVector<size_t> expect_right_in_s =  {  84, 12,  0,  1 };
    SmallVector<size_t> expect_right_out_s = { 864, 72, 12,  1 };
    EXPECT_EQ(plan.right.in_loop_cnt, expect_right_loop);
    EXPECT_EQ(plan.right.in_stride, expect_right_in_s);
    EXPECT_EQ(plan.right.out_stride, expect_right_out_s);
}


GTEST_MAIN_RUN_ALL_TESTS()
