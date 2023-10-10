// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/io/mapped_file_input.h>

using namespace vespalib;

TEST("require that missing file is invalid") {
    MappedFileInput file(TEST_PATH("not_found.txt"));
    EXPECT_TRUE(!file.valid());
}

TEST("require that file can be accessed as in input") {
    MappedFileInput file(TEST_PATH("file.txt"));
    EXPECT_TRUE(file.valid());
    EXPECT_EQUAL(file.get(), Memory("file content\n"));
    EXPECT_EQUAL(file.obtain(), Memory("file content\n"));
    file.evict(5);
    EXPECT_EQUAL(file.obtain(), Memory("content\n"));
    file.evict(8);
    EXPECT_EQUAL(file.obtain(), Memory());
}

TEST_MAIN() { TEST_RUN_ALL(); }
