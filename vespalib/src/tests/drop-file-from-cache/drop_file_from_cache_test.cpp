// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/process/process.h>

using vespalib::Process;

TEST(DropFileFromCacheTest, no_arguments) {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache");
    EXPECT_EQ(1, drop.join());
}

TEST(DropFileFromCacheTest, file_does_not_exist) {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache not_exist");
    EXPECT_EQ(2, drop.join());
}

TEST(DropFileFromCacheTest, all_is_well) {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache vespalib_drop_file_from_cache_test_app");
    EXPECT_EQ(0, drop.join());
}

GTEST_MAIN_RUN_ALL_TESTS()
