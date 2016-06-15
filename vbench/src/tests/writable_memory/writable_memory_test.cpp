// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("empty") {
    WritableMemory wm;
    EXPECT_EQUAL((char*)0, wm.data);
    EXPECT_EQUAL(0u, wm.size);
}

TEST("from buffer") {
    char buf[3];
    WritableMemory wm(buf, 3);
    EXPECT_EQUAL(buf, wm.data);
    EXPECT_EQUAL(3u, wm.size);
}

TEST_MAIN() { TEST_RUN_ALL(); }
