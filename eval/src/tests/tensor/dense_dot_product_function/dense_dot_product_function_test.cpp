// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;

tensor::Tensor::UP
makeTensor(size_t numCells, double cellBias)
{
    DenseTensorBuilder builder;
    DenseTensorBuilder::Dimension dim = builder.defineDimension("x", numCells);
    for (size_t i = 0; i < numCells; ++i) {
        builder.addLabel(dim, i).addCell(i + cellBias);
    }
    return builder.build();
}

double
calcDotProduct(const DenseTensor &lhs, const DenseTensor &rhs)
{
    size_t numCells = std::min(lhs.cells().size(), rhs.cells().size());
    double result = 0;
    for (size_t i = 0; i < numCells; ++i) {
        result += (lhs.cells()[i] * rhs.cells()[i]);
    }
    return result;
}

const DenseTensor &
asDenseTensor(const tensor::Tensor &tensor)
{
    return dynamic_cast<const DenseTensor &>(tensor);
}

class FunctionInput
{
private:
    tensor::Tensor::UP _lhsTensor;
    tensor::Tensor::UP _rhsTensor;
    const DenseTensor &_lhsDenseTensor;
    const DenseTensor &_rhsDenseTensor;
    std::vector<Value::CREF> _params;

public:
    FunctionInput(size_t lhsNumCells, size_t rhsNumCells)
        : _lhsTensor(makeTensor(lhsNumCells, 3.0)),
          _rhsTensor(makeTensor(rhsNumCells, 5.0)),
          _lhsDenseTensor(asDenseTensor(*_lhsTensor)),
          _rhsDenseTensor(asDenseTensor(*_rhsTensor))
    {
        _params.emplace_back(_lhsDenseTensor);
        _params.emplace_back(_rhsDenseTensor);
    }
    SimpleObjectParams get() const { return SimpleObjectParams(_params); }
    const Value &param(size_t idx) const { return _params[idx]; }
    double expectedDotProduct() const {
        return calcDotProduct(_lhsDenseTensor, _rhsDenseTensor);
    }
};

struct Fixture
{
    FunctionInput input;
    tensor_function::Inject a;
    tensor_function::Inject b;
    DenseDotProductFunction function;
    Fixture(size_t lhsNumCells, size_t rhsNumCells);
    ~Fixture();
    double eval() const {
        InterpretedFunction ifun(DefaultTensorEngine::ref(), function);
        InterpretedFunction::Context ictx(ifun);
        const Value &result = ifun.eval(ictx, input.get());
        ASSERT_TRUE(result.is_double());
        LOG(info, "eval(): (%s) * (%s) = %f",
            input.param(0).type().to_spec().c_str(),
            input.param(1).type().to_spec().c_str(),
            result.as_double());
        return result.as_double();
    }
};

Fixture::Fixture(size_t lhsNumCells, size_t rhsNumCells)
    : input(lhsNumCells, rhsNumCells),
      a(input.param(0).type(), 0),
      b(input.param(1).type(), 1),
      function(a, b)
{ }

Fixture::~Fixture() { }

void
assertDotProduct(size_t numCells)
{
    Fixture f(numCells, numCells);
    EXPECT_EQUAL(f.input.expectedDotProduct(), f.eval());
}

void
assertDotProduct(size_t lhsNumCells, size_t rhsNumCells)
{
    Fixture f(lhsNumCells, rhsNumCells);
    EXPECT_EQUAL(f.input.expectedDotProduct(), f.eval());
}

TEST_F("require that empty dot product is correct", Fixture(0, 0))
{
    EXPECT_EQUAL(0.0, f.eval());
}

TEST_F("require that basic dot product with equal sizes is correct", Fixture(2, 2))
{
    EXPECT_EQUAL((3.0 * 5.0) + (4.0 * 6.0), f.eval());
}

TEST_F("require that basic dot product with un-equal sizes is correct", Fixture(2, 3))
{
    EXPECT_EQUAL((3.0 * 5.0) + (4.0 * 6.0), f.eval());
}

TEST_F("require that basic dot product with un-equal sizes is correct", Fixture(3, 2))
{
    EXPECT_EQUAL((3.0 * 5.0) + (4.0 * 6.0), f.eval());
}

TEST("require that dot product with equal sizes is correct")
{
    TEST_DO(assertDotProduct(8));
    TEST_DO(assertDotProduct(16));
    TEST_DO(assertDotProduct(32));
    TEST_DO(assertDotProduct(64));
    TEST_DO(assertDotProduct(128));
    TEST_DO(assertDotProduct(256));
    TEST_DO(assertDotProduct(512));
    TEST_DO(assertDotProduct(1024));

    TEST_DO(assertDotProduct(8 + 3));
    TEST_DO(assertDotProduct(16 + 3));
    TEST_DO(assertDotProduct(32 + 3));
    TEST_DO(assertDotProduct(64 + 3));
    TEST_DO(assertDotProduct(128 + 3));
    TEST_DO(assertDotProduct(256 + 3));
    TEST_DO(assertDotProduct(512 + 3));
    TEST_DO(assertDotProduct(1024 + 3));
}

TEST("require that dot product with un-equal sizes is correct")
{
    TEST_DO(assertDotProduct(8, 8 + 3));
    TEST_DO(assertDotProduct(16, 16 + 3));
    TEST_DO(assertDotProduct(32, 32 + 3));
    TEST_DO(assertDotProduct(64, 64 + 3));
    TEST_DO(assertDotProduct(128, 128 + 3));
    TEST_DO(assertDotProduct(256, 256 + 3));
    TEST_DO(assertDotProduct(512, 512 + 3));
    TEST_DO(assertDotProduct(1024, 1024 + 3));
}

TEST_MAIN() { TEST_RUN_ALL(); }
