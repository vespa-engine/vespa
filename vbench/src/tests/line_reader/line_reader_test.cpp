// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

using OutputWriter = vespalib::OutputWriter;
using SimpleBuffer = vespalib::SimpleBuffer;

TEST("line reader") {
    SimpleBuffer buffer;
    {
        OutputWriter dst(buffer, 64);
        dst.write("foo\n");
        dst.write("bar\r\n");
        dst.write("\n");
        dst.write("\rbaz\n");
        dst.write("\r\n");
        dst.write("zzz");
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
