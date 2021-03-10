// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

GenSpec::seq_t N_16ths = [] (size_t i) noexcept { return (i + 1.0) / 16.0; };

GenSpec G() { return GenSpec().seq(N_16ths); }

const std::vector<GenSpec> layouts = {
    G(),
    G().idx("x", 3),
    G().idx("x", 3).idx("y", 5),
    G().idx("x", 3).idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),
    G().map("x", {}),
    G().map("x", {}).idx("y", 10),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {}).idx("z", 7)
};

TensorSpec perform_generic_reduce(const TensorSpec &a, Aggr aggr, const std::vector<vespalib::string> &dims,
                                  const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto res_type = lhs->type().reduce(dims);
    auto my_op = GenericReduce::make_instruction(res_type, lhs->type(), aggr, dims, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

TEST(GenericReduceTest, dense_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a[2],aa{},b[2],bb[1],c[2],cc{},d[2],dd[1],e[2],ee{},f[2])");
    auto plan = DenseReducePlan(type, type.reduce({"a", "d", "e"}));
    SmallVector<size_t> expect_loop_cnt = {2,4,4,2};
    SmallVector<size_t> expect_in_stride = {32,2,8,1};
    SmallVector<size_t> expect_out_stride = {0,0,2,1};
    EXPECT_EQ(plan.in_size, 64);
    EXPECT_EQ(plan.out_size, 8);
    EXPECT_EQ(plan.loop_cnt, expect_loop_cnt);
    EXPECT_EQ(plan.in_stride, expect_in_stride);
    EXPECT_EQ(plan.out_stride, expect_out_stride);
}

TEST(GenericReduceTest, sparse_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a{},aa[10],b{},c{},cc[5],d{},e{},ee[1],f{})");
    auto plan = SparseReducePlan(type, type.reduce({"a", "d", "e"}));
    SmallVector<size_t> expect_keep_dims = {1,2,5};
    EXPECT_EQ(plan.num_reduce_dims, 3);
    EXPECT_EQ(plan.keep_dims, expect_keep_dims);
}

void test_generic_reduce_with(const ValueBuilderFactory &factory) {
    for (const auto &layout: layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto input = layout.cpy().cells(ct);
            if (input.bad_scalar()) continue;
            SCOPED_TRACE(fmt("tensor type: %s, num_cells: %zu", input.gen().type().c_str(), input.gen().cells().size()));
            for (Aggr aggr: {Aggr::SUM, Aggr::AVG, Aggr::MIN, Aggr::MAX}) {
                SCOPED_TRACE(fmt("aggregator: %s", AggrNames::name_of(aggr)->c_str()));
                auto t = layout.type();
                for (const auto & dim: t.dimensions()) {
                    auto expect = ReferenceOperations::reduce(input, aggr, {dim.name});
                    auto actual = perform_generic_reduce(input, aggr, {dim.name}, factory);
                    EXPECT_EQ(actual, expect);
                }
                auto expect = ReferenceOperations::reduce(input, aggr, {});
                auto actual = perform_generic_reduce(input, aggr, {}, factory);
                EXPECT_EQ(actual, expect);
            }
        }
    }
}

TEST(GenericReduceTest, generic_reduce_works_for_simple_values) {
    test_generic_reduce_with(SimpleValueBuilderFactory::get());
}

TEST(GenericReduceTest, generic_reduce_works_for_fast_values) {
    test_generic_reduce_with(FastValueBuilderFactory::get());
}


GTEST_MAIN_RUN_ALL_TESTS()
