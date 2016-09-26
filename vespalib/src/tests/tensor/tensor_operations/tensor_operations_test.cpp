// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/sparse/sparse_tensor.h>
#include <vespa/vespalib/tensor/sparse/sparse_tensor_builder.h>
#include <vespa/vespalib/tensor/types.h>
#include <vespa/vespalib/tensor/tensor_factory.h>
#include <vespa/vespalib/tensor/tensor_function.h>
#include <vespa/vespalib/tensor/tensor_visitor.h>
#include <iostream>

using namespace vespalib::tensor;

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

    Tensor::UP createTensor(const TensorCells &cells) {
        return TensorFactory::create(cells, _builder);
    }
    Tensor::UP createTensor(const TensorCells &cells, const TensorDimensions &dimensions) {
        return TensorFactory::create(cells, dimensions, _builder);
    }
    void assertEquals(const TensorCells &lhs,
                      const TensorDimensions &lhsDimensions,
                      const TensorCells &rhs,
                      const TensorDimensions &rhsDimensions) {
        EXPECT_EQUAL(*createTensor(lhs, lhsDimensions),
                     *createTensor(rhs, rhsDimensions));
    }
    void assertEquals(const TensorCells &lhs, const TensorCells &rhs) {
        EXPECT_EQUAL(*createTensor(lhs), *createTensor(rhs));
    }
    void assertNotEquals(const TensorCells &lhs, const TensorCells &rhs) {
        EXPECT_NOT_EQUAL(*createTensor(lhs), *createTensor(rhs));
    }
    void assertNotEquals(const TensorCells &lhs,
                         const TensorDimensions &lhsDimensions,
                         const TensorCells &rhs,
                         const TensorDimensions &rhsDimensions) {
        EXPECT_NOT_EQUAL(*createTensor(lhs, lhsDimensions),
                         *createTensor(rhs, rhsDimensions));
    }
    void assertAddImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::add(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertAdd(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs, bool check_types = true) {
        assertAddImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertSubtractImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::subtract(function::input(lhs.getType(), input.add(lhs)),
                                                  function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertSubtract(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs, bool check_types = true) {
        assertSubtractImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertMinImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::min(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMin(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs, bool check_types = true) {
        assertMinImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertMaxImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::max(function::input(lhs.getType(), input.add(lhs)),
                                             function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMax(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs, bool check_types = true) {
        assertMaxImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertSumImpl(double exp, const Tensor &tensor) {
        MyInput input;
        function::Node_UP ir = function::sum(function::input(tensor.getType(), input.add(tensor)));
        EXPECT_EQUAL(exp, eval_number(*ir, input));
    }
    void assertSum(double exp, const TensorCells &cells) {
        assertSumImpl(exp, *createTensor(cells));
    }
    void assertMatchImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs) {
        MyInput input;
        function::Node_UP ir = function::match(function::input(lhs.getType(), input.add(lhs)),
                                               function::input(rhs.getType(), input.add(rhs)));
        // The match operation currently ends up the union of input
        // dimensions. It should be the intersection of input
        // dimensions as claimed by the intermediate
        // representation. The tensor result type checking is disabled
        // until the corresponding bug is fixed.
        EXPECT_EQUAL(exp, eval_tensor_unchecked(*ir, input)); // UNCHECKED (ref VESPA-1868)
    }
    void assertMatch(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs) {
        assertMatchImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs));
    }
    void assertMatch(const TensorCells &expTensor, const TensorDimensions &expDimensions,
                     const TensorCells &lhs, const TensorCells &rhs) {
        assertMatchImpl(*createTensor(expTensor, expDimensions), *createTensor(lhs), *createTensor(rhs));
    }
    void assertMultiplyImpl(const Tensor &exp, const Tensor &lhs, const Tensor &rhs, bool check_types) {
        MyInput input;
        function::Node_UP ir = function::multiply(function::input(lhs.getType(), input.add(lhs)),
                                                  function::input(rhs.getType(), input.add(rhs)));
        EXPECT_EQUAL(exp, eval_tensor(*ir, input, check_types));
    }
    void assertMultiply(const TensorCells &exp, const TensorCells &lhs, const TensorCells &rhs, bool check_types = true) {
        assertMultiplyImpl(*createTensor(exp), *createTensor(lhs), *createTensor(rhs), check_types);
    }
    void assertMultiply(const TensorCells &expTensor, const TensorDimensions &expDimensions,
                        const TensorCells &lhs, const TensorCells &rhs) {
        assertMultiplyImpl(*createTensor(expTensor, expDimensions), *createTensor(lhs), *createTensor(rhs), true);
    }
    void assertMultiplyImpl(const Tensor &exp, const Tensor &arg1, const Tensor &arg2, const Tensor &arg3) {
        MyInput input;
        function::Node_UP ir = function::multiply(
                function::multiply(function::input(arg1.getType(), input.add(arg1)),
                                   function::input(arg2.getType(), input.add(arg2))),
                function::input(arg3.getType(), input.add(arg3)));
        EXPECT_EQUAL(exp, eval_tensor_checked(*ir, input));
    }
    void assertMultiply(const TensorCells &expTensor, const TensorDimensions &expDimensions,
                        const TensorCells &arg1, const TensorCells &arg2, const TensorCells &arg3) {
        assertMultiplyImpl(*createTensor(expTensor, expDimensions), *createTensor(arg1), *createTensor(arg2), *createTensor(arg3));
    }
    void assertApplyImpl(const Tensor &exp, const Tensor &tensor, const CellFunction &func) {
        MyInput input;
        function::Node_UP ir = function::apply(function::input(tensor.getType(), input.add(tensor)), input.add(func));
        EXPECT_EQUAL(exp, eval_tensor_checked(*ir, input));
    }
    void assertApply(const TensorCells &exp, const TensorCells &arg, const CellFunction &func) {
        assertApplyImpl(*createTensor(exp), *createTensor(arg), func);
    }
    void assertDimensionSumImpl(const Tensor &exp, const Tensor &tensor, const vespalib::string &dimension) {
        MyInput input;
        function::Node_UP ir = function::dimension_sum(function::input(tensor.getType(), input.add(tensor)), dimension);
        EXPECT_EQUAL(exp, eval_tensor_checked(*ir, input));
    }
    void assertDimensionSum(const TensorCells &exp, const TensorCells &arg,
                            const vespalib::string &dimension) {
        assertDimensionSumImpl(*createTensor(exp), *createTensor(arg), dimension);
    }
};

using SparseFixture = Fixture<SparseTensorBuilder>;


template <typename FixtureType>
void
testTensorEquals(FixtureType &f)
{
    TEST_DO(f.assertEquals({}, {}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {}));
    TEST_DO(f.assertNotEquals({}, { {{{"x","1"}}, 3} }));
    TEST_DO(f.assertEquals({ {{{"x","1"}}, 3} }, { {{{"x","1"}}, 3} }));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, { {{{"x","1"}}, 4} }));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, { {{{"x","2"}}, 3} }));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, { {{{"y","1"}}, 3} }));
    TEST_DO(f.assertEquals({ {{{"x","1"}}, 3} }, {"x"},
                           { {{{"x","1"}}, 3} }, {"x"}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {"x"},
                              { {{{"x","1"}}, 4} }, {"x"}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {"x"},
                              { {{{"x","2"}}, 3} }, {"x"}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {"x"},
                              { {{{"x","2"}}, 3} }, {"x"}));
    TEST_DO(f.assertEquals({ {{{"x","1"}}, 3} }, {"x", "y"},
                           { {{{"x","1"}}, 3} }, {"x", "y"}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {"x", "y"},
                              { {{{"x","1"}}, 3} }, {"x", "z"}));
    TEST_DO(f.assertNotEquals({ {{{"x","1"}}, 3} }, {"x", "y"},
                              { {{{"y","1"}}, 3} }, {"y", "z"}));
}

template <typename FixtureType>
void
testTensorAdd(FixtureType &f)
{
    f.assertAdd({},{},{}, false);
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"x","2"}}, 5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, 5} });
    f.assertAdd({ {{{"x","1"}}, 8} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, 5} });
    f.assertAdd({ {{{"x","1"}}, -2} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, -5} });
    f.assertAdd({ {{{"x","1"}}, 0} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, -3} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"y","2"}}, 12}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"y","2"}}, 12}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertAdd({ {{{"y","2"}}, 12}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertAdd({ {{{"y","2"}}, 12}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"y","2"}}, 12} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"y","2"}}, 12} },
                { {{{"y","2"}}, 7} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} },
                { {{{"z","3"}}, 11} });
    f.assertAdd({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} });
}

template <typename FixtureType>
void
testTensorSubtract(FixtureType &f)
{
    f.assertSubtract({},{},{}, false);
    f.assertSubtract({ {{{"x","1"}}, 3}, {{{"x","2"}}, -5} },
                     { {{{"x","1"}}, 3} },
                     { {{{"x","2"}}, 5} });
    f.assertSubtract({ {{{"x","1"}}, -2} },
                     { {{{"x","1"}}, 3} },
                     { {{{"x","1"}}, 5} });
    f.assertSubtract({ {{{"x","1"}}, 8} },
                     { {{{"x","1"}}, 3} },
                     { {{{"x","1"}}, -5} });
    f.assertSubtract({ {{{"x","1"}}, 0} },
                     { {{{"x","1"}}, 3} },
                     { {{{"x","1"}}, 3} });
    f.assertSubtract({ {{{"x","1"}}, 3}, {{{"y","2"}},-2}, {{{"z","3"}},-11} },
                     { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                     { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertSubtract({ {{{"x","1"}},-3}, {{{"y","2"}}, 2}, {{{"z","3"}}, 11} },
                     { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                     { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertSubtract({ {{{"y","2"}},-2}, {{{"z","3"}},-11} },
                     { {{{"y","2"}}, 5} },
                     { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertSubtract({ {{{"y","2"}}, 2}, {{{"z","3"}}, 11} },
                     { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                     { {{{"y","2"}}, 5} });
    f.assertSubtract({ {{{"x","1"}}, 3}, {{{"y","2"}},-2} },
                     { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                     { {{{"y","2"}}, 7} });
    f.assertSubtract({ {{{"x","1"}},-3}, {{{"y","2"}}, 2} },
                     { {{{"y","2"}}, 7} },
                     { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertSubtract({ {{{"x","1"}}, 3}, {{{"z","3"}},-11} },
                     { {{{"x","1"}}, 3} },
                     { {{{"z","3"}}, 11} });
    f.assertSubtract({ {{{"x","1"}},-3}, {{{"z","3"}}, 11} },
                     { {{{"z","3"}}, 11} },
                     { {{{"x","1"}}, 3} });
}

template <typename FixtureType>
void
testTensorMin(FixtureType &f)
{
    f.assertMin({},{},{}, false);
    f.assertMin({ {{{"x","1"}}, 3}, {{{"x","2"}}, 5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, 5} });
    f.assertMin({ {{{"x","1"}}, 3} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, 5} });
    f.assertMin({ {{{"x","1"}}, -5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, -5} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"x","2"}}, 0} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, 0} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"y","2"}}, 5}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"y","2"}}, 5}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertMin({ {{{"y","2"}}, 5}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertMin({ {{{"y","2"}}, 5}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} },
                { {{{"z","3"}}, 11} });
    f.assertMin({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} });
}

template <typename FixtureType>
void
testTensorMax(FixtureType &f)
{
    f.assertMax({},{},{}, false);
    f.assertMax({ {{{"x","1"}}, 3}, {{{"x","2"}}, 5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, 5} });
    f.assertMax({ {{{"x","1"}}, 5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, 5} });
    f.assertMax({ {{{"x","1"}}, 3} },
                { {{{"x","1"}}, 3} },
                { {{{"x","1"}}, -5} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"x","2"}}, 0} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, 0} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"x","2"}}, -5} },
                { {{{"x","1"}}, 3} },
                { {{{"x","2"}}, -5} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertMax({ {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} });
    f.assertMax({ {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                { {{{"y","2"}}, 5} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"y","2"}}, 7} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                { {{{"y","2"}}, 7} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"y","2"}}, 7} },
                { {{{"y","2"}}, 7} },
                { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} },
                { {{{"z","3"}}, 11} });
    f.assertMax({ {{{"x","1"}}, 3}, {{{"z","3"}}, 11} },
                { {{{"z","3"}}, 11} },
                { {{{"x","1"}}, 3} });
}

template <typename FixtureType>
void
testTensorSum(FixtureType &f)
{
    f.assertSum(0.0, {});
    f.assertSum(0.0, { {{{"x","1"}}, 0} });
    f.assertSum(3.0, { {{{"x","1"}}, 3} });
    f.assertSum(8.0, { {{{"x","1"}}, 3}, {{{"x","2"}}, 5} });
    f.assertSum(-2.0, { {{{"x","1"}}, 3}, {{{"x","2"}}, -5} });
}

template <typename FixtureType>
void
testTensorMatch(FixtureType &f)
{
    TEST_DO(f.assertMatch({}, {}, {}));
    TEST_DO(f.assertMatch({}, {"x"},
                          { {{{"x","1"}}, 3} },
                          { {{{"x","2"}}, 5} }));
    TEST_DO(f.assertMatch({ {{{"x","1"}}, 15} },
                          { {{{"x","1"}}, 3} },
                          { {{{"x","1"}}, 5} }));
    TEST_DO(f.assertMatch({ {{{"x","1"}}, 0} },
                          { {{{"x","1"}}, 3} },
                          { {{{"x","1"}}, 0} }));
    TEST_DO(f.assertMatch({ {{{"x","1"}}, -15} },
                          { {{{"x","1"}}, 3} },
                          { {{{"x","1"}}, -5} }));
    TEST_DO(f.assertMatch({ {{{"x","1"}}, 15},
                            {{{"x","1"}, {"y","1"}}, 7} }, {"x","y","z"},
                          { {{{"x","1"}}, 3},
                            {{{"x","2"}}, 3},
                            {{{"x","1"},{"y","1"}}, 1},
                            {{{"x","1"},{"y","2"}}, 6} },
                          { {{{"x","1"}}, 5},
                            {{{"x","1"},{"y","1"}}, 7},
                            {{{"x","1"},{"y","1"},{"z","1"}}, 6} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"x", "y", "z"},
                          { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                          { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"x", "y", "z"},
                          { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                          { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"y", "z"},
                          { {{{"y","2"}}, 5} },
                          { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"y", "z"},
                          { {{{"y","2"}}, 7}, {{{"z","3"}}, 11} },
                          { {{{"y","2"}}, 5} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"x", "y"},
                          { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} },
                          { {{{"y","2"}}, 7} }));
    TEST_DO(f.assertMatch({ {{{"y","2"}}, 35} }, {"x", "y"},
                          { {{{"y","2"}}, 7} },
                          { {{{"x","1"}}, 3}, {{{"y","2"}}, 5} }));
    TEST_DO(f.assertMatch({ }, {"x", "z"},
                          { {{{"x","1"}}, 3} },
                          { {{{"z","3"}}, 11} }));
    TEST_DO(f.assertMatch({ }, {"x", "z"},
                          { {{{"z","3"}}, 11} },
                          { {{{"x","1"}}, 3} }));
}

template <typename FixtureType>
void
testTensorMultiply(FixtureType &f)
{
    f.assertMultiply({}, {}, {}, false);
    f.assertMultiply({}, {"x"},
                     { {{{"x","1"}}, 3} },
                     { {{{"x","2"}}, 5} });
    f.assertMultiply({ {{{"x","1"}}, 15} },
                     { {{{"x","1"}}, 3} },
                     { {{{"x","1"}}, 5} });
    f.assertMultiply({ {{{"x","1"},{"y","1"}}, 15} },
                     { {{{"x","1"}}, 3} },
                     { {{{"y","1"}}, 5} });
    f.assertMultiply({ {{{"x","1"},{"y","1"}}, 15}, {{{"x","2"},{"y","1"}}, 35} },
                     { {{{"x","1"}}, 3}, {{{"x","2"}}, 7} },
                     { {{{"y","1"}}, 5} });
    f.assertMultiply({ {{{"x","1"},{"y","1"},{"z","1"}}, 7},
                       {{{"x","1"},{"y","1"},{"z","2"}}, 13},
                       {{{"x","2"},{"y","1"},{"z","1"}}, 21},
                       {{{"x","2"},{"y","1"},{"z","2"}}, 39},
                       {{{"x","1"},{"y","2"},{"z","1"}}, 55} },
                     { {{{"x","1"},{"y","1"}}, 1},
                       {{{"x","2"},{"y","1"}}, 3},
                       {{{"x","1"},{"y","2"}}, 5} },
                     { {{{"y","1"},{"z","1"}}, 7},
                       {{{"y","2"},{"z","1"}}, 11},
                       {{{"y","1"},{"z","2"}}, 13} });
    f.assertMultiply({ {{{"x","1"},{"y","1"},{"z","1"}}, 7} },
                     { {{{"x","1"}}, 5}, {{{"x","1"},{"y","1"}}, 1} },
                     { {{{"y","1"},{"z","1"}}, 7} });
    f.assertMultiply({ {{{"x","1"},{"y","1"},{"z","1"}}, 7}, {{{"x","1"},{"z","1"}}, 55} },
                     { {{{"x","1"}}, 5}, {{{"x","1"},{"y","1"}}, 1} },
                     { {{{"z","1"}}, 11}, {{{"y","1"},{"z","1"}}, 7} });
    f.assertMultiply({ {{{"x","1"},{"y","1"},{"z","1"}}, 7} },
                     { {{}, 5}, {{{"x","1"},{"y","1"}}, 1} },
                     { {{{"y","1"},{"z","1"}}, 7} });
    f.assertMultiply({ {{{"x","1"},{"y","1"},{"z","1"}}, 7}, {{}, 55} },
                     { {{}, 5}, {{{"x","1"},{"y","1"}}, 1} },
                     { {{}, 11}, {{{"y","1"},{"z","1"}}, 7} });
}

template <typename FixtureType>
void
testTensorMultiplePreservationOfDimensions(FixtureType &f)
{
    f.assertMultiply({}, {"x"},
                     { {{{"x","1"}}, 1} },
                     { {{{"x","2"}}, 1} });
    f.assertMultiply({ {{{"x","1"}}, 1} }, {"x","y"},
                     { {{{"x","1"}}, 1} },
                     { {{{"x","2"},{"y","1"}}, 1}, {{{"x","1"}}, 1} });
    f.assertMultiply({}, {"x","y"},
                     { {{{"x","1"}}, 1} },
                     { {{{"x","2"},{"y","1"}}, 1}, {{{"x","1"}}, 1} },
                     { {{{"x","1"},{"y","1"}}, 1} });
    f.assertMultiply({ {{{"x","1"},{"y","1"}}, 1} }, {"x","y"},
                     { {{{"x","1"}}, 1} },
                     { {{{"x","1"},{"y","1"}}, 1} });
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
    f.assertApply({ {{{"x","1"}}, 6}, {{{"y","1"}}, 2} },
                  { {{{"x","1"}}, 1}, {{{"y","1"}}, -3} },
                  MyFunction());
}

template <typename FixtureType>
void
testTensorSumDimension(FixtureType &f)
{
    f.assertDimensionSum({ {{{"y","1"}}, 4}, {{{"y","2"}}, 12} },
                         { {{{"x","1"},{"y","1"}}, 1},
                           {{{"x","2"},{"y","1"}}, 3},
                           {{{"x","1"},{"y","2"}}, 5},
                           {{{"x","2"},{"y","2"}}, 7} }, "x");
    f.assertDimensionSum({ {{{"x","1"}}, 6}, {{{"x","2"}}, 10} },
                         { {{{"x","1"},{"y","1"}}, 1},
                           {{{"x","2"},{"y","1"}}, 3},
                           {{{"x","1"},{"y","2"}}, 5},
                           {{{"x","2"},{"y","2"}}, 7} }, "y");
    f.assertDimensionSum({ {{}, 13}, {{{"x","1"}}, 17}, {{{"x","2"}}, 10} },
                         { {{{"x","1"},{"y","1"}}, 1},
                           {{{"x","2"},{"y","1"}}, 3},
                           {{{"x","1"},{"y","2"}}, 5},
                           {{{"x","2"},{"y","2"}}, 7},
                           {{{"x","1"}}, 11},
                           {{{"y","2"}}, 13} }, "y");
    f.assertDimensionSum({ {{}, 11}, {{{"y","1"}}, 4}, {{{"y","2"}}, 25}, {{{"z","1"}}, 19} },
                         { {{{"x","1"},{"y","1"}}, 1},
                           {{{"x","2"},{"y","1"}}, 3},
                           {{{"x","1"},{"y","2"}}, 5},
                           {{{"x","2"},{"y","2"}}, 7},
                           {{{"x","1"}}, 11},
                           {{{"y","2"}}, 13},
                           {{{"z","1"}}, 19}, }, "x");
}

template <typename FixtureType>
void
testAllTensorOperations(FixtureType &f)
{
    TEST_DO(testTensorEquals(f));
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

TEST_F("test tensor operations for SparseTensor", SparseFixture)
{
    testAllTensorOperations(f);
}

TEST_MAIN() { TEST_RUN_ALL(); }
