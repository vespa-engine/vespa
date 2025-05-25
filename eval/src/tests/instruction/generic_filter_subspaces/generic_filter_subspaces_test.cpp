// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_filter_subspaces.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

TensorSpec ref_eval(const TensorSpec &a, const Function &lambda) {
    auto subspace_fun = [&](const TensorSpec &subspace){
        return ReferenceEvaluation::eval(lambda, {subspace});
    };
    return ReferenceOperations::filter_subspaces(a, subspace_fun);
}

TensorSpec my_eval(const TensorSpec &a, const Function &lambda, const ValueBuilderFactory &factory) {
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto inner_type = lhs->type().strip_mapped_dimensions();
    auto res_type = lhs->type();
    NodeTypes inner_types(lambda, {inner_type});
    auto my_op = GenericFilterSubspaces::make_instruction(res_type, inner_type, lambda, inner_types, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

void verify(const std::string &input_str, const std::string &fun_str, const std::string &expect_str) {
    SCOPED_TRACE(fmt("input: %s, fun: %s, expect: %s", input_str.c_str(), fun_str.c_str(), expect_str.c_str()));
    auto input = TensorSpec::from_expr(input_str);
    ASSERT_TRUE(input.type() != "error");
    auto expect = TensorSpec::from_expr(expect_str);
    ASSERT_TRUE(expect.type() != "error");
    auto fun = Function::parse({"s"}, fun_str);
    ASSERT_FALSE(fun->has_error());
    for (CellType cell_type: CellTypeUtils::list_types()) {
        SCOPED_TRACE(fmt("cell type: %d", int(cell_type)));
        auto typed_input = ReferenceOperations::cell_cast(input, cell_type);
        auto typed_expect = ReferenceOperations::cell_cast(expect, cell_type);
        EXPECT_EQ(ValueType::from_spec(typed_input.type()).cell_type(), cell_type);
        EXPECT_EQ(ValueType::from_spec(typed_expect.type()).cell_type(), cell_type);
        EXPECT_EQ(ref_eval(typed_input, *fun), typed_expect);
        EXPECT_EQ(my_eval(typed_input, *fun, FastValueBuilderFactory::get()), typed_expect);
        EXPECT_EQ(my_eval(typed_input, *fun, SimpleValueBuilderFactory::get()), typed_expect);
    }
}

TEST(GenericFilterSubspacesTest, filter_doubles) {
    verify("tensor(x{}):{}", "s", "tensor(x{}):{}");
    verify("tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}", "s", "tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}");
    verify("tensor(x{}):{a:0,b:2,c:0,d:4,e:0,f:6}", "s", "tensor(x{}):{b:2,d:4,f:6}");
    verify("tensor(x{}):{a:1,b:0,c:3,d:0,e:5,f:0}", "s", "tensor(x{}):{a:1,c:3,e:5}");

    verify("tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}", "s>3.5", "tensor(x{}):{d:4,e:5,f:6}");
    verify("tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}", "s<3.5", "tensor(x{}):{a:1,b:2,c:3}");
    verify("tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}", "s>0.5", "tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}");
    verify("tensor(x{}):{a:1,b:2,c:3,d:4,e:5,f:6}", "s<0.5", "tensor(x{}):{}");
}

TEST(GenericFilterSubspacesTest, filter_vectors) {
    verify("tensor(x{},y[3]):{}", "s", "tensor(x{},y[3]):{}");
    verify("tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}", "s", "tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}");
    verify("tensor(x{},y[3]):{a:[0,0,0],b:[4,5,6]}", "s", "tensor(x{},y[3]):{b:[4,5,6]}");
    verify("tensor(x{},y[3]):{a:[1,2,3],b:[0,0,0]}", "s", "tensor(x{},y[3]):{a:[1,2,3]}");

    verify("tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}", "reduce(s,sum)>6.5", "tensor(x{},y[3]):{b:[4,5,6]}");
    verify("tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}", "reduce(s,sum)<6.5", "tensor(x{},y[3]):{a:[1,2,3]}");
    verify("tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}", "reduce(s,sum)>2.5", "tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}");
    verify("tensor(x{},y[3]):{a:[1,2,3],b:[4,5,6]}", "reduce(s,sum)<2.5", "tensor(x{},y[3]):{}");
}

TEST(GenericFilterSubspacesTest, filter_matrices) {
    verify("tensor(x{},y[2],z[3]):{}", "s", "tensor(x{},y[2],z[3]):{}");
    verify("tensor(x{},y[2],z[3]):{a:[[1,2,3],[4,5,6]]}", "s", "tensor(x{},y[2],z[3]):{a:[[1,2,3],[4,5,6]]}");
    verify("tensor(x{},y[2],z[3]):{a:[[0,0,0],[4,5,6]]}", "s", "tensor(x{},y[2],z[3]):{a:[[0,0,0],[4,5,6]]}");
    verify("tensor(x{},y[2],z[3]):{a:[[1,2,3],[0,0,0]]}", "s", "tensor(x{},y[2],z[3]):{a:[[1,2,3],[0,0,0]]}");
    verify("tensor(x{},y[2],z[3]):{a:[[0,0,0],[0,0,0]]}", "s", "tensor(x{},y[2],z[3]):{}");
    verify("tensor(x{},y[2],z[3]):{a:[[1,2,3],[4,5,6]]}", "reduce(s,sum)==21", "tensor(x{},y[2],z[3]):{a:[[1,2,3],[4,5,6]]}");
    verify("tensor(x{},y[2],z[3]):{a:[[1,2,3],[4,5,6]]}", "reduce(s,sum)!=21", "tensor(x{},y[2],z[3]):{}");
}

GTEST_MAIN_RUN_ALL_TESTS()
