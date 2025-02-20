// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

void verify(const std::string &a, const std::string &expr, const std::string &result) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", TensorSpec::from_expr(a));
    auto expect = TensorSpec::from_expr(result);
    EXPECT_FALSE(ValueType::from_spec(expect.type()).is_error());
    EXPECT_EQ(EvalFixture::ref(expr, param_repo), expect);
    EXPECT_EQ(EvalFixture::prod(expr, param_repo), expect);
}

//-----------------------------------------------------------------------------

TEST(MapSubspacesTest, require_that_simple_map_subspaces_work)
{
    verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
           "map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))",
           "tensor(x{},y[2]):{foo:[3,5],bar:[9,11]}");
}

TEST(MapSubspacesTest, require_that_scalars_can_be_used_with_map_subspaces)
{
    verify("3.0",
           "map_subspaces(a,f(n)(n+5.0))",
           "8.0");
}

TEST(MapSubspacesTest, require_that_outer_cell_type_is_decayed_when_inner_type_is_double)
{
    verify("tensor<int8>(x{}):{foo:3,bar:7}",
           "map_subspaces(a,f(n)(n+2))",
           "tensor<float>(x{}):{foo:5,bar:9}");
}

TEST(MapSubspacesTest, require_that_inner_cell_type_is_used_directly_without_decay) {
    {
        SCOPED_TRACE("1");
        verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
               "map_subspaces(a,f(t)(cell_cast(t,int8)))",
               "tensor<int8>(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}");
    }
    {
        SCOPED_TRACE("2");
        verify("tensor(y[3]):[1,2,3]",
               "map_subspaces(a,f(t)(cell_cast(t,int8)))",
               "tensor<int8>(y[3]):[1,2,3]");
    }
}

TEST(MapSubspacesTest, require_that_map_subspaces_can_be_nested)
{
    verify("tensor(x{},y[3]):{foo:[1,2,3],bar:[4,5,6]}",
           "map_subspaces(a,f(a)(5+map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))))",
           "tensor(x{},y[2]):{foo:[8,10],bar:[14,16]}");
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
    ASSERT_EQ(types.errors().size(), 0u);
}

TEST(MapSubspacesTest, require_that_type_resolving_also_include_nodes_from_the_mapping_lambda_function) {
    auto fun = Function::parse("map_subspaces(a,f(a)(map_subspaces(a,f(t)(tensor(y[2])(t{y:(y)}+t{y:(y+1)})))))");
    NodeTypes types(*fun, {ValueType::from_spec("tensor(x{},y[3])")});
    check_errors(types);
    auto map_subspaces = nodes::as<nodes::TensorMapSubspaces>(fun->root());
    ASSERT_TRUE(map_subspaces != nullptr);
    EXPECT_EQ(types.get_type(*map_subspaces).to_spec(), "tensor(x{},y[2])");
    EXPECT_EQ(types.get_type(map_subspaces->lambda().root()).to_spec(), "tensor(y[2])");
    
    NodeTypes copy = types.export_types(fun->root());
    check_errors(copy);
    EXPECT_EQ(count_nodes(types), count_nodes(copy));

    NodeTypes map_types = copy.export_types(map_subspaces->lambda().root());
    check_errors(map_types);
    EXPECT_LT(count_nodes(map_types), count_nodes(copy));

    auto inner_map = nodes::as<nodes::TensorMapSubspaces>(map_subspaces->lambda().root());
    ASSERT_TRUE(inner_map != nullptr);
    NodeTypes inner_types = map_types.export_types(inner_map->lambda().root());
    check_errors(inner_types);
    EXPECT_LT(count_nodes(inner_types), count_nodes(map_types));
    
    // [lambda, peek, t, y, +, peek, t, y, +, 1] are the 10 nodes:
    EXPECT_EQ(count_nodes(inner_types), 10u);
}

GTEST_MAIN_RUN_ALL_TESTS()
