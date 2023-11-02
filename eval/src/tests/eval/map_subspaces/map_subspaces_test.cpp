// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/tensor_nodes.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

void verify(const vespalib::string &a, const vespalib::string &expr, const vespalib::string &result) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", TensorSpec::from_expr(a));
    auto expect = TensorSpec::from_expr(result);
    EXPECT_FALSE(ValueType::from_spec(expect.type()).is_error());
    EXPECT_EQUAL(EvalFixture::ref(expr, param_repo), expect);
    EXPECT_EQUAL(EvalFixture::prod(expr, param_repo), expect);
}

//-----------------------------------------------------------------------------

TEST("require that simple map_subspaces work") {
    TEST_DO(verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
                   "map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))",
                   "tensor(x{},y[2]):{foo:[3,5],bar:[9,11]}"));
}

TEST("require that scalars can be used with map_subspaces") {
    TEST_DO(verify("3.0",
                   "map_subspaces(a,f(n)(n+5.0))",
                   "8.0"));
}

TEST("require that outer cell type is decayed when inner type is double") {
    TEST_DO(verify("tensor<int8>(x{}):{foo:3,bar:7}",
                   "map_subspaces(a,f(n)(n+2))",
                   "tensor<float>(x{}):{foo:5,bar:9}"));
}

TEST("require that inner cell type is used directly without decay") {
    TEST_DO(verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
                   "map_subspaces(a,f(t)(cell_cast(t,int8)))",
                   "tensor<int8>(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}"));
    TEST_DO(verify("tensor(y[3]):[1,2,3]",
                   "map_subspaces(a,f(t)(cell_cast(t,int8)))",
                   "tensor<int8>(y[3]):[1,2,3]"));
}

TEST("require that map_subspaces can be nested") {
    TEST_DO(verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
                   "map_subspaces(a,f(a)(5+map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))))",
                   "tensor(x{},y[2]):{foo:[8,10],bar:[14,16]}"));
}

size_t count_nodes(const NodeTypes &types) {
    size_t cnt = 0;
    types.each([&](const auto &, const auto &){++cnt;});
    return cnt;
}

void check_errors(const NodeTypes &types) {
    for (const auto &err: types.errors()) {
        fprintf(stderr, "%s\n", err.c_str());
    }
    ASSERT_EQUAL(types.errors().size(), 0u);
}

TEST("require that type resolving also include nodes from the mapping lambda function") {
    auto fun = Function::parse("map_subspaces(a,f(a)(map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))))");
    NodeTypes types(*fun, {ValueType::from_spec("tensor(x{},y[3])")});
    check_errors(types);
    auto map_subspaces = nodes::as<nodes::TensorMapSubspaces>(fun->root());
    ASSERT_TRUE(map_subspaces != nullptr);
    EXPECT_EQUAL(types.get_type(*map_subspaces).to_spec(), "tensor(x{},y[2])");
    EXPECT_EQUAL(types.get_type(map_subspaces->lambda().root()).to_spec(), "tensor(y[2])");
    
    NodeTypes copy = types.export_types(fun->root());
    check_errors(copy);
    EXPECT_EQUAL(count_nodes(types), count_nodes(copy));

    NodeTypes map_types = copy.export_types(map_subspaces->lambda().root());
    check_errors(map_types);
    EXPECT_LESS(count_nodes(map_types), count_nodes(copy));

    auto inner_map = nodes::as<nodes::TensorMapSubspaces>(map_subspaces->lambda().root());
    ASSERT_TRUE(inner_map != nullptr);
    NodeTypes inner_types = map_types.export_types(inner_map->lambda().root());
    check_errors(inner_types);
    EXPECT_LESS(count_nodes(inner_types), count_nodes(map_types));
    
    // [lambda, peek, t, y, +, peek, t, y, +, 1] are the 10 nodes:
    EXPECT_EQUAL(count_nodes(inner_types), 10u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
