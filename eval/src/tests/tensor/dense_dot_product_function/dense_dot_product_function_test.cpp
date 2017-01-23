// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;

ValueType
makeType(size_t numCells)
{
    return ValueType::tensor_type({{"x", numCells}});
}

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

class FunctionInput : public TensorFunction::Input
{
private:
    tensor::Tensor::UP _lhsTensor;
    tensor::Tensor::UP _rhsTensor;
    const DenseTensor &_lhsDenseTensor;
    const DenseTensor &_rhsDenseTensor;
    TensorValue _lhsValue;
    TensorValue _rhsValue;

public:
    FunctionInput(size_t lhsNumCells, size_t rhsNumCells)
        : _lhsTensor(makeTensor(lhsNumCells, 3.0)),
          _rhsTensor(makeTensor(rhsNumCells, 5.0)),
          _lhsDenseTensor(asDenseTensor(*_lhsTensor)),
          _rhsDenseTensor(asDenseTensor(*_rhsTensor)),
          _lhsValue(std::make_unique<DenseTensor>(_lhsDenseTensor.type(),
                                                  _lhsDenseTensor.cells())),
          _rhsValue(std::make_unique<DenseTensor>(_rhsDenseTensor.type(),
                                                  _rhsDenseTensor.cells()))
    {}
    virtual const Value &get_tensor(size_t id) const override {
        if (id == 0) {
            return _lhsValue;
        } else {
            return _rhsValue;
        }
    }
    virtual const UnaryOperation &get_map_operation(size_t) const override {
        abort();
    }
    double expectedDotProduct() const {
        return calcDotProduct(_lhsDenseTensor, _rhsDenseTensor);
    }
};

struct Fixture
{
    DenseDotProductFunction function;
    FunctionInput input;
    Fixture(size_t lhsNumCells, size_t rhsNumCells)
        : function(0, 1),
          input(lhsNumCells, rhsNumCells)
    {
    }
    double eval() const {
        Stash stash;
        const Value &result = function.eval(input, stash);
        ASSERT_TRUE(result.is_double());
        LOG(info, "eval(): (%s) * (%s) = %f",
            input.get_tensor(0).type().to_spec().c_str(),
            input.get_tensor(1).type().to_spec().c_str(),
            result.as_double());
        return result.as_double();
    }
};

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
