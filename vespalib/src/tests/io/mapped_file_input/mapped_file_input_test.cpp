// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <vespa/vespalib/io/mapped_file_input.h>

using namespace vespalib;

TEST(MappedFileInputTest, require_that_missing_file_is_invalid) {
    MappedFileInput file(TEST_PATH("not_found.txt"));
    EXPECT_TRUE(!file.valid());
}

TEST(MappedFileInputTest, require_that_file_can_be_accessed_as_in_input) {
    MappedFileInput file(TEST_PATH("file.txt"));
    EXPECT_TRUE(file.valid());
    EXPECT_EQ(file.get(), Memory("file content\n"));
    EXPECT_EQ(file.obtain(), Memory("file content\n"));
    file.evict(5);
    EXPECT_EQ(file.obtain(), Memory("content\n"));
    file.evict(8);
    EXPECT_EQ(file.obtain(), Memory());
}

GTEST_MAIN_RUN_ALL_TESTS()
