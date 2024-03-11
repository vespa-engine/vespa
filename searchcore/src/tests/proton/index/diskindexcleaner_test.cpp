// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for diskindexcleaner.

#include <vespa/searchcorespi/index/disk_indexes.h>
#include <vespa/searchcorespi/index/diskindexcleaner.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/fastos/file.h>
#include <algorithm>
#include <filesystem>

using std::string;
using std::vector;
using namespace searchcorespi::index;

namespace {

const string index_dir = "diskindexcleaner_test_data";

void removeTestData() {
    std::filesystem::remove_all(std::filesystem::path(index_dir));
}

class DiskIndexCleanerTest : public ::testing::Test {
protected:
    DiskIndexCleanerTest();
    ~DiskIndexCleanerTest() override;
    void SetUp() override;
    void TearDown() override;
};

DiskIndexCleanerTest::DiskIndexCleanerTest() = default;
DiskIndexCleanerTest::~DiskIndexCleanerTest() = default;

void
DiskIndexCleanerTest::SetUp()
{
    removeTestData();
}

void
DiskIndexCleanerTest::TearDown()
{
    removeTestData();
}

void createIndex(const string &name) {
    std::filesystem::create_directory(std::filesystem::path(index_dir));
    const string dir_name = index_dir + "/" + name;
    std::filesystem::create_directory(std::filesystem::path(dir_name));
    const string serial_file = dir_name + "/serial.dat";
    FastOS_File file(serial_file.c_str());
    file.OpenWriteOnlyTruncate();
}

vector<string> readIndexes() {
    vector<string> indexes;
    std::filesystem::directory_iterator dir_scan(index_dir);
    for (auto& entry : dir_scan) {
        if (entry.is_directory() && entry.path().filename().string().find("index.") == 0) {
            indexes.push_back(entry.path().filename().string());
        }
    }
    return indexes;
}

template <class Container>
bool contains(Container c, typename Container::value_type v) {
    return find(c.begin(), c.end(), v) != c.end();
}

void createIndexes() {
    createIndex("index.flush.0");
    createIndex("index.flush.1");
    createIndex("index.fusion.1");
    createIndex("index.flush.2");
    createIndex("index.fusion.2");
    createIndex("index.flush.3");
    createIndex("index.flush.4");
}

TEST_F(DiskIndexCleanerTest, require_that_all_indexes_older_than_last_fusion_is_removed)
{
    createIndexes();
    DiskIndexes disk_indexes;
    DiskIndexCleaner::clean(index_dir, disk_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQ(3u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

TEST_F(DiskIndexCleanerTest, require_that_indexes_in_use_are_not_removed)
{
    createIndexes();
    DiskIndexes disk_indexes;
    disk_indexes.setActive(index_dir + "/index.fusion.1", 0);
    disk_indexes.setActive(index_dir + "/index.flush.2", 0);
    DiskIndexCleaner::clean(index_dir, disk_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_TRUE(contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(contains(indexes, "index.flush.2"));

    disk_indexes.notActive(index_dir + "/index.fusion.1");
    disk_indexes.notActive(index_dir + "/index.flush.2");
    DiskIndexCleaner::clean(index_dir, disk_indexes);
    indexes = readIndexes();
    EXPECT_TRUE(!contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(!contains(indexes, "index.flush.2"));
}

TEST_F(DiskIndexCleanerTest, require_that_invalid_flush_indexes_are_removed)
{
    createIndexes();
    std::filesystem::remove(std::filesystem::path(index_dir + "/index.flush.4/serial.dat"));
    DiskIndexes disk_indexes;
    DiskIndexCleaner::clean(index_dir, disk_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQ(2u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
}

TEST_F(DiskIndexCleanerTest, require_that_invalid_fusion_indexes_are_removed)
{
    createIndexes();
    std::filesystem::remove(std::filesystem::path(index_dir + "/index.fusion.2/serial.dat"));
    DiskIndexes disk_indexes;
    DiskIndexCleaner::clean(index_dir, disk_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQ(4u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(contains(indexes, "index.flush.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

TEST_F(DiskIndexCleanerTest, require_that_remove_doesnt_touch_new_indexes)
{
    createIndexes();
    std::filesystem::remove(std::filesystem::path(index_dir + "/index.flush.4/serial.dat"));
    DiskIndexes disk_indexes;
    DiskIndexCleaner::removeOldIndexes(index_dir, disk_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQ(3u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
