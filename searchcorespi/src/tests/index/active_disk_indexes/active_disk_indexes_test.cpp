// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/index/activediskindexes.h>
#include <vespa/searchcorespi/index/index_disk_dir.h>
#include <vespa/searchcorespi/index/indexdisklayout.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <fstream>

namespace {

vespalib::string base_dir("base");

constexpr uint32_t block_size = 4_Ki;

}

namespace searchcorespi::index {

class ActiveDiskIndexesTest : public ::testing::Test,
                              public ActiveDiskIndexes
{
    IndexDiskLayout _layout;
protected:
    ActiveDiskIndexesTest();
    ~ActiveDiskIndexesTest();

    static IndexDiskDir get_index_disk_dir(const vespalib::string& dir) {
        return IndexDiskLayout::get_index_disk_dir(dir);
    }

    void assert_transient_size(uint64_t exp, IndexDiskDir index_disk_dir) {
        EXPECT_EQ(exp, get_transient_size(_layout, index_disk_dir));
    }
};

ActiveDiskIndexesTest::ActiveDiskIndexesTest()
    : ::testing::Test(),
      ActiveDiskIndexes(),
      _layout(base_dir)
{
}

ActiveDiskIndexesTest::~ActiveDiskIndexesTest() = default;

TEST_F(ActiveDiskIndexesTest, simple_set_active_works)
{
    EXPECT_FALSE(isActive("index.flush.1"));
    setActive("index.flush.1", 0);
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_FALSE(isActive("index.flush.1"));
}

TEST_F(ActiveDiskIndexesTest, nested_set_active_works)
{
    setActive("index.flush.1", 0);
    setActive("index.flush.1", 0);
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

TEST_F(ActiveDiskIndexesTest, remove_works)
{
    EXPECT_TRUE(remove(IndexDiskDir()));
    auto fusion1 = get_index_disk_dir("index.fusion.1");
    EXPECT_TRUE(remove(fusion1));
    add_not_active(fusion1);
    EXPECT_TRUE(remove(fusion1));
    setActive("index.fusion.1", 0);
    EXPECT_FALSE(remove(fusion1));
    notActive("index.fusion.1");
    EXPECT_TRUE(remove(fusion1));
}

TEST_F(ActiveDiskIndexesTest, basic_get_transient_size_works)
{
    setActive("index.fusion.1", 1000000);
    setActive("index.flush.2", 500000);
    setActive("index.fusion.2", 1200000);
    auto fusion1 = get_index_disk_dir("index.fusion.1");
    auto flush2 = get_index_disk_dir("index.flush.2");
    auto fusion2 = get_index_disk_dir("index.fusion.2");
    {
        SCOPED_TRACE("index.fusion.1");
        assert_transient_size(1200000, fusion1);
    }
    {
        SCOPED_TRACE("index.flush.2");
        assert_transient_size(0, flush2);
    }
    {
        SCOPED_TRACE("index.fusion.2");
        assert_transient_size(1500000, fusion2);
    }
    notActive("index.fusion.2");
    {
        SCOPED_TRACE("index.fusion.1 after remove of index.fusion.2");
        assert_transient_size(0, fusion1);
    }
}

TEST_F(ActiveDiskIndexesTest, dynamic_get_transient_size_works)
{
    setActive("index.fusion.1", 1000000);
    auto fusion1 = get_index_disk_dir("index.fusion.1");
    auto fusion2 = get_index_disk_dir("index.fusion.2");
    add_not_active(fusion2);
    {
        SCOPED_TRACE("dir missing");
        assert_transient_size(0, fusion1);
    }
    auto dir = base_dir + "/index.fusion.2";
    vespalib::mkdir(dir, true);
    {
        SCOPED_TRACE("empty dir");
        assert_transient_size(0, fusion1);
    }
    constexpr uint32_t seek_pos = 999999;
    {
        std::string name = dir + "/foo";
        std::ofstream ostr(name, std::ios::binary);
        ostr.seekp(seek_pos);
        ostr.write(" ", 1);
        ostr.flush();
        ostr.close();
    }
    {
        SCOPED_TRACE("single file");
        assert_transient_size((seek_pos + block_size) / block_size * block_size, fusion1);
    }
    EXPECT_TRUE(remove(fusion2));
    {
        SCOPED_TRACE("removed");
        assert_transient_size(0, fusion1);
    }
}

}

int
main(int argc, char* argv[])
{
    vespalib::rmdir(base_dir, true);
    ::testing::InitGoogleTest(&argc, argv);
    auto result = RUN_ALL_TESTS();
    vespalib::rmdir(base_dir, true);
    return result;
}
