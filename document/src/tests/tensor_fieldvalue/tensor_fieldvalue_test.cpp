// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for tensor_fieldvalue.

#include <vespa/log/log.h>
LOG_SETUP("fieldvalue_test");

#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/eval/tensor/test/test_utils.h>

#include <vespa/vespalib/testkit/testapp.h>

using namespace document;
using namespace vespalib::tensor;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::test::makeTensor;

namespace
{

TensorDataType xSparseTensorDataType(ValueType::from_spec("tensor(x{})"));
TensorDataType xySparseTensorDataType(ValueType::from_spec("tensor(x{},y{})"));

Tensor::UP
createTensor(const TensorCells &cells, const TensorDimensions &dimensions) {
    vespalib::tensor::DefaultTensor::builder builder;
    return vespalib::tensor::TensorFactory::create(cells, dimensions, builder);
}

std::unique_ptr<Tensor>
makeSimpleTensor()
{
    return makeTensor<Tensor>(TensorSpec("tensor(x{},y{})").
                              add({{"x", "4"}, {"y", "5"}}, 7));
}

FieldValue::UP clone(FieldValue &fv) {
    auto ret = FieldValue::UP(fv.clone());
    EXPECT_NOT_EQUAL(ret.get(), &fv);
    EXPECT_EQUAL(*ret, fv);
    EXPECT_EQUAL(fv, *ret);
    return ret;
}

}

TEST("require that TensorFieldValue can be assigned tensors and cloned") {
    TensorFieldValue noTensorValue(xySparseTensorDataType);
    TensorFieldValue emptyTensorValue(xySparseTensorDataType);
    TensorFieldValue twoCellsTwoDimsValue(xySparseTensorDataType);
    emptyTensorValue = createTensor({}, {"x", "y"});
    twoCellsTwoDimsValue = createTensor({ {{{"y", "3"}}, 3},
                                             {{{"x", "4"}, {"y", "5"}}, 7} },
                                        {"x", "y"});
    EXPECT_NOT_EQUAL(noTensorValue, emptyTensorValue);
    EXPECT_NOT_EQUAL(noTensorValue, twoCellsTwoDimsValue);
    EXPECT_NOT_EQUAL(emptyTensorValue, noTensorValue);
    EXPECT_NOT_EQUAL(emptyTensorValue, twoCellsTwoDimsValue);
    EXPECT_NOT_EQUAL(twoCellsTwoDimsValue, noTensorValue);
    EXPECT_NOT_EQUAL(twoCellsTwoDimsValue, emptyTensorValue);
    FieldValue::UP noneClone = clone(noTensorValue);
    FieldValue::UP emptyClone = clone(emptyTensorValue);
    FieldValue::UP twoClone = clone(twoCellsTwoDimsValue);
    EXPECT_NOT_EQUAL(*noneClone, *emptyClone);
    EXPECT_NOT_EQUAL(*noneClone, *twoClone);
    EXPECT_NOT_EQUAL(*emptyClone, *noneClone);
    EXPECT_NOT_EQUAL(*emptyClone, *twoClone);
    EXPECT_NOT_EQUAL(*twoClone, *noneClone);
    EXPECT_NOT_EQUAL(*twoClone, *emptyClone);
    TensorFieldValue twoCellsTwoDimsValue2(xySparseTensorDataType);
    twoCellsTwoDimsValue2 =
        createTensor({ {{{"y", "3"}}, 3},
                          {{{"x", "4"}, {"y", "5"}}, 7} },
                     {"x", "y"});
    EXPECT_NOT_EQUAL(*noneClone, twoCellsTwoDimsValue2);
    EXPECT_NOT_EQUAL(*emptyClone, twoCellsTwoDimsValue2);
    EXPECT_EQUAL(*twoClone, twoCellsTwoDimsValue2);
}

TEST("require that TensorFieldValue::toString works")
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    EXPECT_EQUAL("{TensorFieldValue: null}", tensorFieldValue.toString());
    tensorFieldValue = createTensor({{{{"x","a"}}, 3}}, {"x"});
    EXPECT_EQUAL("{TensorFieldValue: {\"dimensions\":[\"x\"],\"cells\":[{\"address\":{\"x\":\"a\"},\"value\":3}]}}", tensorFieldValue.toString());
}

TEST("require that wrong tensor type for special case assign throws exception")
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    EXPECT_EXCEPTION(tensorFieldValue = makeSimpleTensor(),
                     document::WrongTensorTypeException,
                     "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but tensor type is 'tensor(x{},y{})'");
}

TEST("require that wrong tensor type for copy assign throws exception")
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    TensorFieldValue simpleTensorFieldValue(xySparseTensorDataType);
    simpleTensorFieldValue = makeSimpleTensor();
    EXPECT_EXCEPTION(tensorFieldValue = simpleTensorFieldValue,
                     document::WrongTensorTypeException,
                     "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but tensor type is 'tensor(x{},y{})'");
}

TEST("require that wrong tensor type for assignDeserialized throws exception")
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    EXPECT_EXCEPTION(tensorFieldValue.assignDeserialized(makeSimpleTensor()),
                     document::WrongTensorTypeException,
                     "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but tensor type is 'tensor(x{},y{})'");
}

TEST_MAIN() { TEST_RUN_ALL(); }
