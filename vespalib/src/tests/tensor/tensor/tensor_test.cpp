// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/simple/simple_tensor.h>
#include <vespa/vespalib/tensor/tensor_factory.h>
#include <vespa/vespalib/tensor/simple/simple_tensor_builder.h>

using namespace vespalib::tensor;

namespace
{

SimpleTensor::UP createTensor(const TensorCells &cells)
{
    SimpleTensorBuilder builder;
    return SimpleTensor::UP(static_cast<SimpleTensor *>
                            (TensorFactory::create(cells, builder).release()));
}

}

void
assertCellValue(double expValue, const TensorAddress &address, const SimpleTensor::Cells &cells)
{
    auto itr = cells.find(address);
    EXPECT_FALSE(itr == cells.end());
    EXPECT_EQUAL(expValue, itr->second);
}

TEST("require that tensor can be constructed")
{
    SimpleTensor::UP tensor = createTensor({ {{{"a","1"},{"b","2"}},10}, {{{"c","3"},{"d","4"}},20} });
    const SimpleTensor::Cells &cells = tensor->cells();
    EXPECT_EQUAL(2u, cells.size());
    assertCellValue(10, TensorAddress({{"a","1"},{"b","2"}}), cells);
    assertCellValue(20, TensorAddress({{"c","3"},{"d","4"}}), cells);
}

TEST("require that dimensions are extracted")
{
    SimpleTensor::UP tensor = createTensor({ {{{"a","1"},{"b","2"}},10}, {{{"b","3"},{"c","4"}},20} });
    const SimpleTensor::Dimensions &dims = tensor->dimensions();
    EXPECT_EQUAL(3u, dims.size());
    EXPECT_EQUAL("a", dims[0]);
    EXPECT_EQUAL("b", dims[1]);
    EXPECT_EQUAL("c", dims[2]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
