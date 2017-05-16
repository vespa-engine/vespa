// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    operation::Neg neg;
    ErrorValue error;
    std::map<size_t, Value::UP> tensors;
    EvalCtx(const TensorEngine &engine_in)
        : engine(engine_in), stash(), neg(), error(), tensors() {}
    ~EvalCtx() { }
    void add_tensor(std::unique_ptr<Tensor> tensor, size_t id) {
        tensors.emplace(id, std::make_unique<TensorValue>(std::move(tensor)));
    }
    const Value &get_tensor(size_t id) const override {
        if (tensors.count(id) == 0) {
            return error;
        }
        return *tensors.find(id)->second;
    }
    const UnaryOperation &get_map_operation(size_t id) const override {
        ASSERT_EQUAL(42u, id);
        return neg;
    }
    const Value &eval(const TensorFunction &fun) { return fun.eval(*this, stash); }
    const ValueType type(const Tensor &tensor) const { return engine.type_of(tensor); }
    TensorFunction::UP compile(tensor_function::Node_UP expr) const {
        return engine.compile(std::move(expr));
    }
    std::unique_ptr<Tensor> make_tensor_inject() {
        return engine.create(
                TensorSpec("tensor(x[2],y[2])")
                .add({{"x", 0}, {"y", 0}}, 1.0)
                .add({{"x", 0}, {"y", 1}}, 2.0)
                .add({{"x", 1}, {"y", 0}}, 3.0)
                .add({{"x", 1}, {"y", 1}}, 4.0));
    }
    std::unique_ptr<Tensor> make_tensor_reduce_input() {
        return engine.create(
                TensorSpec("tensor(x[3],y[2])")
                .add({{"x",0},{"y",0}}, 1)
                .add({{"x",1},{"y",0}}, 2)
                .add({{"x",2},{"y",0}}, 3)
                .add({{"x",0},{"y",1}}, 4)
                .add({{"x",1},{"y",1}}, 5)
                .add({{"x",2},{"y",1}}, 6));
    }
    std::unique_ptr<Tensor> make_tensor_reduce_y_output() {
        return engine.create(
                TensorSpec("tensor(x[3])")
                .add({{"x",0}}, 5)
                .add({{"x",1}}, 7)
                .add({{"x",2}}, 9));
    }
    std::unique_ptr<Tensor> make_tensor_map_input() {
        return engine.create(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, 1)
                .add({{"x","2"},{"y","1"}}, -3)
                .add({{"x","1"},{"y","2"}}, 5));
    }
    std::unique_ptr<Tensor> make_tensor_map_output() {
        return engine.create(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, -1)
                .add({{"x","2"},{"y","1"}}, 3)
                .add({{"x","1"},{"y","2"}}, -5));
    }
    std::unique_ptr<Tensor> make_tensor_apply_lhs() {
        return engine.create(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, 1)
                .add({{"x","2"},{"y","1"}}, 3)
                .add({{"x","1"},{"y","2"}}, 5));
    }
    std::unique_ptr<Tensor> make_tensor_apply_rhs() {
        return engine.create(
                TensorSpec("tensor(y{},z{})")
                .add({{"y","1"},{"z","1"}}, 7)
                .add({{"y","2"},{"z","1"}}, 11)
                .add({{"y","1"},{"z","2"}}, 13));
    }
    std::unique_ptr<Tensor> make_tensor_apply_output() {
        return engine.create(
                TensorSpec("tensor(x{},y{},z{})")
                .add({{"x","1"},{"y","1"},{"z","1"}}, 7)
                .add({{"x","1"},{"y","1"},{"z","2"}}, 13)
                .add({{"x","2"},{"y","1"},{"z","1"}}, 21)
                .add({{"x","2"},{"y","1"},{"z","2"}}, 39)
                .add({{"x","1"},{"y","2"},{"z","1"}}, 55));
    }
};

void verify_equal(const Tensor &expect, const Value &value) {
    const Tensor *tensor = value.as_tensor();
    ASSERT_TRUE(tensor != nullptr);
    ASSERT_EQUAL(&expect.engine(), &tensor->engine());
    EXPECT_TRUE(expect.engine().equal(expect, *tensor));
}

TEST("require that tensor injection works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_inject(), 1);
    auto expect = ctx.make_tensor_inject();
    auto fun = inject(ValueType::from_spec("tensor(x[2],y[2])"), 1);
    EXPECT_EQUAL(ctx.type(*expect), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that partial tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_reduce_input(), 1);
    auto expect = ctx.make_tensor_reduce_y_output();
    auto fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), 1), operation::Add(), {"y"});
    EXPECT_EQUAL(ctx.type(*expect), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that full tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_reduce_input(), 1);
    auto fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), 1), operation::Add(), {});
    EXPECT_EQUAL(ValueType::from_spec("double"), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    EXPECT_EQUAL(21.0, ctx.eval(*prog).as_double());
}

TEST("require that tensor map works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_map_input(), 1);
    auto expect = ctx.make_tensor_map_output();
    auto fun = map(42, inject(ValueType::from_spec("tensor(x{},y{})"), 1));
    EXPECT_EQUAL(ctx.type(*expect), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST("require that tensor apply works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    ctx.add_tensor(ctx.make_tensor_apply_lhs(), 1);
    ctx.add_tensor(ctx.make_tensor_apply_rhs(), 2);
    auto expect = ctx.make_tensor_apply_output();
    auto fun = apply(operation::Mul(),
                     inject(ValueType::from_spec("tensor(x{},y{})"), 1),
                     inject(ValueType::from_spec("tensor(y{},z{})"), 2));
    EXPECT_EQUAL(ctx.type(*expect), fun->result_type);
    auto prog = ctx.compile(std::move(fun));
    TEST_DO(verify_equal(*expect, ctx.eval(*prog)));
}

TEST_MAIN() { TEST_RUN_ALL(); }
