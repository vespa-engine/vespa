// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_builder.h>
#include <vespa/vespalib/test/insertion_operators.h>

using namespace vespalib::tensor;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;

void
assertCellValue(double expValue, const TensorAddress &address,
                const ValueType &type,
                const SparseTensor::Cells &cells)
{
    SparseTensorAddressBuilder addressBuilder;
    auto dimsItr = type.dimensions().cbegin();
    auto dimsItrEnd = type.dimensions().cend();
    for (const auto &element : address.elements()) {
        while ((dimsItr < dimsItrEnd) && (dimsItr->name < element.dimension())) {
            addressBuilder.add("");
            ++dimsItr;
        }
        assert((dimsItr != dimsItrEnd) && (dimsItr->name == element.dimension()));
        addressBuilder.add(element.label());
        ++dimsItr;
    }
    while (dimsItr < dimsItrEnd) {
        addressBuilder.add("");
        ++dimsItr;
    }
    SparseTensorAddressRef addressRef(addressBuilder.getAddressRef());
    auto itr = cells.find(addressRef);
    EXPECT_FALSE(itr == cells.end());
    EXPECT_EQUAL(expValue, itr->second);
}

Tensor::UP
buildTensor()
{
    SparseTensorBuilder builder;
    builder.define_dimension("c");
    builder.define_dimension("d");
    builder.define_dimension("a");
    builder.define_dimension("b");
    builder.add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("c"), "3").
        add_label(builder.define_dimension("d"), "4").add_cell(20);
    return builder.build();
}

TEST("require that tensor can be constructed")
{
    Tensor::UP tensor = buildTensor();
    const SparseTensor &sparseTensor = dynamic_cast<const SparseTensor &>(*tensor);
    const ValueType &type = sparseTensor.type();
    const SparseTensor::Cells &cells = sparseTensor.cells();
    EXPECT_EQUAL(2u, cells.size());
    assertCellValue(10, TensorAddress({{"a","1"},{"b","2"}}),
                    type, cells);
    assertCellValue(20, TensorAddress({{"c","3"},{"d","4"}}),
                    type, cells);
}

TEST("require that tensor can be converted to tensor spec")
{
    Tensor::UP tensor = buildTensor();
    TensorSpec expSpec("tensor(a{},b{},c{},d{})");
    expSpec.add({{"a", "1"}, {"b", "2"}, {"c", ""}, {"d", ""}}, 10).
        add({{"a", ""},{"b",""},{"c", "3"}, {"d", "4"}}, 20);
    TensorSpec actSpec = tensor->toSpec();
    EXPECT_EQUAL(expSpec, actSpec);
}

TEST("require that dimensions are extracted")
{
    SparseTensorBuilder builder;
    builder.define_dimension("c");
    builder.define_dimension("a");
    builder.define_dimension("b");
    builder.
        add_label(builder.define_dimension("a"), "1").
        add_label(builder.define_dimension("b"), "2").add_cell(10).
        add_label(builder.define_dimension("b"), "3").
        add_label(builder.define_dimension("c"), "4").add_cell(20);
    Tensor::UP tensor = builder.build();
    const SparseTensor &sparseTensor = dynamic_cast<const SparseTensor &>(*tensor);
    const auto &dims = sparseTensor.type().dimensions();
    EXPECT_EQUAL(3u, dims.size());
    EXPECT_EQUAL("a", dims[0].name);
    EXPECT_EQUAL("b", dims[1].name);
    EXPECT_EQUAL("c", dims[2].name);
    EXPECT_EQUAL("tensor(a{},b{},c{})", sparseTensor.getType().to_spec());
}

TEST_MAIN() { TEST_RUN_ALL(); }
