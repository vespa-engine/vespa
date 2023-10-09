// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("input file reader") {
    {
        InputFileReader reader("not_found.txt");
        EXPECT_TRUE(reader.tainted());
    }
    {
        InputFileReader reader(TEST_PATH("simple_test_input.txt"));
        EXPECT_TRUE(!reader.tainted());
        string line;
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("foo", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("bar", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("baz", line);
        EXPECT_TRUE(!reader.readLine(line));
        TEST_FLUSH();
    }
    {
        InputFileReader reader(TEST_PATH("hard_test_input.txt"));
        EXPECT_TRUE(!reader.tainted());
        string line;
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("foo", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("bar", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("baz", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQUAL("\r", line);
        EXPECT_TRUE(!reader.readLine(line));
        TEST_FLUSH();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
