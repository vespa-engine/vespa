// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/sparse/direct_sparse_tensor_builder.h>
#include <vespa/eval/tensor/sparse/sparse_tensor_address_combiner.h>
#include <vespa/vespalib/test/insertion_operators.h>

using namespace vespalib::tensor;
using namespace vespalib::tensor::sparse;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;

void
assertCellValue(double expValue, const TensorAddress &address,
                const ValueType &type,
                const SparseTensor &tensor)
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
    size_t idx;
    bool found = tensor.index().lookup_address(addressRef, idx);
    EXPECT_TRUE(found);
    auto cells = tensor.cells();
    if (EXPECT_TRUE(cells.type == CellType::DOUBLE)) {
        auto arr = cells.typify<double>();
        EXPECT_EQUAL(expValue, arr[idx]);
    }
}

Tensor::UP
buildTensor()
{
    DirectSparseTensorBuilder<double> builder(ValueType::from_spec("tensor(a{},b{},c{},d{})"));
    SparseTensorAddressBuilder address;
    address.set({"1", "2", "", ""});
    builder.insertCell(address, 10);
    address.set({"", "", "3", "4"});
    builder.insertCell(address, 20);
    return builder.build();
}

TEST("require that tensor can be constructed")
{
    Tensor::UP tensor = buildTensor();
    const SparseTensor &sparseTensor = dynamic_cast<const SparseTensor &>(*tensor);
    const ValueType &type = sparseTensor.type();
    const auto & index = sparseTensor.index();
    EXPECT_EQUAL(2u, index.size());
    assertCellValue(10, TensorAddress({{"a","1"},{"b","2"}}), type, sparseTensor);
    assertCellValue(20, TensorAddress({{"c","3"},{"d","4"}}), type, sparseTensor);
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
    Tensor::UP tensor = buildTensor();
    const SparseTensor &sparseTensor = dynamic_cast<const SparseTensor &>(*tensor);
    const auto &dims = sparseTensor.type().dimensions();
    EXPECT_EQUAL(4u, dims.size());
    EXPECT_EQUAL("a", dims[0].name);
    EXPECT_EQUAL("b", dims[1].name);
    EXPECT_EQUAL("c", dims[2].name);
    EXPECT_EQUAL("d", dims[3].name);
    EXPECT_EQUAL("tensor(a{},b{},c{},d{})", sparseTensor.type().to_spec());
}

void verifyAddressCombiner(const ValueType & a, const ValueType & b, size_t numDim, size_t numOverlapping) {
    TensorAddressCombiner combiner(a, b);
    EXPECT_EQUAL(numDim, combiner.numDimensions());
    EXPECT_EQUAL(numOverlapping, combiner.numOverlappingDimensions());
}
TEST("Test sparse tensor address combiner") {
    verifyAddressCombiner(ValueType::tensor_type({{"a"}}), ValueType::tensor_type({{"b"}}), 2, 0);
    verifyAddressCombiner(ValueType::tensor_type({{"a"}, {"b"}}), ValueType::tensor_type({{"b"}}), 2, 1);
    verifyAddressCombiner(ValueType::tensor_type({{"a"}, {"b"}}), ValueType::tensor_type({{"b"}, {"c"}}), 3, 1);

}

TEST("Test essential object sizes") {
    EXPECT_EQUAL(16u, sizeof(SparseTensorAddressRef));
    EXPECT_EQUAL(24u, sizeof(std::pair<SparseTensorAddressRef, double>));
    EXPECT_EQUAL(32u, sizeof(vespalib::hash_node<std::pair<SparseTensorAddressRef, double>>));
    Tensor::UP tensor = buildTensor();
    size_t used = tensor->get_memory_usage().usedBytes();
    EXPECT_GREATER(used, sizeof(SparseTensor));
    EXPECT_LESS(used, 10000u);
    size_t allocated = tensor->get_memory_usage().allocatedBytes();
    EXPECT_GREATER(allocated, used);
    EXPECT_LESS(allocated, 50000u);
    fprintf(stderr, "tensor using %zu bytes of %zu allocated\n",
            used, allocated);
}

TEST_MAIN() { TEST_RUN_ALL(); }
