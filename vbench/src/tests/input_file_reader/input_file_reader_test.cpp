// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(InputFileReaderTest, input_file_reader) {
    {
        InputFileReader reader("not_found.txt");
        EXPECT_TRUE(reader.tainted());
    }
    {
        InputFileReader reader(TEST_PATH("simple_test_input.txt"));
        EXPECT_TRUE(!reader.tainted());
        string line;
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("foo", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("bar", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("baz", line);
        EXPECT_TRUE(!reader.readLine(line));
    }
    {
        InputFileReader reader(TEST_PATH("hard_test_input.txt"));
        EXPECT_TRUE(!reader.tainted());
        string line;
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("foo", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("bar", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("baz", line);
        EXPECT_TRUE(reader.readLine(line));
        EXPECT_EQ("\r", line);
        EXPECT_TRUE(!reader.readLine(line));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
