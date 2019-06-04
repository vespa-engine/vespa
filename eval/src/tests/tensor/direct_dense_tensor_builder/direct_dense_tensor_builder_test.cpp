// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/direct_dense_tensor_builder.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib::tensor;
using vespalib::IllegalArgumentException;
using Builder = DirectDenseTensorBuilder;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::ConstArrayRef;

template <typename T> std::vector<T> make_vector(const ConstArrayRef<T> &ref) {
    std::vector<T> vec;
    for (const T &t: ref) {
        vec.push_back(t);
    }
    return vec;
}

void assertTensor(const vespalib::string &type_spec,
                  const DenseTensor::Cells &expCells,
                  const Tensor &tensor)
{
    const DenseTensor &realTensor = dynamic_cast<const DenseTensor &>(tensor);
    EXPECT_EQUAL(ValueType::from_spec(type_spec), realTensor.type());
    EXPECT_EQUAL(expCells, make_vector(realTensor.cellsRef()));
}

void assertTensorSpec(const TensorSpec &expSpec, const Tensor &tensor) {
    TensorSpec actSpec = tensor.toSpec();
    EXPECT_EQUAL(expSpec, actSpec);
}

Tensor::UP build1DTensor() {
    Builder builder(ValueType::from_spec("tensor(x[3])"));
    builder.insertCell(0, 10);
    builder.insertCell(1, 11);
    builder.insertCell(2, 12);
    return builder.build();
}

TEST("require that 1d tensor can be constructed") {
    assertTensor("tensor(x[3])", {10,11,12}, *build1DTensor());
}

TEST("require that 1d tensor can be converted to tensor spec") {
    assertTensorSpec(TensorSpec("tensor(x[3])").
                     add({{"x", 0}}, 10).
                     add({{"x", 1}}, 11).
                     add({{"x", 2}}, 12),
                     *build1DTensor());
}

Tensor::UP build2DTensor() {
    Builder builder(ValueType::from_spec("tensor(x[3],y[2])"));
    builder.insertCell({0, 0}, 10);
    builder.insertCell({0, 1}, 11);
    builder.insertCell({1, 0}, 12);
    builder.insertCell({1, 1}, 13);
    builder.insertCell({2, 0}, 14);
    builder.insertCell({2, 1}, 15);
    return builder.build();
}

TEST("require that 2d tensor can be constructed") {
    assertTensor("tensor(x[3],y[2])", {10,11,12,13,14,15}, *build2DTensor());
}

TEST("require that 2d tensor can be converted to tensor spec") {
    assertTensorSpec(TensorSpec("tensor(x[3],y[2])").
                     add({{"x", 0},{"y", 0}}, 10).
                     add({{"x", 0},{"y", 1}}, 11).
                     add({{"x", 1},{"y", 0}}, 12).
                     add({{"x", 1},{"y", 1}}, 13).
                     add({{"x", 2},{"y", 0}}, 14).
                     add({{"x", 2},{"y", 1}}, 15),
                     *build2DTensor());
}

TEST("require that 3d tensor can be constructed") {
    Builder builder(ValueType::from_spec("tensor(x[3],y[2],z[2])"));
    builder.insertCell({0, 0, 0}, 10);
    builder.insertCell({0, 0, 1}, 11);
    builder.insertCell({0, 1, 0}, 12);
    builder.insertCell({0, 1, 1}, 13);
    builder.insertCell({1, 0, 0}, 14);
    builder.insertCell({1, 0, 1}, 15);
    builder.insertCell({1, 1, 0}, 16);
    builder.insertCell({1, 1, 1}, 17);
    builder.insertCell({2, 0, 0}, 18);
    builder.insertCell({2, 0, 1}, 19);
    builder.insertCell({2, 1, 0}, 20);
    builder.insertCell({2, 1, 1}, 21);
    assertTensor("tensor(x[3],y[2],z[2])",
                 {10,11,12,13,14,15,16,17,18,19,20,21},
                 *builder.build());
}

TEST("require that cells get default value 0 if not specified") {
    Builder builder(ValueType::from_spec("tensor(x[3])"));
    builder.insertCell(1, 11);
    assertTensor("tensor(x[3])", {0,11,0},
                 *builder.build());
}

void assertTensorCell(const DenseTensor::Address &expAddress,
                      double expCell,
                      const DenseTensor::CellsIterator &itr)
{
    EXPECT_TRUE(itr.valid());
    EXPECT_EQUAL(expAddress, itr.address());
    EXPECT_EQUAL(expCell, itr.cell());
}

TEST("require that dense tensor cells iterator works for 1d tensor") {
    Tensor::UP tensor;
    {
        Builder builder(ValueType::from_spec("tensor(x[2])"));
        builder.insertCell(0, 2);
        builder.insertCell(1, 3);
        tensor = builder.build();
    }

    const DenseTensor &denseTensor = dynamic_cast<const DenseTensor &>(*tensor);
    DenseTensor::CellsIterator itr = denseTensor.cellsIterator();

    assertTensorCell({0}, 2, itr);
    itr.next();
    assertTensorCell({1}, 3, itr);
    itr.next();
    EXPECT_FALSE(itr.valid());
}

TEST("require that dense tensor cells iterator works for 2d tensor") {
    Tensor::UP tensor;
    {
        Builder builder(ValueType::from_spec("tensor(x[2],y[2])"));
        builder.insertCell({0, 0}, 2);
        builder.insertCell({0, 1}, 3);
        builder.insertCell({1, 0}, 5);
        builder.insertCell({1, 1}, 7);
        tensor = builder.build();
    }

    const DenseTensor &denseTensor = dynamic_cast<const DenseTensor &>(*tensor);
    DenseTensor::CellsIterator itr = denseTensor.cellsIterator();

    assertTensorCell({0,0}, 2, itr);
    itr.next();
    assertTensorCell({0,1}, 3, itr);
    itr.next();
    assertTensorCell({1,0}, 5, itr);
    itr.next();
    assertTensorCell({1,1}, 7, itr);
    itr.next();
    EXPECT_FALSE(itr.valid());
}

TEST_MAIN() { TEST_RUN_ALL(); }
