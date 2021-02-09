// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/instruction/fast_rename_optimizer.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x5", GenSpec().idx("x", 5))
        .add("x5f", GenSpec().idx("x", 5).cells_float())
        .add("x_m", GenSpec().map("x", {"a", "b", "c"}))
        .add("xy_mm", GenSpec().map("x", {"a", "b", "c"}).map("y", {"d","e"}))
        .add("x5y3z_m", GenSpec().idx("x", 5).idx("y", 3).map("z", {"a","b"}))
        .add("x5yz_m", GenSpec().idx("x", 5).map("y", {"a","b"}).map("z", {"d","e"}))
        .add("x5y3", GenSpec().idx("x", 5).idx("y", 3));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_EQUAL(info.size(), 1u);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that non-transposing dense renames are optimized") {
    TEST_DO(verify_optimized("rename(x5,x,y)"));
    TEST_DO(verify_optimized("rename(x5,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,y,z)"));
    TEST_DO(verify_optimized("rename(x5y3,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(a,b))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(z,zz))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(y,z))"));
    TEST_DO(verify_optimized("rename(x5y3,(y,x),(b,a))"));
}

TEST("require that transposing dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x5y3,x,z)"));
    TEST_DO(verify_not_optimized("rename(x5y3,y,a)"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(y,x))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(b,a))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(y,x),(a,b))"));
}

TEST("require that non-dense renames may be optimized") {
    TEST_DO(verify_optimized("rename(x_m,x,y)"));
    TEST_DO(verify_optimized("rename(xy_mm,(x,y),(a,b))"));
    TEST_DO(verify_optimized("rename(xy_mm,(x,y),(y,z))"));
    TEST_DO(verify_not_optimized("rename(xy_mm,(x,y),(b,a))"));
    TEST_DO(verify_not_optimized("rename(xy_mm,(x,y),(y,x))"));

    TEST_DO(verify_optimized("rename(x5y3z_m,(z),(a))"));
    TEST_DO(verify_optimized("rename(x5y3z_m,(x,y,z),(b,c,a))"));
    TEST_DO(verify_optimized("rename(x5y3z_m,(z),(a))"));
    TEST_DO(verify_optimized("rename(x5y3z_m,(x,y,z),(b,c,a))"));
    TEST_DO(verify_not_optimized("rename(x5y3z_m,(y),(a))"));
    TEST_DO(verify_not_optimized("rename(x5y3z_m,(x,z),(z,x))"));

    TEST_DO(verify_optimized("rename(x5yz_m,(x,y),(y,x))"));
    TEST_DO(verify_optimized("rename(x5yz_m,(x,y,z),(c,a,b))"));
    TEST_DO(verify_optimized("rename(x5yz_m,(y,z),(a,b))"));
    TEST_DO(verify_not_optimized("rename(x5yz_m,(z),(a))"));
    TEST_DO(verify_not_optimized("rename(x5yz_m,(y,z),(z,y))"));
}

TEST("require that chained optimized renames are compacted into a single operation") {
    TEST_DO(verify_optimized("rename(rename(x5,x,y),y,z)"));
}

TEST("require that optimization works for float cells") {
    TEST_DO(verify_optimized("rename(x5f,x,y)"));
}

bool is_stable(const vespalib::string &from_spec, const vespalib::string &to_spec,
               const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to)
{
    auto from_type = ValueType::from_spec(from_spec);
    auto to_type = ValueType::from_spec(to_spec);
    return FastRenameOptimizer::is_stable_rename(from_type, to_type, from, to);
}

TEST("require that rename is stable if dimension order is preserved") {
    EXPECT_TRUE(is_stable("tensor(a{},b{})", "tensor(a{},c{})", {"b"}, {"c"}));
    EXPECT_TRUE(is_stable("tensor(c[3],d[5])", "tensor(c[3],e[5])", {"d"}, {"e"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(a{},b{},c[3],e[5])", {"d"}, {"e"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(e{},f{},g[3],h[5])", {"a", "b", "c", "d"}, {"e", "f", "g", "h"}));
}

TEST("require that rename is unstable if nontrivial indexed dimensions change order") {
    EXPECT_FALSE(is_stable("tensor(c[3],d[5])", "tensor(d[5],e[3])", {"c"}, {"e"}));
    EXPECT_FALSE(is_stable("tensor(c[3],d[5])", "tensor(c[5],d[3])", {"c", "d"}, {"d", "c"}));
}

TEST("require that rename is unstable if mapped dimensions change order") {
    EXPECT_FALSE(is_stable("tensor(a{},b{})", "tensor(b{},c{})", {"a"}, {"c"}));
    EXPECT_FALSE(is_stable("tensor(a{},b{})", "tensor(a{},b{})", {"a", "b"}, {"b", "a"}));
}

TEST("require that rename can be stable if indexed and mapped dimensions change order") {
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(a[3],b[5],c{},d{})", {"a", "b", "c", "d"}, {"c", "d", "a", "b"}));
    EXPECT_TRUE(is_stable("tensor(a{},b{},c[3],d[5])", "tensor(c[3],d[5],e{},f{})", {"a", "b"}, {"e", "f"}));
}

TEST("require that rename can be stable if trivial dimension is moved") {
    EXPECT_TRUE(is_stable("tensor(a[1],b{},c[3])", "tensor(b{},bb[1],c[3])", {"a"}, {"bb"}));
    EXPECT_TRUE(is_stable("tensor(a[1],b{},c[3])", "tensor(b{},c[3],cc[1])", {"a"}, {"cc"}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
