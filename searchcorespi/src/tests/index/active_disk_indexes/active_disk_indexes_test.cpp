// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/index/activediskindexes.h>
#include <vespa/searchcorespi/index/index_disk_dir.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace searchcorespi::index {

class ActiveDiskIndexesTest : public ::testing::Test,
                              public ActiveDiskIndexes
{
protected:
    ActiveDiskIndexesTest();
    ~ActiveDiskIndexesTest();
};

ActiveDiskIndexesTest::ActiveDiskIndexesTest()
    : ::testing::Test(),
      ActiveDiskIndexes()
{
}

ActiveDiskIndexesTest::~ActiveDiskIndexesTest() = default;

TEST_F(ActiveDiskIndexesTest, simple_set_active_works)
{
    EXPECT_FALSE(isActive("index.flush.1"));
    setActive("index.flush.1");
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_FALSE(isActive("index.flush.1"));
}

TEST_F(ActiveDiskIndexesTest, nested_set_active_works)
{
    setActive("index.flush.1");
    setActive("index.flush.1");
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_FALSE(isActive("index.flush.1"));
}

TEST_F(ActiveDiskIndexesTest, is_active_returns_false_for_bad_name)
{
    EXPECT_FALSE(isActive("foo/bar/baz"));
    EXPECT_FALSE(isActive("index.flush.0"));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
