// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_tensor_address_combiner.h>
#include <vespa/vespalib/test/insertion_operators.h>

using namespace vespalib::tensor;
using vespalib::eval::ValueType;

ValueType
combine(const std::vector<ValueType::Dimension> &lhs,
        const std::vector<ValueType::Dimension> &rhs)
{
    return DenseTensorAddressCombiner::combineDimensions(
            ValueType::tensor_type(lhs),
            ValueType::tensor_type(rhs));
}

TEST("require that dimensions can be combined")
{
    EXPECT_EQUAL(ValueType::tensor_type({{"a", 3}, {"b", 5}}), combine({{"a", 3}}, {{"b", 5}}));
    EXPECT_EQUAL(ValueType::tensor_type({{"a", 3}, {"b", 5}}), combine({{"a", 3}, {"b", 5}}, {{"b", 5}}));
    EXPECT_EQUAL(ValueType::tensor_type({{"a", 3}, {"b", 5}}), combine({{"a", 3}, {"b", 7}}, {{"b", 5}}));
    EXPECT_EQUAL(ValueType::tensor_type({{"a", 3}, {"b", 11}, {"c", 5}, {"d", 7}, {"e", 17}}),
                                combine({{"a", 3}, {"c", 5}, {"d", 7}},
                                        {{"b", 11}, {"c", 13}, {"e", 17}}));
    EXPECT_EQUAL(ValueType::tensor_type({{"a", 3}, {"b", 11}, {"c", 5}, {"d", 7}, {"e", 17}}),
                 combine({{"b", 11}, {"c", 13}, {"e", 17}},
                         {{"a", 3}, {"c", 5}, {"d", 7}}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
