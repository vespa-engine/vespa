// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/instruction/fast_rename_optimizer.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/vespalib/util/unwind_message.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

struct FunInfo {
    using LookFor = ReplaceTypeFunction;
    void verify(const LookFor &fun) const {
        EXPECT_FALSE(fun.result_is_mutable());
    }
};

void verify_optimized(const vespalib::string &expr) {
    CellTypeSpace all_types(CellTypeUtils::list_types(), 1);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{}}, all_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    CellTypeSpace all_types(CellTypeUtils::list_types(), 1);
    EvalFixture::verify<FunInfo>(expr, {}, all_types);
}

TEST(FastRenameTest, non_transposing_dense_renames_are_optimized) {
    UNWIND_DO(verify_optimized("rename(x5,x,y)"));
    UNWIND_DO(verify_optimized("rename(x5,x,a)"));
    UNWIND_DO(verify_optimized("rename(x5y3,y,z)"));
    UNWIND_DO(verify_optimized("rename(x5y3,x,a)"));
    UNWIND_DO(verify_optimized("rename(x5y3,(x,y),(a,b))"));
    UNWIND_DO(verify_optimized("rename(x5y3,(x,y),(z,zz))"));
    UNWIND_DO(verify_optimized("rename(x5y3,(x,y),(y,z))"));
    UNWIND_DO(verify_optimized("rename(x5y3,(y,x),(b,a))"));
}

TEST(FastRenameTest, transposing_dense_renames_are_not_optimized) {
    UNWIND_DO(verify_not_optimized("rename(x5y3,x,z)"));
    UNWIND_DO(verify_not_optimized("rename(x5y3,y,a)"));
    UNWIND_DO(verify_not_optimized("rename(x5y3,(x,y),(y,x))"));
    UNWIND_DO(verify_not_optimized("rename(x5y3,(x,y),(b,a))"));
    UNWIND_DO(verify_not_optimized("rename(x5y3,(y,x),(a,b))"));
}

TEST(FastRenameTest, non_dense_renames_may_be_optimized) {
    UNWIND_DO(verify_optimized("rename(x3_1,x,y)"));
    UNWIND_DO(verify_optimized("rename(x3_1y2_1,(x,y),(a,b))"));
    UNWIND_DO(verify_optimized("rename(x3_1y2_1,(x,y),(y,z))"));
    UNWIND_DO(verify_not_optimized("rename(x3_1y2_1,(x,y),(b,a))"));
    UNWIND_DO(verify_not_optimized("rename(x3_1y2_1,(x,y),(y,x))"));

    UNWIND_DO(verify_optimized("rename(x5y3z2_1,(z),(a))"));
    UNWIND_DO(verify_optimized("rename(x5y3z2_1,(x,y,z),(b,c,a))"));
    UNWIND_DO(verify_optimized("rename(x5y3z2_1,(z),(a))"));
    UNWIND_DO(verify_optimized("rename(x5y3z2_1,(x,y,z),(b,c,a))"));
    UNWIND_DO(verify_not_optimized("rename(x5y3z2_1,(y),(a))"));
    UNWIND_DO(verify_not_optimized("rename(x5y3z2_1,(x,z),(z,x))"));

    UNWIND_DO(verify_optimized("rename(x5y2_1z9_3,(x,y),(y,x))"));
    UNWIND_DO(verify_optimized("rename(x5y2_1z9_3,(x,y,z),(c,a,b))"));
    UNWIND_DO(verify_optimized("rename(x5y2_1z9_3,(y,z),(a,b))"));
    UNWIND_DO(verify_not_optimized("rename(x5y2_1z9_3,(z),(a))"));
    UNWIND_DO(verify_not_optimized("rename(x5y2_1z9_3,(y,z),(z,y))"));
}

TEST(FastRenameTest, chained_optimized_renames_are_compacted_into_a_single_operation) {
    UNWIND_DO(verify_optimized("rename(rename(x5,x,y),y,z)"));
}

bool is_stable(const vespalib::string &from_spec, const vespalib::string &to_spec,
               const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to)
{
    auto from_type = ValueType::from_spec(from_spec);
    auto to_type = ValueType::from_spec(to_spec);
    return FastRenameOptimizer::is_stable_rename(from_type, to_type, from, to);
}

TEST(FastRenameTest, rename_is_stable_if_dimension_order_is_preserved) {
    EXPECT_TRUE(is_stable("tensor(a{},b{})", "tensor(a{},c{})", {"b"}, {"c"}));
    EXPECT_TRUE(is_stable("tensor(c[3],d[5])", "tensor(c[3],e[5])", {"d"}, {"e"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(a{},b{},c[3],e[5])", {"d"}, {"e"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(e{},f{},g[3],h[5])", {"a", "b", "c", "d"}, {"e", "f", "g", "h"}));
}

TEST(FastRenameTest, rename_is_unstable_if_nontrivial_indexed_dimensions_change_order) {
    EXPECT_FALSE(is_stable("tensor(c[3],d[5])", "tensor(d[5],e[3])", {"c"}, {"e"}));
    EXPECT_FALSE(is_stable("tensor(c[3],d[5])", "tensor(c[5],d[3])", {"c", "d"}, {"d", "c"}));
}

TEST(FastRenameTest, rename_is_unstable_if_mapped_dimensions_change_order) {
    EXPECT_FALSE(is_stable("tensor(a{},b{})", "tensor(b{},c{})", {"a"}, {"c"}));
    EXPECT_FALSE(is_stable("tensor(a{},b{})", "tensor(a{},b{})", {"a", "b"}, {"b", "a"}));
}

TEST(FastRenameTest, rename_can_be_stable_if_indexed_and_mapped_dimensions_change_order) {
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(a[3],b[5],c{},d{})", {"a", "b", "c", "d"}, {"c", "d", "a", "b"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(c[3],d[5],e{},f{})", {"a", "b"}, {"e", "f"}));
}

TEST(FastRenameTest, rename_can_be_stable_if_trivial_dimension_is_moved) {
    EXPECT_TRUE(is_stable("tensor(a[1],b{},c[3])", "tensor(b{},bb[1],c[3])", {"a"}, {"bb"}));
    EXPECT_TRUE(is_stable("tensor(a[1],b{},c[3])", "tensor(b{},c[3],cc[1])", {"a"}, {"cc"}));
}

GTEST_MAIN_RUN_ALL_TESTS()
