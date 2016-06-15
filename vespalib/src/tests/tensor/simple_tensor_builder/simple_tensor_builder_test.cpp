// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/simple/simple_tensor_builder.h>

using namespace vespalib::tensor;

void
assertCellValue(double expValue, const TensorAddress &address, const SimpleTensor::Cells &cells)
{
    auto itr = cells.find(address);
    EXPECT_FALSE(itr == cells.end());
    EXPECT_EQUAL(expValue, itr->second);
}

TEST("require that tensor can be constructed")
{
    SimpleTensorBuilder builder;
    builder.add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("c"), "3").
        add_label(builder.define_dimension("d"), "4").add_cell(20);
    Tensor::UP tensor = builder.build();
    const SimpleTensor &simpleTensor = dynamic_cast<const SimpleTensor &>(*tensor);
    const SimpleTensor::Cells &cells = simpleTensor.cells();
    EXPECT_EQUAL(2u, cells.size());
    assertCellValue(10, TensorAddress({{"a","1"},{"b","2"}}), cells);
    assertCellValue(20, TensorAddress({{"c","3"},{"d","4"}}), cells);
}

TEST("require that dimensions are extracted")
{
    SimpleTensorBuilder builder;
    builder.define_dimension("c");
    builder.define_dimension("a");
    builder.define_dimension("b");
    builder.
        add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("b"), "3").
        add_label(builder.define_dimension("c"), "4").add_cell(20);
    Tensor::UP tensor = builder.build();
    const SimpleTensor &simpleTensor = dynamic_cast<const SimpleTensor &>(*tensor);
    const SimpleTensor::Dimensions &dims = simpleTensor.dimensions();
    EXPECT_EQUAL(3u, dims.size());
    EXPECT_EQUAL("a", dims[0]);
    EXPECT_EQUAL("b", dims[1]);
    EXPECT_EQUAL("c", dims[2]);
    EXPECT_EQUAL("tensor(a{},b{},c{})", simpleTensor.getType().toSpec());
}

TEST_MAIN() { TEST_RUN_ALL(); }
