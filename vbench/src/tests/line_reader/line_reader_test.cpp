// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

using OutputWriter = vespalib::OutputWriter;
using SimpleBuffer = vespalib::SimpleBuffer;

TEST(LineReaderTest, line_reader) {
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
        EXPECT_EQ("foo", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQ("bar", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQ("", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQ("\rbaz", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQ("", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQ("zzz", str);
        EXPECT_TRUE(!src.readLine(str));
        EXPECT_EQ("", str);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
