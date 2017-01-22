// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("buffered output") {
    SimpleBuffer buffer;
    { // produce data
        BufferedOutput dst(buffer, 3);
        dst.append('a').append('b').append('c').append('\n');
        dst.append("foo bar").append('\n');
        dst.append(string("str")).append('\n');
        dst.printf("%d + %d = %d\n", 2, 2, 4);
    }
    { // verify data
        LineReader src(buffer);
        string str;
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("abc", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("foo bar", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("str", str);
        EXPECT_TRUE(src.readLine(str));
        EXPECT_EQUAL("2 + 2 = 4", str);
        EXPECT_TRUE(!src.readLine(str));
        EXPECT_EQUAL("", str);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
