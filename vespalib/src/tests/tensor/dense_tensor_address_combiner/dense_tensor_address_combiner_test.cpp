// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/tensor/dense/dense_tensor_address_combiner.h>
#include <vespa/vespalib/test/insertion_operators.h>

using namespace vespalib::tensor;
using DimensionsMeta = DenseTensor::DimensionsMeta;

std::ostream &
operator<<(std::ostream &out, const DenseTensor::DimensionMeta &dimMeta)
{
    out << dimMeta.dimension() << "[" << dimMeta.size() << "]";
    return out;
}

DimensionsMeta
combine(const DimensionsMeta &lhs, const DimensionsMeta &rhs)
{
    return DenseTensorAddressCombiner::combineDimensions(lhs, rhs);
}

TEST("require that dimensions can be combined")
{
    EXPECT_EQUAL(DimensionsMeta({{"a", 3}, {"b", 5}}), combine({{"a", 3}}, {{"b", 5}}));
    EXPECT_EQUAL(DimensionsMeta({{"a", 3}, {"b", 5}}), combine({{"a", 3}, {"b", 5}}, {{"b", 5}}));
    EXPECT_EQUAL(DimensionsMeta({{"a", 3}, {"b", 5}}), combine({{"a", 3}, {"b", 7}}, {{"b", 5}}));
    EXPECT_EQUAL(DimensionsMeta({{"a", 3}, {"b", 11}, {"c", 5}, {"d", 7}, {"e", 17}}),
                                combine({{"a", 3}, {"c", 5}, {"d", 7}},
                                        {{"b", 11}, {"c", 13}, {"e", 17}}));
    EXPECT_EQUAL(DimensionsMeta({{"a", 3}, {"b", 11}, {"c", 5}, {"d", 7}, {"e", 17}}),
                                combine({{"b", 11}, {"c", 13}, {"e", 17}},
                                        {{"a", 3}, {"c", 5}, {"d", 7}}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
