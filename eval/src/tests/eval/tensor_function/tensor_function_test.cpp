// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/util/stash.h>
#include <map>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::tensor_function;

struct EvalCtx {
    const TensorEngine &engine;
    Stash stash;
    ErrorValue error;
    std::vector<Value::UP> tensors;
    std::vector<Value::CREF> params;
    EvalCtx(const TensorEngine &engine_in)
        : engine(engine_in), stash(), error(), tensors() {}
    ~EvalCtx() {}
    size_t add_tensor(Value::UP tensor) {
        size_t id = params.size();
        params.emplace_back(*tensor);
        tensors.push_back(std::move(tensor));
        return id;
    }
    const Value &eval(const TensorFunction &fun) {
        return fun.eval(SimpleObjectParams(params), stash);
    }
    const TensorFunction &compile(const tensor_function::Node &expr) {
        return engine.compile(expr, stash);
    }
    Value::UP make_tensor_inject() {
        return engine.from_spec(
                TensorSpec("tensor(x[2],y[2])")
                .add({{"x", 0}, {"y", 0}}, 1.0)
                .add({{"x", 0}, {"y", 1}}, 2.0)
                .add({{"x", 1}, {"y", 0}}, 3.0)
                .add({{"x", 1}, {"y", 1}}, 4.0));
    }
    Value::UP make_tensor_reduce_input() {
        return engine.from_spec(
                TensorSpec("tensor(x[3],y[2])")
                .add({{"x",0},{"y",0}}, 1)
                .add({{"x",1},{"y",0}}, 2)
                .add({{"x",2},{"y",0}}, 3)
                .add({{"x",0},{"y",1}}, 4)
                .add({{"x",1},{"y",1}}, 5)
                .add({{"x",2},{"y",1}}, 6));
    }
    Value::UP make_tensor_reduce_y_output() {
        return engine.from_spec(
                TensorSpec("tensor(x[3])")
                .add({{"x",0}}, 5)
                .add({{"x",1}}, 7)
                .add({{"x",2}}, 9));
    }
    Value::UP make_tensor_map_input() {
        return engine.from_spec(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, 1)
                .add({{"x","2"},{"y","1"}}, -3)
                .add({{"x","1"},{"y","2"}}, 5));
    }
    Value::UP make_tensor_map_output() {
        return engine.from_spec(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, -1)
                .add({{"x","2"},{"y","1"}}, 3)
                .add({{"x","1"},{"y","2"}}, -5));
    }
    Value::UP make_tensor_apply_lhs() {
        return engine.from_spec(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, 1)
                .add({{"x","2"},{"y","1"}}, 3)
                .add({{"x","1"},{"y","2"}}, 5));
    }
    Value::UP make_tensor_apply_rhs() {
        return engine.from_spec(
                TensorSpec("tensor(y{},z{})")
                .add({{"y","1"},{"z","1"}}, 7)
                .add({{"y","2"},{"z","1"}}, 11)
                .add({{"y","1"},{"z","2"}}, 13));
    }
    Value::UP make_tensor_apply_output() {
        return engine.from_spec(
                TensorSpec("tensor(x{},y{},z{})")
                .add({{"x","1"},{"y","1"},{"z","1"}}, 7)
                .add({{"x","1"},{"y","1"},{"z","2"}}, 13)
                .add({{"x","2"},{"y","1"},{"z","1"}}, 21)
                .add({{"x","2"},{"y","1"},{"z","2"}}, 39)
                .add({{"x","1"},{"y","2"},{"z","1"}}, 55));
    }
};

void verify_equal(const Value &expect, const Value &value) {
    const Tensor *tensor = value.as_tensor();
    ASSERT_TRUE(tensor != nullptr);
    const Tensor *expect_tensor = expect.as_tensor();
    ASSERT_TRUE(expect_tensor != nullptr);
    ASSERT_EQUAL(&expect_tensor->engine(), &tensor->engine());
    auto expect_spec = expect_tensor->engine().to_spec(expect);
    auto value_spec = tensor->engine().to_spec(value);
    EXPECT_EQUAL(expect_spec, value_spec);
}

TEST("require that tensor injection works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_inject());
    Value::UP expect = ctx.make_tensor_inject();
    const auto &fun = inject(ValueType::from_spec("tensor(x[2],y[2])"), a_id, ctx.stash);
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that partial tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_reduce_input());
    Value::UP expect = ctx.make_tensor_reduce_y_output();
    const auto &fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), a_id, ctx.stash), Aggr::SUM, {"y"}, ctx.stash);
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that full tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_reduce_input());
    const auto &fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), a_id, ctx.stash), Aggr::SUM, {}, ctx.stash);
    EXPECT_EQUAL(ValueType::from_spec("double"), fun.result_type());
    const auto &prog = ctx.compile(fun);
    const Value &result = ctx.eval(prog);
    EXPECT_TRUE(result.is_double());
    EXPECT_EQUAL(21.0, result.as_double());
}

TEST("require that tensor map works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_map_input());
    Value::UP expect = ctx.make_tensor_map_output();
    const auto &fun = map(inject(ValueType::from_spec("tensor(x{},y{})"), a_id, ctx.stash), operation::Neg::f, ctx.stash);
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that tensor join works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_apply_lhs());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_apply_rhs());
    Value::UP expect = ctx.make_tensor_apply_output();
    const auto &fun = join(inject(ValueType::from_spec("tensor(x{},y{})"), a_id, ctx.stash),
                           inject(ValueType::from_spec("tensor(y{},z{})"), b_id, ctx.stash),
                           operation::Mul::f, ctx.stash);
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that push_children works") {
    Stash stash;
    std::vector<Node::Child::CREF> refs;
    const Node &a = inject(ValueType::double_type(), 0, stash);
    const Node &b = inject(ValueType::double_type(), 1, stash);
    a.push_children(refs);
    b.push_children(refs);
    ASSERT_EQUAL(refs.size(), 0u);
    //-------------------------------------------------------------------------
    reduce(a, Aggr::SUM, {}, stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 1u);
    EXPECT_EQUAL(&refs[0].get().get(), &a);
    //-------------------------------------------------------------------------
    map(b, operation::Neg::f, stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 2u);
    EXPECT_EQUAL(&refs[1].get().get(), &b);
    //-------------------------------------------------------------------------
    join(a, b, operation::Add::f, stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 4u);
    EXPECT_EQUAL(&refs[2].get().get(), &a);
    EXPECT_EQUAL(&refs[3].get().get(), &b);
    //-------------------------------------------------------------------------
}

TEST_MAIN() { TEST_RUN_ALL(); }
