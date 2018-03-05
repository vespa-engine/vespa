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
    InterpretedFunction::UP ifun;
    std::unique_ptr<InterpretedFunction::Context> ictx;
    EvalCtx(const TensorEngine &engine_in)
        : engine(engine_in), stash(), error(), tensors(), params(), ifun(), ictx() {}
    ~EvalCtx() {}
    size_t add_tensor(Value::UP tensor) {
        size_t id = params.size();
        params.emplace_back(*tensor);
        tensors.push_back(std::move(tensor));
        return id;
    }
    void replace_tensor(size_t idx, Value::UP tensor) {
        params[idx] = *tensor;
        tensors[idx] = std::move(tensor);
    }
    const Value &eval(const TensorFunction &fun) {
        ifun = std::make_unique<InterpretedFunction>(engine, fun);
        ictx = std::make_unique<InterpretedFunction::Context>(*ifun);
        return ifun->eval(*ictx, SimpleObjectParams(params));
    }
    const TensorFunction &compile(const tensor_function::Node &expr) {
        return engine.optimize(expr, stash);
    }
    Value::UP make_true() {
        return engine.from_spec(TensorSpec("double").add({}, 1.0));
    }
    Value::UP make_false() {
        return engine.from_spec(TensorSpec("double").add({}, 0.0));
    }
    Value::UP make_tensor_matrix_first_half() {
        return engine.from_spec(
                TensorSpec("tensor(x[2])")
                .add({{"x", 0}}, 1.0)
                .add({{"x", 1}}, 3.0));
    }
    Value::UP make_tensor_matrix_second_half() {
        return engine.from_spec(
                TensorSpec("tensor(x[2])")
                .add({{"x", 0}}, 2.0)
                .add({{"x", 1}}, 4.0));
    }
    Value::UP make_tensor_matrix() {
        return engine.from_spec(
                TensorSpec("tensor(x[2],y[2])")
                .add({{"x", 0}, {"y", 0}}, 1.0)
                .add({{"x", 0}, {"y", 1}}, 2.0)
                .add({{"x", 1}, {"y", 0}}, 3.0)
                .add({{"x", 1}, {"y", 1}}, 4.0));
    }
    Value::UP make_tensor_matrix_renamed() {
        return engine.from_spec(
                TensorSpec("tensor(y[2],z[2])")
                .add({{"z", 0}, {"y", 0}}, 1.0)
                .add({{"z", 0}, {"y", 1}}, 2.0)
                .add({{"z", 1}, {"y", 0}}, 3.0)
                .add({{"z", 1}, {"y", 1}}, 4.0));
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
    Value::UP make_tensor_join_lhs() {
        return engine.from_spec(
                TensorSpec("tensor(x{},y{})")
                .add({{"x","1"},{"y","1"}}, 1)
                .add({{"x","2"},{"y","1"}}, 3)
                .add({{"x","1"},{"y","2"}}, 5));
    }
    Value::UP make_tensor_join_rhs() {
        return engine.from_spec(
                TensorSpec("tensor(y{},z{})")
                .add({{"y","1"},{"z","1"}}, 7)
                .add({{"y","2"},{"z","1"}}, 11)
                .add({{"y","1"},{"z","2"}}, 13));
    }
    Value::UP make_tensor_join_output() {
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

TEST("require that const_value works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    Value::UP my_const = ctx.make_tensor_matrix();
    Value::UP expect = ctx.make_tensor_matrix();
    const auto &fun = const_value(*my_const, ctx.stash);
    EXPECT_TRUE(!fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that tensor injection works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_matrix());
    Value::UP expect = ctx.make_tensor_matrix();
    const auto &fun = inject(ValueType::from_spec("tensor(x[2],y[2])"), a_id, ctx.stash);
    EXPECT_TRUE(!fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that partial tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_reduce_input());
    Value::UP expect = ctx.make_tensor_reduce_y_output();
    const auto &fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), a_id, ctx.stash), Aggr::SUM, {"y"}, ctx.stash);
    EXPECT_TRUE(fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that full tensor reduction works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_reduce_input());
    const auto &fun = reduce(inject(ValueType::from_spec("tensor(x[3],y[2])"), a_id, ctx.stash), Aggr::SUM, {}, ctx.stash);
    EXPECT_TRUE(fun.result_is_mutable());
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
    EXPECT_TRUE(fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that tensor join works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_join_lhs());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_join_rhs());
    Value::UP expect = ctx.make_tensor_join_output();
    const auto &fun = join(inject(ValueType::from_spec("tensor(x{},y{})"), a_id, ctx.stash),
                           inject(ValueType::from_spec("tensor(y{},z{})"), b_id, ctx.stash),
                           operation::Mul::f, ctx.stash);
    EXPECT_TRUE(fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that tensor concat works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_matrix_first_half());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_matrix_second_half());
    Value::UP expect = ctx.make_tensor_matrix();
    const auto &fun = concat(inject(ValueType::from_spec("tensor(x[2])"), a_id, ctx.stash),
                             inject(ValueType::from_spec("tensor(x[2])"), b_id, ctx.stash),
                             "y", ctx.stash);
    EXPECT_TRUE(fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that tensor rename works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_tensor_matrix());
    Value::UP expect = ctx.make_tensor_matrix_renamed();
    const auto &fun = rename(inject(ValueType::from_spec("tensor(x[2],y[2])"), a_id, ctx.stash),
                             {"x"}, {"z"}, ctx.stash);
    EXPECT_TRUE(fun.result_is_mutable());
    EXPECT_EQUAL(expect->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that if_node works") {
    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_true());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_matrix_first_half());
    size_t c_id = ctx.add_tensor(ctx.make_tensor_matrix_second_half());
    Value::UP expect_true = ctx.make_tensor_matrix_first_half();
    Value::UP expect_false = ctx.make_tensor_matrix_second_half();
    const auto &fun = if_node(inject(ValueType::double_type(), a_id, ctx.stash),
                              inject(ValueType::from_spec("tensor(x[2])"), b_id, ctx.stash),
                              inject(ValueType::from_spec("tensor(x[2])"), c_id, ctx.stash), ctx.stash);
    EXPECT_TRUE(!fun.result_is_mutable());
    EXPECT_EQUAL(expect_true->type(), fun.result_type());
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect_true, ctx.eval(prog)));
    ctx.replace_tensor(a_id, ctx.make_false());
    TEST_DO(verify_equal(*expect_false, ctx.eval(prog)));
}

TEST("require that if_node result is mutable only when both children produce mutable results") {
    Stash stash;
    const Node &cond = inject(DoubleValue::double_type(), 0, stash);
    const Node &a = inject(ValueType::from_spec("tensor(x[2])"), 0, stash);
    const Node &b = inject(ValueType::from_spec("tensor(x[3])"), 0, stash);
    const Node &tmp = concat(a, b, "x", stash); // will be mutable
    const Node &if_con_con = if_node(cond, a, b, stash);
    const Node &if_mut_con = if_node(cond, tmp, b, stash);
    const Node &if_con_mut = if_node(cond, a, tmp, stash);
    const Node &if_mut_mut = if_node(cond, tmp, tmp, stash);
    EXPECT_TRUE(!if_con_con.result_is_mutable());
    EXPECT_TRUE(!if_mut_con.result_is_mutable());
    EXPECT_TRUE(!if_con_mut.result_is_mutable());
    EXPECT_TRUE(if_mut_mut.result_is_mutable());
}

TEST("require that if_node gets expected result type") {
    Stash stash;
    const Node &a = inject(DoubleValue::double_type(), 0, stash);
    const Node &b = inject(ValueType::from_spec("tensor(x[2])"), 0, stash);
    const Node &c = inject(ValueType::from_spec("tensor(x[3])"), 0, stash);
    const Node &d = inject(ValueType::from_spec("tensor(x[])"), 0, stash);
    const Node &e = inject(ValueType::from_spec("tensor(y[3])"), 0, stash);
    const Node &f = inject(ValueType::from_spec("double"), 0, stash);
    const Node &g = inject(ValueType::from_spec("error"), 0, stash);
    const Node &if_same = if_node(a, b, b, stash);
    const Node &if_similar = if_node(a, b, c, stash);
    const Node &if_subtype = if_node(a, b, d, stash);
    const Node &if_different = if_node(a, b, e, stash);
    const Node &if_different_types = if_node(a, b, f, stash);
    const Node &if_with_error = if_node(a, b, g, stash);
    EXPECT_EQUAL(if_same.result_type(), ValueType::from_spec("tensor(x[2])"));
    EXPECT_EQUAL(if_similar.result_type(), ValueType::from_spec("tensor(x[])"));
    EXPECT_EQUAL(if_subtype.result_type(), ValueType::from_spec("tensor(x[])"));
    EXPECT_EQUAL(if_different.result_type(), ValueType::from_spec("tensor"));
    EXPECT_EQUAL(if_different_types.result_type(), ValueType::from_spec("any"));
    EXPECT_EQUAL(if_with_error.result_type(), ValueType::from_spec("error"));
}

TEST("require that push_children works") {
    Stash stash;
    std::vector<Node::Child::CREF> refs;
    const Node &a = inject(DoubleValue::double_type(), 0, stash);
    const Node &b = inject(DoubleValue::double_type(), 1, stash);
    const Node &c = const_value(stash.create<DoubleValue>(1.0), stash);
    a.push_children(refs);
    b.push_children(refs);
    c.push_children(refs);
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
    concat(a, b, "x", stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 6u);
    EXPECT_EQUAL(&refs[4].get().get(), &a);
    EXPECT_EQUAL(&refs[5].get().get(), &b);
    //-------------------------------------------------------------------------
    rename(c, {}, {}, stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 7u);
    EXPECT_EQUAL(&refs[6].get().get(), &c);
    //-------------------------------------------------------------------------
    if_node(a, b, c, stash).push_children(refs);
    ASSERT_EQUAL(refs.size(), 10u);
    EXPECT_EQUAL(&refs[7].get().get(), &a);
    EXPECT_EQUAL(&refs[8].get().get(), &b);
    EXPECT_EQUAL(&refs[9].get().get(), &c);
    //-------------------------------------------------------------------------
}

TEST_MAIN() { TEST_RUN_ALL(); }
