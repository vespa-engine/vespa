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

struct EvalCtx : TensorFunction::Input {
    const TensorEngine &engine;
    Stash stash;
    ErrorValue error;
    std::map<size_t, Value::UP> tensors;
    EvalCtx(const TensorEngine &engine_in)
        : engine(engine_in), stash(), error(), tensors() {}
    ~EvalCtx() {}
    void add_tensor(Value::UP tensor, size_t id) {
        tensors.emplace(id, std::move(tensor));
    }
    const Value &get_tensor(size_t id) const override {
        if (tensors.count(id) == 0) {
            return error;
        }
        return *tensors.find(id)->second;
    }
    const Value &eval(const TensorFunction &fun) { return fun.eval(*this, stash); }
    TensorFunction::UP compile(tensor_function::Node_UP expr) const {
        return engine.compile(std::move(expr));
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
    ctx.add_tensor(ctx.make_tensor_inject(), 1);
    Value::UP expect = ctx.make_tensor_inject();
    auto fun = inject(ValueType::from_spec("tensor(x[2],y[2])"), 1);
    EXPECT_EQUAL(expect->type(), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that partial tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_reduce_input(), 1);
    Value::UP expect = ctx.make_tensor_reduce_y_output();
    auto fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), 1), Aggr::SUM, {"y"});
    EXPECT_EQUAL(expect->type(), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that full tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_reduce_input(), 1);
    auto fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), 1), Aggr::SUM, {});
    EXPECT_EQUAL(ValueType::from_spec("double"), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    EXPECT_EQUAL(21.0, ctx.eval(*prog).as_double());
}

TEST("require that tensor map works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_map_input(), 1);
    Value::UP expect = ctx.make_tensor_map_output();
    auto fun = map(inject(ValueType::from_spec("tensor(x{},y{})"), 1), operation::Neg::f);
    EXPECT_EQUAL(expect->type(), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that tensor join works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_apply_lhs(), 1);
    ctx.add_tensor(ctx.make_tensor_apply_rhs(), 2);
    Value::UP expect = ctx.make_tensor_apply_output();
    auto fun = join(inject(ValueType::from_spec("tensor(x{},y{})"), 1),
                    inject(ValueType::from_spec("tensor(y{},z{})"), 2),
                    operation::Mul::f);
    EXPECT_EQUAL(expect->type(), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
