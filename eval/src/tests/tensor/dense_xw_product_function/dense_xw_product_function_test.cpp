// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;

ValueType
makeVectorType(size_t numCells)
{
    return ValueType::tensor_type({{"x", numCells}});
}

ValueType
makeHappyResultType(size_t numCells)
{
    return ValueType::tensor_type({{"out", numCells}});
}

ValueType
makeSadResultType(size_t numCells)
{
    return ValueType::tensor_type({{"y", numCells}});
}

ValueType
makeHappyMatrixType(size_t rows, size_t cols)
{
    return ValueType::tensor_type({{"out", rows}, {"x", cols}});
}

ValueType
makeSadMatrixType(size_t rows, size_t cols)
{
    return ValueType::tensor_type({{"x", rows}, {"y", cols}});
}

tensor::Tensor::UP
makeVector(size_t numCells, double cellBias)
{
    DenseTensorBuilder builder;
    DenseTensorBuilder::Dimension dim = builder.defineDimension("x", numCells);
    for (size_t i = 0; i < numCells; ++i) {
        builder.addLabel(dim, i).addCell(i + cellBias);
    }
    return builder.build();
}

tensor::Tensor::UP
makeHappyMatrix(size_t rows, size_t cols)
{
    DenseTensorBuilder builder;
    DenseTensorBuilder::Dimension dimR = builder.defineDimension("out", rows);
    DenseTensorBuilder::Dimension dimC = builder.defineDimension("x", cols);
    for (size_t r = 0; r < rows; ++r) {
        for (size_t c = 0; c < cols; ++c) {
            builder.addLabel(dimR, r).addLabel(dimC, c).addCell(1.0 + r*16 + c*4);
        }
    }
    return builder.build();
}

tensor::Tensor::UP
makeSadMatrix(size_t rows, size_t cols)
{
    DenseTensorBuilder builder;
    DenseTensorBuilder::Dimension dimR = builder.defineDimension("x", rows);
    DenseTensorBuilder::Dimension dimC = builder.defineDimension("y", cols);
    for (size_t r = 0; r < rows; ++r) {
        for (size_t c = 0; c < cols; ++c) {
            builder.addLabel(dimR, r).addLabel(dimC, c).addCell(1.0 + r*16 + c*4);
        }
    }
    return builder.build();
}


tensor::Tensor::UP
calcProduct(const DenseTensor &lhs, const DenseTensor &rhs)
{
    return lhs.join(eval::operation::Mul::f, rhs)->reduce(eval::operation::Add::f, {"x"});
}

const DenseTensor &
asDenseTensor(const tensor::Tensor &tensor)
{
    return dynamic_cast<const DenseTensor &>(tensor);
}

class HappyFunctionInput
{
private:
    tensor::Tensor::UP _lhsTensor;
    tensor::Tensor::UP _rhsTensor;
    const DenseTensor &_lhsDenseTensor;
    const DenseTensor &_rhsDenseTensor;
    std::vector<Value::CREF> _params;
public:
    const ValueType _resType;
    const size_t _vecSize;
    const size_t _resSize;

    HappyFunctionInput(size_t vsz, size_t rsz)
        : _lhsTensor(makeVector(vsz, 1.0)),
          _rhsTensor(makeHappyMatrix(rsz, vsz)),
          _lhsDenseTensor(asDenseTensor(*_lhsTensor)),
          _rhsDenseTensor(asDenseTensor(*_rhsTensor)),
          _resType(makeHappyResultType(rsz)),
          _vecSize(vsz),
          _resSize(rsz)
    {
        _params.emplace_back(_lhsDenseTensor);
        _params.emplace_back(_rhsDenseTensor);
    }
    ConstArrayRef<Value::CREF> get() const { return _params; }
    tensor::Tensor::UP expectedProduct() const {
        return calcProduct(_lhsDenseTensor, _rhsDenseTensor);
    }
};

class SadFunctionInput
{
private:
    tensor::Tensor::UP _lhsTensor;
    tensor::Tensor::UP _rhsTensor;
    const DenseTensor &_lhsDenseTensor;
    const DenseTensor &_rhsDenseTensor;
    std::vector<Value::CREF> _params;
public:
    const ValueType _resType;
    const size_t _vecSize;
    const size_t _resSize;

    SadFunctionInput(size_t vsz, size_t rsz)
        : _lhsTensor(makeVector(vsz, 1.0)),
          _rhsTensor(makeSadMatrix(vsz, rsz)),
          _lhsDenseTensor(asDenseTensor(*_lhsTensor)),
          _rhsDenseTensor(asDenseTensor(*_rhsTensor)),
          _resType(makeSadResultType(rsz)),
          _vecSize(vsz),
          _resSize(rsz)
    {
        _params.emplace_back(_lhsDenseTensor);
        _params.emplace_back(_rhsDenseTensor);
    }
    ConstArrayRef<Value::CREF> get() const { return _params; }
    tensor::Tensor::UP expectedProduct() const {
        return calcProduct(_lhsDenseTensor, _rhsDenseTensor);
    }
};

const DenseTensorView & downcast(const Value &v)
{
    const eval::Tensor *t = v.as_tensor();
    ASSERT_TRUE(t);
    const DenseTensorView *d = dynamic_cast<const DenseTensorView *>(t);
    ASSERT_TRUE(d);
    return *d;
}

struct Fixture
{
    HappyFunctionInput input1;
    SadFunctionInput input2;
    DenseXWProductFunction function1;
    DenseXWProductFunction function2;
    Fixture(size_t a, size_t b);
    ~Fixture();
    void evalCheck() const {
        Stash stash;
        const Value &result1 = function1.eval(input1.get(), stash);
        EXPECT_EQUAL(result1.type(), input1._resType);
        // LOG(info, "eval(): (%s) * (%s) = (%s)",
        //     input1.get()[0].get().type().to_spec().c_str(),
        //     input1.get()[1].get().type().to_spec().c_str(),
        //     result1.type().to_spec().c_str());
        auto expect1 = input1.expectedProduct();
        // LOG(info, "expect: %s", downcast(*expect1).toSpec().to_string().c_str());
        // LOG(info, "actual: %s", downcast(result1).toSpec().to_string().c_str());
        EXPECT_TRUE(expect1->equals(downcast(result1)));

        const Value &result2 = function2.eval(input2.get(), stash);
        EXPECT_EQUAL(result2.type(), input2._resType);
        // LOG(info, "eval(): (%s) * (%s) = (%s)",
        //     input2.get()[0].get().type().to_spec().c_str(),
        //     input2.get()[1].get().type().to_spec().c_str(),
        //     result2.type().to_spec().c_str());
        auto expect2 = input2.expectedProduct();
        // LOG(info, "expect: %s", downcast(*expect2).toSpec().to_string().c_str());
        // LOG(info, "actual: %s", downcast(result2).toSpec().to_string().c_str());
        EXPECT_TRUE(expect2->equals(downcast(result2)));
    }
};

Fixture::Fixture(size_t a, size_t b)
    : input1(a, b),
      input2(a, b),
      function1(input1._resType, 0, 1, input1._vecSize, input1._resSize, true),
      function2(input2._resType, 0, 1, input2._vecSize, input2._resSize, false)
{ }

Fixture::~Fixture() { }

TEST_F("require that empty product is correct", Fixture(0, 0))
{
    f.evalCheck();
}

TEST_F("require that basic product with size 1 is correct", Fixture(1, 1))
{
    f.evalCheck();
}

TEST_F("require that basic product with size 2 is correct", Fixture(2, 2))
{
    f.evalCheck();
}

TEST_F("require that basic product with size 3/4 is correct", Fixture(3, 4))
{
    f.evalCheck();
}

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
        return fun.eval(params, stash);
    }
    const TensorFunction &compile(const tensor_function::Node &expr) {
        return engine.compile(expr, stash);
    }
    Value::UP make_input_vector() {
        return engine.from_spec(
                TensorSpec("tensor(x[3])")
                .add({{"x", 0}}, 1.0)
                .add({{"x", 1}}, 2.0)
                .add({{"x", 2}}, 3.0));
    }
    Value::UP make_tensor_bad_weights() {
        return engine.from_spec(
                TensorSpec("tensor(x[3],y[2])")
                .add({{"x",0},{"y",0}}, 0)
                .add({{"x",1},{"y",0}}, 7)
                .add({{"x",2},{"y",0}}, 0)
                .add({{"x",0},{"y",1}}, 0)
                .add({{"x",1},{"y",1}}, 0)
                .add({{"x",2},{"y",1}}, 11));
    }
    Value::UP make_tensor_good_weights() {
        return engine.from_spec(
                TensorSpec("tensor(out[2],x[3])")
                .add({{"x",0},{"out",0}}, 0)
                .add({{"x",1},{"out",0}}, 0)
                .add({{"x",2},{"out",0}}, 7)
                .add({{"x",0},{"out",1}}, 0)
                .add({{"x",1},{"out",1}}, 11)
                .add({{"x",2},{"out",1}}, 0));
    }
    Value::UP make_tensor_bad_output() {
        return engine.from_spec(
                TensorSpec("tensor(y[2])")
                .add({{"y",0}}, 14)
                .add({{"y",1}}, 33));
    }
    Value::UP make_tensor_good_output() {
        return engine.from_spec(
                TensorSpec("tensor(out[2])")
                .add({{"out",0}}, 21)
                .add({{"out",1}}, 22));
    }
};

void verify_equal(const Value &expect, const Value &value) {
    const eval::Tensor *tensor = value.as_tensor();
    ASSERT_TRUE(tensor != nullptr);
    const eval::Tensor *expect_tensor = expect.as_tensor();
    ASSERT_TRUE(expect_tensor != nullptr);
    ASSERT_EQUAL(&expect_tensor->engine(), &tensor->engine());
    auto expect_spec = expect_tensor->engine().to_spec(expect);
    auto value_spec = tensor->engine().to_spec(value);
    EXPECT_EQUAL(expect_spec, value_spec);
}


TEST("require that xw product gives expected result with SimpleTensorEngine") {
    using namespace vespalib::eval::tensor_function;

    EvalCtx ctx(SimpleTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_input_vector());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_good_weights());
    Value::UP expect = ctx.make_tensor_good_output();
    const auto &jtf = join(inject(ValueType::from_spec("tensor(x[3])"), a_id, ctx.stash),
                           inject(ValueType::from_spec("tensor(out[2],x[3])"), b_id, ctx.stash),
                           operation::Mul::f, ctx.stash);
    const auto &fun = reduce(jtf, Aggr::SUM, {"x"}, ctx.stash);
    EXPECT_EQUAL(expect->type(), fun.result_type);
    const auto &prog = ctx.compile(fun);
    TEST_DO(verify_equal(*expect, ctx.eval(prog)));
}

TEST("require that xw product works") {
    using namespace vespalib::eval::tensor_function;

    EvalCtx ctx(DefaultTensorEngine::ref());
    size_t a_id = ctx.add_tensor(ctx.make_input_vector());
    size_t b_id = ctx.add_tensor(ctx.make_tensor_good_weights());
    Value::UP expect = ctx.make_tensor_good_output();
    DenseXWProductFunction fun(ValueType::from_spec("tensor(out[2])"),
                               a_id, b_id,
                               3, 2,
                               true);
    TEST_DO(verify_equal(*expect, ctx.eval(fun)));
}


TEST_MAIN() { TEST_RUN_ALL(); }
