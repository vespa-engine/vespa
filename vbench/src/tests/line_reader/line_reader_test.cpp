// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("line reader") {
    SimpleBuffer buffer;
    {
        BufferedOutput dst(buffer, 64);
        dst.append("foo\n");
        dst.append("bar\r\n");
        dst.append("\n");
        dst.append("\rbaz\n");
        dst.append("\r\n");
        dst.append("zzz");
    }
    {
        LineReader src(buffer);
        string str;
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("foo", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("bar", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("\rbaz", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("zzz", str);
        EXPECT_TRUE(!src.readLine(str));
        EXPECT_EQUAL("", str);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
