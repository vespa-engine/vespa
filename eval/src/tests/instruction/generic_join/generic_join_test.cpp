// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_join.h>
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

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

GenSpec G() { return GenSpec().seq(N_16ths); }

const std::vector<GenSpec> join_layouts = {
    G(),                                                         G(),
    G().idx("x", 5),                                             G().idx("x", 5),
    G().idx("x", 5),                                             G().idx("y", 5),
    G().idx("x", 5),                                             G().idx("x", 5).idx("y", 5),
    G().idx("y", 3),                                             G().idx("x", 2).idx("z", 3),
    G().idx("x", 3).idx("y", 5),                                 G().idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b"}),
    G().map("x", {"a","b","c"}),                                 G().map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b","c"}),                                 G().map("x", {"a","b","c"}).map("y", {"foo","bar","baz"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b"}).map("y", {"foo","bar","baz"}),       G().map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}),                    G().map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5),                     G().idx("y", 5).map("z", {"i","j","k","l"})
};

bool join_address(const TensorSpec::Address &a, const TensorSpec::Address &b, TensorSpec::Address &addr) {
    for (const auto &dim_a: a) {
        auto pos_b = b.find(dim_a.first);
        if ((pos_b != b.end()) && !(pos_b->second == dim_a.second)) {
            return false;
        }
        addr.insert_or_assign(dim_a.first, dim_a.second);
    }
    return true;
}

TensorSpec perform_generic_join(const TensorSpec &a, const TensorSpec &b,
                                join_fun_t function, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto res_type = ValueType::join(lhs->type(), rhs->type());
    auto my_op = GenericJoin::make_instruction(res_type, lhs->type(), rhs->type(), function, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(GenericJoinTest, dense_join_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},b[6],c[5],e[3],f[2],g{})");
    auto rhs = ValueType::from_spec("tensor(a{},b[6],c[5],d[4],h{})");
    auto plan = DenseJoinPlan(lhs, rhs);
    SmallVector<size_t> expect_loop = {30,4,6};
    SmallVector<size_t> expect_lhs_stride = {6,0,1};
    SmallVector<size_t> expect_rhs_stride = {4,1,0};
    EXPECT_EQ(plan.lhs_size, 180);
    EXPECT_EQ(plan.rhs_size, 120);
    EXPECT_EQ(plan.out_size, 720);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.lhs_stride, expect_lhs_stride);
    EXPECT_EQ(plan.rhs_stride, expect_rhs_stride);
}

TEST(GenericJoinTest, sparse_join_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},b[6],c[5],e[3],f[2],g{})");
    auto rhs = ValueType::from_spec("tensor(b[6],c[5],d[4],g{},h{})");
    auto plan = SparseJoinPlan(lhs, rhs);
    using SRC = SparseJoinPlan::Source;
    SmallVector<SRC> expect_sources = {SRC::LHS,SRC::BOTH,SRC::RHS};
    SmallVector<size_t> expect_lhs_overlap = {1};
    SmallVector<size_t> expect_rhs_overlap = {0};
    EXPECT_EQ(plan.sources, expect_sources);
    EXPECT_EQ(plan.lhs_overlap, expect_lhs_overlap);
    EXPECT_EQ(plan.rhs_overlap, expect_rhs_overlap);
}

TEST(GenericJoinTest, dense_join_plan_can_be_executed) {
    auto plan = DenseJoinPlan(ValueType::from_spec("tensor(a[2])"),
                              ValueType::from_spec("tensor(b[3])"));
    std::vector<int> a({1, 2});
    std::vector<int> b({3, 4, 5});
    std::vector<int> c(6, 0);
    std::vector<int> expect = {3,4,5,6,8,10};
    ASSERT_EQ(plan.out_size, 6);
    int *dst = &c[0];
    auto cell_join = [&](size_t a_idx, size_t b_idx) { *dst++ = (a[a_idx] * b[b_idx]); };
    plan.execute(0, 0, cell_join);
    EXPECT_EQ(c, expect);
}

TEST(GenericJoinTest, generic_join_works_for_simple_and_fast_values) {
    ASSERT_TRUE((join_layouts.size() % 2) == 0);
    for (size_t i = 0; i < join_layouts.size(); i += 2) {
        const auto &l = join_layouts[i];
        const auto &r = join_layouts[i+1];
        for (CellType lct : CellTypeUtils::list_types()) {
            auto lhs = l.cpy().cells(lct);
            if (lhs.bad_scalar()) continue;
            for (CellType rct : CellTypeUtils::list_types()) {
                auto rhs = r.cpy().cells(rct);
                if (rhs.bad_scalar()) continue;
                for (auto fun: {operation::Add::f, operation::Sub::f, operation::Mul::f, operation::Div::f}) {
                    SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.gen().to_string().c_str(), rhs.gen().to_string().c_str()));
                    auto expect = ReferenceOperations::join(lhs, rhs, fun);
                    auto simple = perform_generic_join(lhs, rhs, fun, SimpleValueBuilderFactory::get());
                    auto fast = perform_generic_join(lhs, rhs, fun, FastValueBuilderFactory::get());
                    EXPECT_EQ(simple, expect);
                    EXPECT_EQ(fast, expect);
                }
            }
        }
    }
}


GTEST_MAIN_RUN_ALL_TESTS()
