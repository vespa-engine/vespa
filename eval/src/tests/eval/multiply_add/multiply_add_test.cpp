// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

double gcc_fun(double a, double b) {
    return (a * 3) + b;
}

TEST(MultiplyAddTest, multiply_add_gives_same_result) {
    auto fun = Function::parse("a*3+b");
    CompiledFunction cfun(*fun, PassParams::ARRAY);
    NodeTypes node_types = NodeTypes(*fun, {ValueType::double_type(), ValueType::double_type()});
    InterpretedFunction ifun(FastValueBuilderFactory::get(), *fun, node_types);
    auto llvm_fun = cfun.get_function();
    //-------------------------------------------------------------------------
    double a = -1.0/3.0;
    double b = 1.0;
    std::vector<double> ab({a, b});
    SimpleParams params(ab);
    InterpretedFunction::Context ictx(ifun);
    //-------------------------------------------------------------------------
    const Value &result_value = ifun.eval(ictx, params);
    double ifun_res = result_value.as_double();
    double llvm_res = llvm_fun(&ab[0]);
    double gcc_res = gcc_fun(a, b);
    fprintf(stderr, "ifun_res: %a\n", ifun_res);
    fprintf(stderr, "llvm_res: %a\n", llvm_res);
    fprintf(stderr, "gcc_res:  %a\n", gcc_res);
    EXPECT_EQ(ifun_res, llvm_res);
    EXPECT_DOUBLE_EQ(llvm_res + 1.0, gcc_res + 1.0);
    if (llvm_res != gcc_res) {
        fprintf(stderr, "WARNING: diverging results caused by fused multiply add\n");
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
