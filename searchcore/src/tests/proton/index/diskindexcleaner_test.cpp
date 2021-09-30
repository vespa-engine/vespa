// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for diskindexcleaner.

#include <vespa/searchcorespi/index/activediskindexes.h>
#include <vespa/searchcorespi/index/diskindexcleaner.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastos/file.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("diskindexcleaner_test");

using std::string;
using std::vector;
using namespace searchcorespi::index;

namespace {

class Test : public vespalib::TestApp {
    void requireThatAllIndexesOlderThanLastFusionIsRemoved();
    void requireThatIndexesInUseAreNotRemoved();
    void requireThatInvalidFlushIndexesAreRemoved();
    void requireThatInvalidFusionIndexesAreRemoved();
    void requireThatRemoveDontTouchNewIndexes();

public:
    int Main() override;
};

const string index_dir = "diskindexcleaner_test_data";

void removeTestData() {
    FastOS_FileInterface::EmptyAndRemoveDirectory(index_dir.c_str());
}

int
Test::Main()
{
    TEST_INIT("diskindexcleaner_test");

    TEST_DO(removeTestData());

    TEST_DO(requireThatAllIndexesOlderThanLastFusionIsRemoved());
    TEST_DO(requireThatIndexesInUseAreNotRemoved());
    TEST_DO(requireThatInvalidFlushIndexesAreRemoved());
    TEST_DO(requireThatInvalidFusionIndexesAreRemoved());
    TEST_DO(requireThatRemoveDontTouchNewIndexes());

    TEST_DO(removeTestData());

    TEST_DONE();
}

void createIndex(const string &name) {
    FastOS_FileInterface::MakeDirIfNotPresentOrExit(index_dir.c_str());
    const string dir_name = index_dir + "/" + name;
    FastOS_FileInterface::MakeDirIfNotPresentOrExit(dir_name.c_str());
    const string serial_file = dir_name + "/serial.dat";
    FastOS_File file(serial_file.c_str());
    file.OpenWriteOnlyTruncate();
}

vector<string> readIndexes() {
    vector<string> indexes;
    FastOS_DirectoryScan dir_scan(index_dir.c_str());
    while (dir_scan.ReadNext()) {
        string name = dir_scan.GetName();
        if (!dir_scan.IsDirectory() || name.find("index.") != 0) {
            continue;
        }
        indexes.push_back(name);
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

void Test::requireThatAllIndexesOlderThanLastFusionIsRemoved() {
    createIndexes();
    ActiveDiskIndexes active_indexes;
    DiskIndexCleaner::clean(index_dir, active_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQUAL(3u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

void Test::requireThatIndexesInUseAreNotRemoved() {
    createIndexes();
    ActiveDiskIndexes active_indexes;
    active_indexes.setActive(index_dir + "/index.fusion.1");
    active_indexes.setActive(index_dir + "/index.flush.2");
    DiskIndexCleaner::clean(index_dir, active_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_TRUE(contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(contains(indexes, "index.flush.2"));

    active_indexes.notActive(index_dir + "/index.fusion.1");
    active_indexes.notActive(index_dir + "/index.flush.2");
    DiskIndexCleaner::clean(index_dir, active_indexes);
    indexes = readIndexes();
    EXPECT_TRUE(!contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(!contains(indexes, "index.flush.2"));
}

void Test::requireThatInvalidFlushIndexesAreRemoved() {
    createIndexes();
    FastOS_File((index_dir + "/index.flush.4/serial.dat").c_str()).Delete();
    ActiveDiskIndexes active_indexes;
    DiskIndexCleaner::clean(index_dir, active_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQUAL(2u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
}

void Test::requireThatInvalidFusionIndexesAreRemoved() {
    createIndexes();
    FastOS_File((index_dir + "/index.fusion.2/serial.dat").c_str()).Delete();
    ActiveDiskIndexes active_indexes;
    DiskIndexCleaner::clean(index_dir, active_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQUAL(4u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.1"));
    EXPECT_TRUE(contains(indexes, "index.flush.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

void Test::requireThatRemoveDontTouchNewIndexes() {
    createIndexes();
    FastOS_File((index_dir + "/index.flush.4/serial.dat").c_str()).Delete();
    ActiveDiskIndexes active_indexes;
    DiskIndexCleaner::removeOldIndexes(index_dir, active_indexes);
    vector<string> indexes = readIndexes();
    EXPECT_EQUAL(3u, indexes.size());
    EXPECT_TRUE(contains(indexes, "index.fusion.2"));
    EXPECT_TRUE(contains(indexes, "index.flush.3"));
    EXPECT_TRUE(contains(indexes, "index.flush.4"));
}

}  // namespace

TEST_APPHOOK(Test);
