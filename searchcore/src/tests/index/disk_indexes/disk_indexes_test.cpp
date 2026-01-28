// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/index/disk_indexes.h>
#include <vespa/searchcorespi/index/index_disk_dir.h>
#include <vespa/searchcorespi/index/indexdisklayout.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>
#include <fstream>

namespace {

std::string base_dir("base");

constexpr uint32_t block_size = 4_Ki;

}

namespace searchcorespi::index {

class DiskIndexesTest : public ::testing::Test,
                              public DiskIndexes
{
    IndexDiskLayout _layout;
protected:
    DiskIndexesTest();
    ~DiskIndexesTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();

    static IndexDiskDir get_index_disk_dir(const std::string& dir) {
        return IndexDiskLayout::get_index_disk_dir(dir);
    }

    uint64_t transient_size() const { return get_transient_size(_layout); }
};

DiskIndexesTest::DiskIndexesTest()
    : ::testing::Test(),
      DiskIndexes(),
      _layout(base_dir)
{
}

DiskIndexesTest::~DiskIndexesTest() = default;

void
DiskIndexesTest::SetUpTestSuite()
{
    std::filesystem::remove_all(std::filesystem::path(base_dir));
}

void
DiskIndexesTest::TearDownTestSuite()
{
    std::filesystem::remove_all(std::filesystem::path(base_dir));
}

TEST_F(DiskIndexesTest, simple_set_active_works)
{
    EXPECT_FALSE(isActive("index.flush.1"));
    setActive("index.flush.1", 0);
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_FALSE(isActive("index.flush.1"));
}

TEST_F(DiskIndexesTest, nested_set_active_works)
{
    setActive("index.flush.1", 0);
    setActive("index.flush.1", 0);
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_TRUE(isActive("index.flush.1"));
    notActive("index.flush.1");
    EXPECT_FALSE(isActive("index.flush.1"));
}

TEST_F(DiskIndexesTest, is_active_returns_false_for_bad_name)
{
    EXPECT_FALSE(isActive("foo/bar/baz"));
    EXPECT_FALSE(isActive("index.flush.0"));
}

TEST_F(DiskIndexesTest, remove_works)
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

TEST_F(DiskIndexesTest, basic_get_transient_size_works)
{
    /*
     * When starting to use a new fusion index, we have a transient
     * period with two ISearchableIndexCollection instances:
     * - old, containing index.fusion.1 and index.flush.2
     * - new, containing index.fusion.2
     */
    setActive("index.fusion.1", 1000000);
    setActive("index.flush.2", 500000);
    setActive("index.fusion.2", 1200000);
    /*
     * Disk space used by index.fusion.1 and index.flush.2 is considered transient.
     */
    EXPECT_EQ(1500000, transient_size());
    notActive("index.fusion.1");
    notActive("index.flush.2");
    EXPECT_EQ(0u, transient_size());
}

TEST_F(DiskIndexesTest, get_transient_size_during_ongoing_fusion)
{
    /*
     * During ongoing fusion, we have one ISearchableIndexCollection instance:
     * - old, containing index.fusion.1 and index.flush.2
     *
     * Fusion output directory is index.fusion.2
     */
    setActive("index.fusion.1", 1000000);
    setActive("index.flush.2", 500000);
    auto fusion2 = get_index_disk_dir("index.fusion.2");
    add_not_active(fusion2); // start tracking disk space for fusion output
    /*
     * Fusion not yet started.
     */
    EXPECT_EQ(0u, transient_size());
    auto dir = base_dir + "/index.fusion.2";
    std::filesystem::create_directories(std::filesystem::path(dir));
    /*
     * Fusion started, but no files written yet.
     */
    EXPECT_EQ(0u, transient_size());
    constexpr uint32_t seek_pos = 999999;
    {
        std::string name = dir + "/foo";
        std::ofstream ostr(name, std::ios::binary);
        ostr.seekp(seek_pos);
        ostr.write(" ", 1);
        ostr.flush();
        ostr.close();
    }
    /*
     * Fusion started, one file written.
     */
    EXPECT_EQ((seek_pos + block_size) / block_size * block_size, transient_size());
    EXPECT_TRUE(remove(fusion2)); // stop tracking disk space for fusion output
    /*
     * Fusion aborted.
     */
    EXPECT_EQ(0, transient_size());
}

TEST_F(DiskIndexesTest, get_size_on_disk_considers_index_staleness)
{
    EXPECT_EQ(0, get_size_on_disk(true));
    setActive("index.fusion.1", 1000000);
    EXPECT_EQ(1000000, get_size_on_disk(false));
    setActive("index.flush.2", 500000);
    EXPECT_EQ(1500000, get_size_on_disk(false));
    setActive("index.flush.3", 200000);
    EXPECT_EQ(1700000, get_size_on_disk(false));
    setActive("index.fusion.3", 1600000);
    // index.fusion.1, index.flush.2 and index.flush.3 are marked stale due to index.fusion.3 being marked active
    EXPECT_EQ(1600000, get_size_on_disk(false));
    EXPECT_EQ(3300000, get_size_on_disk(true));
    notActive("index.fusion.1");
    notActive("index.flush.2");
    notActive("index.flush.3");
    EXPECT_TRUE(remove(get_index_disk_dir("index.fusion.1")));
    EXPECT_TRUE(remove(get_index_disk_dir("index.flush.2")));
    EXPECT_TRUE(remove(get_index_disk_dir("index.flush.3")));
    EXPECT_EQ(1600000, get_size_on_disk(true));
}

}

GTEST_MAIN_RUN_ALL_TESTS()
