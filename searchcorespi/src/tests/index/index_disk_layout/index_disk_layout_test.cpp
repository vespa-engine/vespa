// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/index/indexdisklayout.h>
#include <vespa/searchcorespi/index/index_disk_dir.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace searchcorespi::index {

namespace {

void expect_index_disk_dir(IndexDiskDir exp, const vespalib::string& dir)
{
    auto act = IndexDiskLayout::get_index_disk_dir(dir);
    ASSERT_TRUE(act.valid());
    ASSERT_EQ(exp, act);
}

void expect_bad_index_disk_dir(const vespalib::string& dir)
{
    auto act = IndexDiskLayout::get_index_disk_dir(dir);
    ASSERT_FALSE(act.valid());
}

}

TEST(IndexDiskLayoutTest, get_index_disk_dir_works)
{
    {
        SCOPED_TRACE("index.fusion.1");
        expect_index_disk_dir(IndexDiskDir(1, true), "index.fusion.1");
    }
    {
        SCOPED_TRACE("index.flush.2");
        expect_index_disk_dir(IndexDiskDir(2, false), "index.flush.2");
    }
    {
        SCOPED_TRACE("index.flush.3");
        expect_index_disk_dir(IndexDiskDir(3, false), "index.flush.3");
    }
    {
        SCOPED_TRACE("foo/bar/index.flush.4");
        expect_index_disk_dir(IndexDiskDir(4, false), "foo/bar/index.flush.4");
    }
    {
        SCOPED_TRACE("index.flush.");
        expect_bad_index_disk_dir("index.flush.");
    }
    {
        SCOPED_TRACE("index.flush.0");
        expect_bad_index_disk_dir("index.flush.0");
    }
    {
        SCOPED_TRACE("asdf");
        expect_bad_index_disk_dir("asdf");
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
