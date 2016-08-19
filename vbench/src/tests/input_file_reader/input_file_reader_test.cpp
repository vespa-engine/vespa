// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("input file reader") {
    {
        InputFileReader reader("not_found.txt");
        EXPECT_TRUE(reader.tainted());
    }
    {
        InputFileReader reader(vespalib::TestApp::GetSourceDirectory() + "simple_test_input.txt");
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
        InputFileReader reader(vespalib::TestApp::GetSourceDirectory() + "hard_test_input.txt");
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
