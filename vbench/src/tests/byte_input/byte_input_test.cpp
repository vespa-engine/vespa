// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("byte input") {
    SimpleBuffer buffer;
    {
        BufferedOutput out(buffer, 10);
        out.append("abcdefgh");
    }
    EXPECT_EQUAL(8u, buffer.get().size);
    {
        ByteInput in(buffer);
        EXPECT_EQUAL('a', in.get());
        EXPECT_EQUAL('b', in.get());
        EXPECT_EQUAL('c', in.get());
        EXPECT_EQUAL('d', in.get());
    }
    EXPECT_EQUAL(4u, buffer.get().size);
    {
        ByteInput in(buffer);
        EXPECT_EQUAL('e', in.get());
        EXPECT_EQUAL('f', in.get());
        EXPECT_EQUAL('g', in.get());
        EXPECT_EQUAL('h', in.get());
        EXPECT_EQUAL(-1, in.get());
        EXPECT_EQUAL(-1, in.get());
    }
    EXPECT_EQUAL(0u, buffer.get().size);
    {
        ByteInput in(buffer);
        EXPECT_EQUAL(-1, in.get());
    }
    EXPECT_EQUAL(0u, buffer.get().size);
}

TEST_MAIN() { TEST_RUN_ALL(); }
