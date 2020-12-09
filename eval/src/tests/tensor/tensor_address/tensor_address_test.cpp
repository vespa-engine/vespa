// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/tensor_address.h>

void
assertSortOrder(const TensorAddress::Elements &exp,
                const TensorAddress::Elements &input)
{
    TensorAddress address(input);
    EXPECT_EQUAL(exp, address.elements());
}

TEST("require that elements are sorted in constructor")
{
    assertSortOrder({{"a","1"},{"b","1"},{"c","1"}},
                    {{"c","1"},{"a","1"},{"b","1"}});
}

TEST("require that we can check whether a dimension is present")
{
    TensorAddress address({{"a","1"},{"b","1"}});
    EXPECT_TRUE(address.hasDimension("a"));
    EXPECT_TRUE(address.hasDimension("b"));
    EXPECT_FALSE(address.hasDimension("c"));
}

TEST("require that tensor address sort order is defined")
{
    TensorAddress::Elements single = {{"a","1"}};
    EXPECT_LESS(TensorAddress(single),
                TensorAddress({{"a","1"},{"b","1"}}));
    EXPECT_LESS(TensorAddress({{"a","1"},{"b","1"}}),
                TensorAddress({{"a","1"},{"c","1"}}));
}

TEST_MAIN() { TEST_RUN_ALL(); }
