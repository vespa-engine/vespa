// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/vector_from_doubles_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &ref_engine = SimpleTensorEngine::ref();
const TensorEngine &prod_engine = DefaultTensorEngine::ref();

//-----------------------------------------------------------------------------
// verify that optimize() works as expected

template<typename OPT>
bool treeContains(const TensorFunction &expr) {
    using Child = TensorFunction::Child;
    Child root(expr);
    std::vector<Child::CREF> nodes({root});
    for (size_t i = 0; i < nodes.size(); ++i) {
        nodes[i].get().get().push_children(nodes);
    }
    for (const Child &child : nodes) {
        if (as<OPT>(child.get())) {
            return true;
        }
    }
    return false;
}

const TensorFunction &optimize_fun(const Function &fun, const NodeTypes &node_types, Stash &stash) {
    const TensorFunction &plain_fun = make_tensor_function(prod_engine, fun.root(), node_types, stash);
    return prod_engine.optimize(plain_fun, stash);
}

std::vector<ValueType> extract_types(size_t n, const std::vector<TensorSpec> &input) {
    std::vector<ValueType> vec;
    for (const TensorSpec &spec : input) {
        vec.push_back(ValueType::from_spec(spec.type()));
    }
    while (vec.size() < n) {
        vec.push_back(ValueType::double_type());
    }
    return vec;
}

struct Context {
    Stash stash;
    Function function;
    std::vector<TensorSpec> input;
    std::vector<ValueType> input_types;
    NodeTypes node_types;
    const TensorFunction &optimized;

    Context(const vespalib::string &expr, std::vector<TensorSpec> in)
        : stash(),
          function(Function::parse(expr)),
          input(in),
          input_types(extract_types(function.num_params(), input)),
          node_types(function, input_types),
          optimized(optimize_fun(function, node_types, stash))
    {
        EXPECT_EQUAL(actual(), expected());
    }

    ~Context() {}

    struct Params : LazyParams {
        std::vector<Value::UP> values;
        Value &resolve(size_t idx, Stash &) const override {
            return *values[idx];
        }
    };

    Params gen_params(const TensorEngine &engine) {
        Params p;
        for (const TensorSpec &spec : input) {
            p.values.emplace_back(engine.from_spec(spec));
        }
        while (p.values.size() < function.num_params()) {
            double v = 1.0 + p.values.size();
            p.values.emplace_back(std::make_unique<DoubleValue>(v));
        }
        return p;
    }

    TensorSpec actual() {
        const LazyParams &params = gen_params(prod_engine);
        InterpretedFunction prodIfun(prod_engine, optimized);
        InterpretedFunction::Context prodIctx(prodIfun);
        const Value &result = prodIfun.eval(prodIctx, params);
        return prod_engine.to_spec(result);
    }

    TensorSpec expected() {
        const LazyParams &params = gen_params(ref_engine);
        InterpretedFunction refIfun(ref_engine, function, NodeTypes());
        InterpretedFunction::Context refIctx(refIfun);
        const Value &result = refIfun.eval(refIctx, params);
        return ref_engine.to_spec(result);
    }

};

//-----------------------------------------------------------------------------

void verify_all_optimized(const vespalib::string &expr) {
    Context context(expr, {});
    EXPECT_TRUE(treeContains<VectorFromDoublesFunction>(context.optimized));
    EXPECT_FALSE(treeContains<eval::tensor_function::Concat>(context.optimized));
}

TEST("require that multiple concats are optimized") {
    TEST_DO(verify_all_optimized("concat(a,b,x)"));
    TEST_DO(verify_all_optimized("concat(a,concat(b,concat(c,d,x),x),x)"));
    TEST_DO(verify_all_optimized("concat(concat(concat(a,b,x),c,x),d,x)"));
    TEST_DO(verify_all_optimized("concat(concat(a,b,x),concat(c,d,x),x)"));
}

//-----------------------------------------------------------------------------

void verify_some_optimized(const vespalib::string &expr) {
    Context context(expr, {});
    EXPECT_TRUE(treeContains<VectorFromDoublesFunction>(context.optimized));
    EXPECT_TRUE(treeContains<eval::tensor_function::Concat>(context.optimized));
}

TEST("require that concat along different dimension is not optimized") {
    TEST_DO(verify_some_optimized("concat(concat(a,b,x),concat(c,d,x),y)"));
}

//-----------------------------------------------------------------------------

TEST("require that concat of vector and double is not optimized") {
    TensorSpec vecspec = TensorSpec("tensor(x[3])")
                         .add({{"x", 0}}, 7.0)
                         .add({{"x", 1}}, 11.0)
                         .add({{"x", 2}}, 13.0);
    TensorSpec dblspec = TensorSpec("double")
                         .add({}, 19.0);
    Context context("concat(a,b,x)", {vecspec, dblspec});
    EXPECT_TRUE(treeContains<eval::tensor_function::Concat>(context.optimized));
    EXPECT_FALSE(treeContains<VectorFromDoublesFunction>(context.optimized));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
