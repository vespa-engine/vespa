// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for tensor_fieldvalue.

#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("fieldvalue_test");

using namespace document;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;

namespace
{

TensorDataType xSparseTensorDataType(ValueType::from_spec("tensor(x{})"));
TensorDataType xySparseTensorDataType(ValueType::from_spec("tensor(x{},y{})"));

vespalib::eval::Value::UP createTensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

std::unique_ptr<vespalib::eval::Value>
makeSimpleTensor()
{
    return SimpleValue::from_spec(TensorSpec("tensor(x{},y{})").
                                  add({{"x", "4"}, {"y", "5"}}, 7));
}

FieldValue::UP clone(FieldValue &fv) {
    auto ret = FieldValue::UP(fv.clone());
    EXPECT_NE(ret.get(), &fv);
    EXPECT_EQ(*ret, fv);
    EXPECT_EQ(fv, *ret);
    return ret;
}

}

TEST(TensorFieldValueTest, require_that_TensorFieldValue_can_be_assigned_tensors_and_cloned)
{
    TensorFieldValue noTensorValue(xySparseTensorDataType);
    TensorFieldValue emptyTensorValue(xySparseTensorDataType);
    TensorFieldValue twoCellsTwoDimsValue(xySparseTensorDataType);
    emptyTensorValue = createTensor(TensorSpec("tensor(x{},y{})"));
    twoCellsTwoDimsValue = createTensor(TensorSpec("tensor(x{},y{})")
                                        .add({{"x", ""}, {"y", "3"}}, 3)
                                        .add({{"x", "4"}, {"y", "5"}}, 7));
    EXPECT_NE(noTensorValue, emptyTensorValue);
    EXPECT_NE(noTensorValue, twoCellsTwoDimsValue);
    EXPECT_NE(emptyTensorValue, noTensorValue);
    EXPECT_NE(emptyTensorValue, twoCellsTwoDimsValue);
    EXPECT_NE(twoCellsTwoDimsValue, noTensorValue);
    EXPECT_NE(twoCellsTwoDimsValue, emptyTensorValue);
    FieldValue::UP noneClone = clone(noTensorValue);
    FieldValue::UP emptyClone = clone(emptyTensorValue);
    FieldValue::UP twoClone = clone(twoCellsTwoDimsValue);
    EXPECT_NE(*noneClone, *emptyClone);
    EXPECT_NE(*noneClone, *twoClone);
    EXPECT_NE(*emptyClone, *noneClone);
    EXPECT_NE(*emptyClone, *twoClone);
    EXPECT_NE(*twoClone, *noneClone);
    EXPECT_NE(*twoClone, *emptyClone);
    TensorFieldValue twoCellsTwoDimsValue2(xySparseTensorDataType);
    twoCellsTwoDimsValue2 = createTensor(TensorSpec("tensor(x{},y{})")
                                         .add({{"x", ""}, {"y", "3"}}, 3)
                                         .add({{"x", "4"}, {"y", "5"}}, 7));
    EXPECT_NE(*noneClone, twoCellsTwoDimsValue2);
    EXPECT_NE(*emptyClone, twoCellsTwoDimsValue2);
    EXPECT_EQ(*twoClone, twoCellsTwoDimsValue2);
}

TEST(TensorFieldValueTest, require_that_TensorFieldValue_toString_works)
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    EXPECT_EQ("{TensorFieldValue: null}", tensorFieldValue.toString());
    tensorFieldValue = createTensor(TensorSpec("tensor(x{})").add({{"x", "a"}}, 3));
    EXPECT_EQ("{TensorFieldValue: spec(tensor(x{})) {\n  [a]: 3\n}}", tensorFieldValue.toString());
}

TEST(TensorFieldValueTest, require_that_wrong_tensor_type_for_special_case_assign_throws_exception)
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    VESPA_EXPECT_EXCEPTION(tensorFieldValue = makeSimpleTensor(),
                           document::WrongTensorTypeException,
                           "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but other tensor type is 'tensor(x{},y{})'");
}

TEST(TensorFieldValueTest, require_that_wrong_tensor_type_for_copy_assign_throws_exception)
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    TensorFieldValue simpleTensorFieldValue(xySparseTensorDataType);
    simpleTensorFieldValue = makeSimpleTensor();
    VESPA_EXPECT_EXCEPTION(tensorFieldValue = simpleTensorFieldValue,
                           document::WrongTensorTypeException,
                           "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but other tensor type is 'tensor(x{},y{})'");
}

TEST(TensorFieldValueTest, require_that_wrong_tensor_type_for_assignDeserialized_throws_exception)
{
    TensorFieldValue tensorFieldValue(xSparseTensorDataType);
    VESPA_EXPECT_EXCEPTION(tensorFieldValue.assignDeserialized(makeSimpleTensor()),
                           document::WrongTensorTypeException,
                           "WrongTensorTypeException: Field tensor type is 'tensor(x{})' but other tensor type is 'tensor(x{},y{})'");
}

GTEST_MAIN_RUN_ALL_TESTS()
