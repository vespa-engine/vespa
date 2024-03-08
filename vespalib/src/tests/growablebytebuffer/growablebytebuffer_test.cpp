// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

using namespace vespalib;

TEST(GrowableByteBufferTest, test_growing)
{
    GrowableByteBuffer buf(10);

    buf.putInt(3);
    buf.putInt(7);
    buf.putLong(1234);
    buf.putDouble(1234);
    buf.putString("hei der");

    EXPECT_EQ(35u, buf.position());
}

GTEST_MAIN_RUN_ALL_TESTS()
