// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib::tensor;
using vespalib::IllegalArgumentException;
using Builder = DenseTensorBuilder;
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

void
assertTensor(const std::vector<ValueType::Dimension> &expDims,
             const DenseTensor::Cells &expCells,
             const Tensor &tensor)
{
    const DenseTensor &realTensor = dynamic_cast<const DenseTensor &>(tensor);
    EXPECT_EQUAL(ValueType::tensor_type(expDims), realTensor.type());
    EXPECT_EQUAL(expCells, make_vector(realTensor.cellsRef()));
}

void
assertTensorSpec(const TensorSpec &expSpec, const Tensor &tensor)
{
    TensorSpec actSpec = tensor.toSpec();
    EXPECT_EQUAL(expSpec, actSpec);
}

struct Fixture
{
    Builder builder;
};

Tensor::UP
build1DTensor(Builder &builder)
{
    Builder::Dimension dimX = builder.defineDimension("x", 3);
    builder.addLabel(dimX, 0).addCell(10).
            addLabel(dimX, 1).addCell(11).
            addLabel(dimX, 2).addCell(12);
    return builder.build();
}

TEST_F("require that 1d tensor can be constructed", Fixture)
{
    assertTensor({{"x",3}}, {10,11,12}, *build1DTensor(f.builder));
}

TEST_F("require that 1d tensor can be converted to tensor spec", Fixture)
{
    assertTensorSpec(TensorSpec("tensor(x[3])").
            add({{"x", 0}}, 10).
            add({{"x", 1}}, 11).
            add({{"x", 2}}, 12),
                     *build1DTensor(f.builder));
}

Tensor::UP
build2DTensor(Builder &builder)
{
    Builder::Dimension dimX = builder.defineDimension("x", 3);
    Builder::Dimension dimY = builder.defineDimension("y", 2);
    builder.addLabel(dimX, 0).addLabel(dimY, 0).addCell(10).
            addLabel(dimX, 0).addLabel(dimY, 1).addCell(11).
            addLabel(dimX, 1).addLabel(dimY, 0).addCell(12).
            addLabel(dimX, 1).addLabel(dimY, 1).addCell(13).
            addLabel(dimX, 2).addLabel(dimY, 0).addCell(14).
            addLabel(dimX, 2).addLabel(dimY, 1).addCell(15);
    return builder.build();
}

TEST_F("require that 2d tensor can be constructed", Fixture)
{
    assertTensor({{"x",3},{"y",2}}, {10,11,12,13,14,15}, *build2DTensor(f.builder));
}

TEST_F("require that 2d tensor can be converted to tensor spec", Fixture)
{
    assertTensorSpec(TensorSpec("tensor(x[3],y[2])").
            add({{"x", 0},{"y", 0}}, 10).
            add({{"x", 0},{"y", 1}}, 11).
            add({{"x", 1},{"y", 0}}, 12).
            add({{"x", 1},{"y", 1}}, 13).
            add({{"x", 2},{"y", 0}}, 14).
            add({{"x", 2},{"y", 1}}, 15),
                     *build2DTensor(f.builder));
}

TEST_F("require that 3d tensor can be constructed", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 3);
    Builder::Dimension dimY = f.builder.defineDimension("y", 2);
    Builder::Dimension dimZ = f.builder.defineDimension("z", 2);
    f.builder.addLabel(dimX, 0).addLabel(dimY, 0).addLabel(dimZ, 0).addCell(10).
              addLabel(dimX, 0).addLabel(dimY, 0).addLabel(dimZ, 1).addCell(11).
              addLabel(dimX, 0).addLabel(dimY, 1).addLabel(dimZ, 0).addCell(12).
              addLabel(dimX, 0).addLabel(dimY, 1).addLabel(dimZ, 1).addCell(13).
              addLabel(dimX, 1).addLabel(dimY, 0).addLabel(dimZ, 0).addCell(14).
              addLabel(dimX, 1).addLabel(dimY, 0).addLabel(dimZ, 1).addCell(15).
              addLabel(dimX, 1).addLabel(dimY, 1).addLabel(dimZ, 0).addCell(16).
              addLabel(dimX, 1).addLabel(dimY, 1).addLabel(dimZ, 1).addCell(17).
              addLabel(dimX, 2).addLabel(dimY, 0).addLabel(dimZ, 0).addCell(18).
              addLabel(dimX, 2).addLabel(dimY, 0).addLabel(dimZ, 1).addCell(19).
              addLabel(dimX, 2).addLabel(dimY, 1).addLabel(dimZ, 0).addCell(20).
              addLabel(dimX, 2).addLabel(dimY, 1).addLabel(dimZ, 1).addCell(21);
    assertTensor({{"x",3},{"y",2},{"z",2}},
            {10,11,12,13,14,15,16,17,18,19,20,21},
            *f.builder.build());
}

TEST_F("require that cells get default value 0 if not specified", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 3);
    f.builder.addLabel(dimX, 1).addCell(11);
    assertTensor({{"x",3}}, {0,11,0},
            *f.builder.build());
}

TEST_F("require that labels can be added in arbitrarily order", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 2);
    Builder::Dimension dimY = f.builder.defineDimension("y", 3);
    f.builder.addLabel(dimY, 0).addLabel(dimX, 1).addCell(10);
    assertTensor({{"x",2},{"y",3}}, {0,0,0,10,0,0},
            *f.builder.build());
}

TEST_F("require that builder can be re-used", Fixture)
{
    {
        Builder::Dimension dimX = f.builder.defineDimension("x", 2);
        f.builder.addLabel(dimX, 0).addCell(10).
                  addLabel(dimX, 1).addCell(11);
        assertTensor({{"x",2}}, {10,11},
                *f.builder.build());
    }
    {
        Builder::Dimension dimY = f.builder.defineDimension("y", 3);
        f.builder.addLabel(dimY, 0).addCell(20).
                  addLabel(dimY, 1).addCell(21).
                  addLabel(dimY, 2).addCell(22);
        assertTensor({{"y",3}}, {20,21,22},
                *f.builder.build());
    }
}

void
assertTensorCell(const DenseTensor::Address &expAddress,
                 double expCell,
                 const DenseTensor::CellsIterator &itr)
{
    EXPECT_TRUE(itr.valid());
    EXPECT_EQUAL(expAddress, itr.address());
    EXPECT_EQUAL(expCell, itr.cell());
}

TEST_F("require that dense tensor cells iterator works for 1d tensor", Fixture)
{
    Tensor::UP tensor;
    {
        Builder::Dimension dimX = f.builder.defineDimension("x", 2);
        f.builder.addLabel(dimX, 0).addCell(2).
                  addLabel(dimX, 1).addCell(3);
        tensor = f.builder.build();
    }

    const DenseTensor &denseTensor = dynamic_cast<const DenseTensor &>(*tensor);
    DenseTensor::CellsIterator itr = denseTensor.cellsIterator();

    assertTensorCell({0}, 2, itr);
    itr.next();
    assertTensorCell({1}, 3, itr);
    itr.next();
    EXPECT_FALSE(itr.valid());
}

TEST_F("require that dense tensor cells iterator works for 2d tensor", Fixture)
{
    Tensor::UP tensor;
    {
        Builder::Dimension dimX = f.builder.defineDimension("x", 2);
        Builder::Dimension dimY = f.builder.defineDimension("y", 2);
        f.builder.addLabel(dimX, 0).addLabel(dimY, 0).addCell(2).
                  addLabel(dimX, 0).addLabel(dimY, 1).addCell(3).
                  addLabel(dimX, 1).addLabel(dimY, 0).addCell(5).
                  addLabel(dimX, 1).addLabel(dimY, 1).addCell(7);
        tensor = f.builder.build();
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

TEST_F("require that undefined label for a dimension throws exception", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 2);
    f.builder.defineDimension("y", 3);
    EXPECT_EXCEPTION(f.builder.addLabel(dimX, 0).addCell(10),
            IllegalArgumentException,
            "Label for dimension 'y' is undefined. Expected a value in the range [0, 3>");
}

TEST_F("require that label outside range throws exception", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 2);
    EXPECT_EXCEPTION(f.builder.addLabel(dimX, 2).addCell(10),
            IllegalArgumentException,
            "Label '2' for dimension 'x' is outside range [0, 2>");
}

TEST_F("require that already specified label throws exception", Fixture)
{
    Builder::Dimension dimX = f.builder.defineDimension("x", 2);
    EXPECT_EXCEPTION(f.builder.addLabel(dimX, 0).addLabel(dimX, 1).addCell(10),
            IllegalArgumentException,
            "Label for dimension 'x' is already specified with value '0'");
}

TEST_F("require that dimensions are sorted", Fixture)
{
    Builder::Dimension dimY = f.builder.defineDimension("y", 3);
    Builder::Dimension dimX = f.builder.defineDimension("x", 5);
    f.builder.addLabel(dimX, 0).addLabel(dimY, 0).addCell(10);
    f.builder.addLabel(dimX, 0).addLabel(dimY, 1).addCell(11);
    f.builder.addLabel(dimX, 1).addLabel(dimY, 0).addCell(12);
    std::unique_ptr<Tensor> tensor = f.builder.build();
    const DenseTensor &denseTensor = dynamic_cast<const DenseTensor &>(*tensor);
    assertTensor({{"x", 5}, {"y", 3}},
                 {10, 11, 0, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                 denseTensor);
    EXPECT_EQUAL("tensor(x[5],y[3])", denseTensor.type().to_spec());
}






TEST_MAIN() { TEST_RUN_ALL(); }
