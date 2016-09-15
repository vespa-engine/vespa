// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/compact/compact_tensor_builder.h>

using namespace vespalib::tensor;


void
assertCellValue(double expValue, const TensorAddress &address, const CompactTensor::Cells &cells)
{
    CompactTensorAddressBuilder addressBuilder;
    for (const auto &element : address.elements()) {
        addressBuilder.add(element.dimension(), element.label());
    }
    CompactTensorAddressRef addressRef(addressBuilder.getAddressRef());
    auto itr = cells.find(addressRef);
    EXPECT_FALSE(itr == cells.end());
    EXPECT_EQUAL(expValue, itr->second);
}

TEST("require that tensor can be constructed")
{
    CompactTensorBuilder builder;
    builder.add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("c"), "3").
        add_label(builder.define_dimension("d"), "4").add_cell(20);
    Tensor::UP tensor = builder.build();
    const CompactTensor &compactTensor = dynamic_cast<const CompactTensor &>(*tensor);
    const CompactTensor::Cells &cells = compactTensor.cells();
    EXPECT_EQUAL(2u, cells.size());
    assertCellValue(10, TensorAddress({{"a","1"},{"b","2"}}), cells);
    assertCellValue(20, TensorAddress({{"c","3"},{"d","4"}}), cells);
}

TEST("require that dimensions are extracted")
{
    CompactTensorBuilder builder;
    builder.define_dimension("c");
    builder.define_dimension("a");
    builder.define_dimension("b");
    builder.
        add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("b"), "3").
        add_label(builder.define_dimension("c"), "4").add_cell(20);
    Tensor::UP tensor = builder.build();
    const CompactTensor &compactTensor = dynamic_cast<const CompactTensor &>(*tensor);
    const CompactTensor::Dimensions &dims = compactTensor.dimensions();
    EXPECT_EQUAL(3u, dims.size());
    EXPECT_EQUAL("a", dims[0]);
    EXPECT_EQUAL("b", dims[1]);
    EXPECT_EQUAL("c", dims[2]);
    EXPECT_EQUAL("tensor(a{},b{},c{})", compactTensor.getType().to_spec());
}

TEST_MAIN() { TEST_RUN_ALL(); }
