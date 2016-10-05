// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/dense/dense_tensor.h>
#include <vespa/vespalib/tensor/dense/dense_tensor_builder.h>
#include <vespa/vespalib/tensor/tensor_function.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>

using namespace vespalib::tensor;

using DenseTensorCells = std::map<std::map<vespalib::string, size_t>, double>;

namespace vespalib {
namespace tensor {

static bool operator==(const Tensor &lhs, const Tensor &rhs)
{
    return lhs.equals(rhs);
}

}
}

//-----------------------------------------------------------------------------

class MyInput : public TensorFunction::Input
{
private:
    std::vector<Tensor::CREF> tensors;
    std::vector<CellFunction::CREF> cell_functions;
    const Tensor &get_tensor(size_t id) const override {
        ASSERT_GREATER(tensors.size(), id);
        return tensors[id];
    }
    virtual const CellFunction &get_cell_function(size_t id) const override {
        ASSERT_GREATER(cell_functions.size(), id);
        return cell_functions[id];
    }
public:
    size_t add(const Tensor &tensor) {
        size_t id = tensors.size();
        tensors.push_back(tensor);
        return id;
    }
    size_t add(const CellFunction &cell_function) {
        size_t id = cell_functions.size();
        cell_functions.push_back(cell_function);
        return id;
    }
};

const Tensor &eval_tensor_checked(function::Node &function_ir, const TensorFunction::Input &input) {
    ASSERT_TRUE(function_ir.type().is_tensor());
    TensorFunction &function = function_ir; // compile step
    const Tensor &result = function.eval(input).as_tensor;
    EXPECT_EQUAL(result.getType(), function_ir.type());
    return result;
}

const Tensor &eval_tensor_unchecked(function::Node &function_ir, const TensorFunction::Input &input) {
    TensorFunction &function = function_ir; // compile step
    return function.eval(input).as_tensor;
}

const Tensor &eval_tensor(function::Node &function_ir, const TensorFunction::Input &input, bool check_types) {
    if (check_types) {
        return eval_tensor_checked(function_ir, input);
    } else {
        return eval_tensor_unchecked(function_ir, input);
    }
}

double eval_number(function::Node &function_ir, const TensorFunction::Input &input) {
    ASSERT_TRUE(function_ir.type().is_double());
    TensorFunction &function = function_ir; // compile step    
    return function.eval(input).as_double;
}

//-----------------------------------------------------------------------------

template <typename BuilderType>
struct Fixture
{
    BuilderType _builder;
    Fixture() : _builder() {}

    Tensor::UP createTensor(const DenseTensorCells &cells) {
        std::map<std::string, size_t> dimensionSizes;
        for (const auto &cell : cells) {
            for (const auto &addressElem : cell.first) {
                dimensionSizes[addressElem.first] = std::max(dimensionSizes[addressElem.first],
                        (addressElem.second + 1));
            }
        }
        std::map<std::string, typename BuilderType::Dimension> dimensionEnums;
        for (const auto &dimensionElem : dimensionSizes) {
            dimensionEnums[dimensionElem.first] =
                    _builder.defineDimension(dimensionElem.first, dimensionElem.second);
        }
        for (const auto &cell : cells) {
            for (const auto &addressElem : cell.first) {
                const auto &dimension = addressElem.first;
                size_t label = addressElem.second;
                _builder.addLabel(dimensionEnums[dimension], label);
            }
            _builder.addCell(cell.second);
        }
        return _builder.build();
    }
    void assertAddImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::add(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertAdd(const DenseTensorCells &exp,
                   const DenseTensorCells &lhs, const DenseTensorCells &rhs, bool check_types = true) {
        assertAddImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertSubtractImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::subtract(function::input(lhs.getType(), input.add(lhs)),
                                                  function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertSubtract(const DenseTensorCells &exp,
                        const DenseTensorCells &lhs,
                        const DenseTensorCells &rhs, bool check_types = true) {
        assertSubtractImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertMinImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::min(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMin(const DenseTensorCells &exp, const DenseTensorCells &lhs,
                   const DenseTensorCells &rhs, bool check_types = true) {
        assertMinImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertMaxImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::max(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMax(const DenseTensorCells &exp, const DenseTensorCells &lhs,
                   const DenseTensorCells &rhs, bool check_types = true) {
        assertMaxImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertSumImpl(double exp, const Tensor &tensor) {
        MyInput input;
        function::Node_UP ir = function::sum(function::input(tensor.getType(), input.add(tensor)));
        EXPECT_EQUAL(exp, eval_number(*ir, input));
    }
    void assertSum(double exp, const DenseTensorCells &cells) {
        assertSumImpl(exp, *createTensor(cells));
    }
    void assertMatchImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::match(function::input(lhs.getType(), input.add(lhs)),
                                               function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMatch(const DenseTensorCells &exp, const DenseTensorCells &lhs,
                     const DenseTensorCells &rhs, bool check_types = true) {
        assertMatchImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertApplyImpl(const Tensor &exp, const Tensor &tensor, const CellFunction &func) {
        MyInput input;
        function::Node_UP ir = function::apply(function::input(tensor.getType(), input.add(tensor)), input.add(func));
        EXPECT_EQUAL(exp, eval_tensor_checked(*ir, input));
    }
    void assertApply(const DenseTensorCells &exp, const DenseTensorCells &arg,
                     const CellFunction &func) {
        assertApplyImpl(*createTensor(exp), *createTensor(arg), func);
    }
    void assertDimensionSumImpl(const Tensor &exp, const Tensor &tensor, const vespalib::string &dimension, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::dimension_sum(function::input(tensor.getType(), input.add(tensor)), dimension);
        if (ir->type().is_error()) {
            // According to the ir, it is not allowed to sum over a
            // non-existing dimension.  The current implementation
            // allows this, resulting in a tensor with no cells and
            // with all dimensions not sliced.
            EXPECT_EQUAL(exp, eval_tensor_unchecked(*ir, input));
        } else {
            EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
        }
    }
    void assertDimensionSum(const DenseTensorCells &exp,
                            const DenseTensorCells &arg,
                            const vespalib::string &dimension, bool check_types = true) {
        assertDimensionSumImpl(*createTensor(exp), *createTensor(arg), dimension, check_types);
    }
    void assertMultiplyImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::multiply(function::input(lhs.getType(), input.add(lhs)),
                                                  function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMultiply(const DenseTensorCells &exp,
                        const DenseTensorCells &lhs, const DenseTensorCells &rhs, bool check_types = true) {
        assertMultiplyImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
};

using DenseFixture = Fixture<DenseTensorBuilder>;


template <typename FixtureType>
void
testTensorAdd(FixtureType &f)
{
    TEST_DO(f.assertAdd({},{},{}, false));
    TEST_DO(f.assertAdd({ {{{"x",0}}, 8} },
                        { {{{"x",0}}, 3} },
                        { {{{"x",0}}, 5} }));
    TEST_DO(f.assertAdd({ {{{"x",0}}, -2} },
                        { {{{"x",0}}, 3} },
                        { {{{"x",0}}, -5} }));
    TEST_DO(f.assertAdd({ {{{"x",0}}, 10}, {{{"x",1}}, 16} },
                        { {{{"x",0}}, 3}, {{{"x",1}}, 5} },
                        { {{{"x",0}}, 7}, {{{"x",1}}, 11} }));
    TEST_DO(f.assertAdd({ {{{"x",0},{"y",0}}, 8} },
                        { {{{"x",0},{"y",0}}, 3} },
                        { {{{"x",0},{"y",0}}, 5} }));
    TEST_DO(f.assertAdd({ {{{"x",0}}, 3} },
                        { {{{"x",0}}, 3} },
                        { {{{"x",1}}, 5} }));
}

template <typename FixtureType>
void
testTensorSubtract(FixtureType &f)
{
    TEST_DO(f.assertSubtract({},{},{}, false));
    TEST_DO(f.assertSubtract({ {{{"x",0}}, -2} },
                             { {{{"x",0}}, 3} },
                             { {{{"x",0}}, 5} }));
    TEST_DO(f.assertSubtract({ {{{"x",0}}, 8} },
                             { {{{"x",0}}, 3} },
                             { {{{"x",0}}, -5} }));
    TEST_DO(f.assertSubtract({ {{{"x",0}}, -4}, {{{"x",1}}, -6} },
                             { {{{"x",0}}, 3}, {{{"x",1}}, 5} },
                             { {{{"x",0}}, 7}, {{{"x",1}}, 11} }));
    TEST_DO(f.assertSubtract({ {{{"x",0},{"y",0}}, -2} },
                             { {{{"x",0},{"y",0}}, 3} },
                             { {{{"x",0},{"y",0}}, 5} }));
    TEST_DO(f.assertSubtract({ {{{"x",0}}, -5} },
                             { {{{"x",1}}, 3} },
                             { {{{"x",0}}, 5} }));
}

template <typename FixtureType>
void
testTensorMin(FixtureType &f)
{
    TEST_DO(f.assertMin({},{},{}, false));
    TEST_DO(f.assertMin({ {{{"x",0}}, 3} },
                        { {{{"x",0}}, 3} },
                        { {{{"x",0}}, 5} }));
    TEST_DO(f.assertMin({ {{{"x",0}}, -5} },
                        { {{{"x",0}}, 3} },
                        { {{{"x",0}}, -5} }));
    TEST_DO(f.assertMin({ {{{"x",0}}, 3}, {{{"x",1}}, 5} },
                        { {{{"x",0}}, 3}, {{{"x",1}}, 5} },
                        { {{{"x",0}}, 7}, {{{"x",1}}, 11} }));
    TEST_DO(f.assertMin({ {{{"x",0},{"y",0}}, 3} },
                        { {{{"x",0},{"y",0}}, 3} },
                        { {{{"x",0},{"y",0}}, 5} }));
    TEST_DO(f.assertMin({ {{{"x",0}}, 0} },
                        { {{{"x",1}}, 3} },
                        { {{{"x",0}}, 5} }));
}

template <typename FixtureType>
void
testTensorMax(FixtureType &f)
{
    f.assertMax({},{},{}, false);
    f.assertMax({ {{{"x",0}}, 5} },
                { {{{"x",0}}, 3} },
                { {{{"x",0}}, 5} });
    f.assertMax({ {{{"x",0}}, 3} },
                { {{{"x",0}}, 3} },
                { {{{"x",0}}, -5} });
    f.assertMax({ {{{"x",0}}, 7}, {{{"x",1}}, 11} },
                { {{{"x",0}}, 3}, {{{"x",1}}, 5} },
                { {{{"x",0}}, 7}, {{{"x",1}}, 11} });
    f.assertMax({ {{{"x",0},{"y",0}}, 5} },
                { {{{"x",0},{"y",0}}, 3} },
                { {{{"x",0},{"y",0}}, 5} });
}

template <typename FixtureType>
void
testTensorSum(FixtureType &f)
{
    TEST_DO(f.assertSum(0.0, {}));
    TEST_DO(f.assertSum(0.0, { {{{"x",0}}, 0} }));
    TEST_DO(f.assertSum(3.0, { {{{"x",0}}, 3} }));
    TEST_DO(f.assertSum(8.0, { {{{"x",0}}, 3}, {{{"x",1}}, 5} }));
    TEST_DO(f.assertSum(-2.0, { {{{"x",0}}, 3}, {{{"x",1}}, -5} }));
}

template <typename FixtureType>
void
testTensorMatch(FixtureType &f)
{
    f.assertMatch({}, {}, {}, false);
    f.assertMatch({ {{{"x",0}}, 15} },
                  { {{{"x",0}}, 3} },
                  { {{{"x",0}}, 5} });
    f.assertMatch({ {{{"x",0}}, 0} },
                  { {{{"x",0}}, 3} },
                  { {{{"x",0}}, 0} });
    f.assertMatch({ {{{"x",0}}, -15} },
                  { {{{"x",0}}, 3} },
                  { {{{"x",0}}, -5} });
    f.assertMatch({ {{{"x",0}, {"y",0}}, 39},
                    {{{"x",1}, {"y",0}}, 85},
                    {{{"x",0}, {"y",1}}, 133},
                    {{{"x",1}, {"y",1}}, 253} },
                  { {{{"x",0}, {"y",0}}, 3},
                    {{{"x",1}, {"y",0}}, 5},
                    {{{"x",0}, {"y",1}}, 7},
                    {{{"x",1}, {"y",1}}, 11} },
                  { {{{"x",0}, {"y",0}}, 13},
                    {{{"x",1}, {"y",0}}, 17},
                    {{{"x",0}, {"y",1}}, 19},
                    {{{"x",1}, {"y",1}}, 23} });
}

template <typename FixtureType>
void
testTensorMultiply(FixtureType &f)
{
    f.assertMultiply({}, {}, {}, false);
    f.assertMultiply({ {{{"x",0}}, 15} },
                     { {{{"x",0}}, 3} },
                     { {{{"x",0}}, 5} });
    f.assertMultiply({ {{{"x",0}}, 21},
                       {{{"x",1}}, 55} },
                     { {{{"x",0}}, 3},
                       {{{"x",1}}, 5} },
                     { {{{"x",0}}, 7},
                       {{{"x",1}}, 11} });
    f.assertMultiply({ {{{"x",0},{"y",0}}, 15} },
                     { {{{"x",0}}, 3} },
                     { {{{"y",0}}, 5} });
    f.assertMultiply({ {{{"x",0},{"y",0}}, 21},
                       {{{"x",0},{"y",1}}, 33},
                       {{{"x",1},{"y",0}}, 35},
                       {{{"x",1},{"y",1}}, 55} },
                     { {{{"x",0}}, 3},
                       {{{"x",1}}, 5} },
                     { {{{"y",0}}, 7},
                       {{{"y",1}}, 11} });
    f.assertMultiply({ {{{"x",0},{"y",0},{"z",0}}, 7},
                       {{{"x",0},{"y",0},{"z",1}}, 11},
                       {{{"x",0},{"y",1},{"z",0}}, 26},
                       {{{"x",0},{"y",1},{"z",1}}, 34},
                       {{{"x",1},{"y",0},{"z",0}}, 21},
                       {{{"x",1},{"y",0},{"z",1}}, 33},
                       {{{"x",1},{"y",1},{"z",0}}, 65},
                       {{{"x",1},{"y",1},{"z",1}}, 85} },
                     { {{{"x",0},{"y",0}}, 1},
                       {{{"x",0},{"y",1}}, 2},
                       {{{"x",1},{"y",0}}, 3},
                       {{{"x",1},{"y",1}}, 5} },
                     { {{{"y",0},{"z",0}}, 7},
                       {{{"y",0},{"z",1}}, 11},
                       {{{"y",1},{"z",0}}, 13},
                       {{{"y",1},{"z",1}}, 17} });
}

template <typename FixtureType>
void
testTensorMultiplePreservationOfDimensions(FixtureType &f)
{
    (void) f;
}

struct MyFunction : public CellFunction
{
    virtual double apply(double value) const override {
        return value + 5;
    }
};

template <typename FixtureType>
void
testTensorApply(FixtureType &f)
{
    f.assertApply({ {{{"x",0}}, 6}, {{{"x",1}}, 2} },
                  { {{{"x",0}}, 1}, {{{"x",1}}, -3} },
                  MyFunction());
}

template <typename FixtureType>
void
testTensorSumDimension(FixtureType &f)
{
    f.assertDimensionSum({ {{{"y",0}}, 4}, {{{"y",1}}, 12} },
                         { {{{"x",0},{"y",0}}, 1},
                           {{{"x",1},{"y",0}}, 3},
                           {{{"x",0},{"y",1}}, 5},
                           {{{"x",1},{"y",1}}, 7} }, "x");

    f.assertDimensionSum({ {{{"x",0}}, 6}, {{{"x",1}}, 10} },
                         { {{{"x",0},{"y",0}}, 1},
                           {{{"x",1},{"y",0}}, 3},
                           {{{"x",0},{"y",1}}, 5},
                           {{{"x",1},{"y",1}}, 7} }, "y");
    f.assertDimensionSum({ {{{"y",0}, {"z",0}}, 4},
                           {{{"y",1}, {"z",0}}, 12},
                           {{{"y",0}, {"z",1}}, 24},
                           {{{"y",1}, {"z",1}}, 36} },
                         { {{{"x",0},{"y",0}, {"z",0}}, 1},
                           {{{"x",1},{"y",0}, {"z",0}}, 3},
                           {{{"x",0},{"y",1}, {"z",0}}, 5},
                           {{{"x",1},{"y",1}, {"z",0}}, 7},
                           {{{"x",0},{"y",0}, {"z",1}}, 11},
                           {{{"x",1},{"y",0}, {"z",1}}, 13},
                           {{{"x",0},{"y",1}, {"z",1}}, 17},
                           {{{"x",1},{"y",1}, {"z",1}}, 19} }, "x");
    f.assertDimensionSum({ {{{"x",0}, {"z",0}}, 6},
                           {{{"x",1}, {"z",0}}, 10},
                           {{{"x",0}, {"z",1}}, 28},
                           {{{"x",1}, {"z",1}}, 32} },
                         { {{{"x",0},{"y",0}, {"z",0}}, 1},
                           {{{"x",1},{"y",0}, {"z",0}}, 3},
                           {{{"x",0},{"y",1}, {"z",0}}, 5},
                           {{{"x",1},{"y",1}, {"z",0}}, 7},
                           {{{"x",0},{"y",0}, {"z",1}}, 11},
                           {{{"x",1},{"y",0}, {"z",1}}, 13},
                           {{{"x",0},{"y",1}, {"z",1}}, 17},
                           {{{"x",1},{"y",1}, {"z",1}}, 19} }, "y");
    f.assertDimensionSum({ {{{"x",0}, {"y",0}}, 12},
                           {{{"x",1}, {"y",0}}, 16},
                           {{{"x",0}, {"y",1}}, 22},
                           {{{"x",1}, {"y",1}}, 26} },
                         { {{{"x",0},{"y",0}, {"z",0}}, 1},
                           {{{"x",1},{"y",0}, {"z",0}}, 3},
                           {{{"x",0},{"y",1}, {"z",0}}, 5},
                           {{{"x",1},{"y",1}, {"z",0}}, 7},
                           {{{"x",0},{"y",0}, {"z",1}}, 11},
                           {{{"x",1},{"y",0}, {"z",1}}, 13},
                           {{{"x",0},{"y",1}, {"z",1}}, 17},
                           {{{"x",1},{"y",1}, {"z",1}}, 19} }, "z");
    f.assertDimensionSum({ {{{"x",0}}, 3} },
                         { {{{"x",0}}, 3} },
                         "y");
    f.assertDimensionSum({ {{}, 3} },
                         { {{{"x",0}}, 3} },
                         "x", false);
}

template <typename FixtureType>
void
testAllTensorOperations(FixtureType &f)
{
    TEST_DO(testTensorAdd(f));
    TEST_DO(testTensorSubtract(f));
    TEST_DO(testTensorMin(f));
    TEST_DO(testTensorMax(f));
    TEST_DO(testTensorSum(f));
    TEST_DO(testTensorMatch(f));
    TEST_DO(testTensorMultiply(f));
    TEST_DO(testTensorMultiplePreservationOfDimensions(f));
    TEST_DO(testTensorApply(f));
    TEST_DO(testTensorSumDimension(f));
}

TEST_F("test tensor operations for DenseTensor", DenseFixture)
{
    testAllTensorOperations(f);
}

TEST_MAIN() { TEST_RUN_ALL(); }
