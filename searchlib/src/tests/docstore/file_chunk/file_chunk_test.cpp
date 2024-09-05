// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/docstore/filechunk.h>
#include <vespa/searchlib/docstore/writeablefilechunk.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <iomanip>

#include <vespa/log/log.h>

LOG_SETUP("file_chunk_test");

using namespace search;

using common::FileHeaderContext;
using vespalib::CpuUsage;
using vespalib::ThreadStackExecutor;

struct MyFileHeaderContext : public FileHeaderContext {
    void addTags(vespalib::GenericHeader &header, const std::string &name) const override {
        (void) header;
        (void) name;
    }
};

struct SetLidObserver : public ISetLid {
    std::vector<uint32_t> lids;
    void setLid(const unique_lock &guard, uint32_t lid, const LidInfo &lidInfo) override {
        (void) guard;
        (void) lidInfo;
        lids.push_back(lid);
    }
};

struct BucketizerObserver : public IBucketizer {
    mutable std::vector<uint32_t> lids;
    document::BucketId getBucketOf(const vespalib::GenerationHandler::Guard &guard, uint32_t lid) const override {
        (void) guard;
        lids.push_back(lid);
        return {};
    }
    vespalib::GenerationHandler::Guard getGuard() const override {
        return {};
    }
};

std::string
getData(uint32_t lid)
{
    std::ostringstream oss;
    oss << "data_" << std::setw(5) << std::setfill('0') << lid;
    return oss.str();
}

struct FixtureBase {
    test::DirectoryHandler dir;
    ThreadStackExecutor executor;
    uint64_t serialNum;
    TuneFileSummary tuneFile;
    MyFileHeaderContext fileHeaderCtx;
    std::mutex updateLock;
    SetLidObserver lidObserver;
    BucketizerObserver bucketizer;

    uint64_t nextSerialNum() {
        return serialNum++;
    };

    explicit FixtureBase(const std::string &baseName, bool dirCleanup = true)
        : dir(baseName),
          executor(1),
          serialNum(1),
          tuneFile(),
          fileHeaderCtx(),
          updateLock(),
          lidObserver(),
          bucketizer()
    {
        dir.cleanup(dirCleanup);
    }
    ~FixtureBase();
    void assertLidMap(const std::vector<uint32_t> &expLids) const {
        EXPECT_EQ(expLids, lidObserver.lids);
    }
    void assertBucketizer(const std::vector<uint32_t> &expLids) const {
        EXPECT_EQ(expLids, bucketizer.lids);
    }
};

FixtureBase::~FixtureBase() = default;

struct ReadFixture : public FixtureBase {
    FileChunk chunk;

    explicit ReadFixture(const std::string &baseName, bool dirCleanup = true)
        : FixtureBase(baseName, dirCleanup),
          chunk(FileChunk::FileId(0), FileChunk::NameId(1234), baseName, tuneFile, &bucketizer)
    {
        dir.cleanup(dirCleanup);
    }
    void updateLidMap(uint32_t docIdLimit) {
        std::unique_lock guard(updateLock);
        chunk.updateLidMap(guard, lidObserver, serialNum, docIdLimit);
    }
};

struct WriteFixture : public FixtureBase {
    WriteableFileChunk chunk;
    using CompressionConfig = vespalib::compression::CompressionConfig;

    WriteFixture(const std::string &baseName,
                 uint32_t docIdLimit,
                 bool dirCleanup = true)
        : FixtureBase(baseName, dirCleanup),
          chunk(executor, FileChunk::FileId(0), FileChunk::NameId(1234), baseName, serialNum, docIdLimit,
                {CompressionConfig(), 0x1000}, tuneFile, fileHeaderCtx, &bucketizer)
    {
        dir.cleanup(dirCleanup);
    }
    void flush() {
        chunk.flush(true, serialNum, CpuUsage::Category::WRITE);
        chunk.flushPendingChunks(serialNum);
    }
    WriteFixture &append(uint32_t lid) {
        std::string data = getData(lid);
        chunk.append(nextSerialNum(), lid, {data.c_str(), data.size()}, CpuUsage::Category::WRITE);
        return *this;
    }
    void updateLidMap(uint32_t docIdLimit) {
        std::unique_lock guard(updateLock);
        chunk.updateLidMap(guard, lidObserver, serialNum, docIdLimit);
        serialNum = chunk.getSerialNum();
    }

};

TEST(FileChunkTest, require_that_idx_file_without_docIdLimit_in_header_can_be_read_by_FileChunk)
{
    ReadFixture f(TEST_PATH("without_doc_id_limit"), false);
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), f.chunk.getDocIdLimit());
}

TEST(FileChunkTest, require_that_idx_file_without_docIdLimit_in_header_can_be_read_by_WriteableFileChunk)
{
    WriteFixture f(TEST_PATH("without_doc_id_limit"), 1000, false);
    EXPECT_EQ(std::numeric_limits<uint32_t>::max(), f.chunk.getDocIdLimit());
}

TEST(FileChunkTest, require_that_docIdLimit_is_written_to_and_read_from_idx_file_header)
{
    {
        WriteFixture f("tmp", 1000, false);
        EXPECT_EQ(1000u, f.chunk.getDocIdLimit());
    }
    {
        ReadFixture f("tmp", false);
        f.updateLidMap(std::numeric_limits<uint32_t>::max()); // trigger reading of idx file header
        EXPECT_EQ(1000u, f.chunk.getDocIdLimit());
    }
    {
        WriteFixture f("tmp", 0);
        EXPECT_EQ(1000u, f.chunk.getDocIdLimit());
    }
}

TEST(FileChunkTest, require_that_numlids_are_updated) {
    {
        WriteFixture f("tmp", 1000, false);
        f.updateLidMap(1000);
        EXPECT_EQ(0u, f.chunk.getNumLids());
        f.append(1);
        EXPECT_EQ(1u, f.chunk.getNumLids());
        f.append(2);
        f.append(3);
        EXPECT_EQ(3u, f.chunk.getNumLids());
        f.append(3);
        EXPECT_EQ(4u, f.chunk.getNumLids());
        f.flush();
    }
    {
        WriteFixture f("tmp", 1000, true);
        EXPECT_EQ(0u, f.chunk.getNumLids());
        f.updateLidMap(1000);
        EXPECT_EQ(4u, f.chunk.getNumLids());
        f.append(7);
        EXPECT_EQ(5u, f.chunk.getNumLids());
    }
}

template <typename FixtureType>
void
assertUpdateLidMap(FixtureType &f)
{
    std::vector<uint32_t> expLids({1,10,100,999,998,999});
    f.assertLidMap(expLids);
    f.assertBucketizer(expLids);
    size_t entrySize = 10 + 8;
    EXPECT_EQ(9 * entrySize, f.chunk.getAddedBytes());
    EXPECT_EQ(3u, f.chunk.getErasedCount());
    EXPECT_EQ(3 * entrySize, f.chunk.getErasedBytes());
}

TEST(FileChunkTest, require_that_entries_with_lid_ge__docIdLimit_are_skipped_in_updateLidMap)
{
    {
        WriteFixture f("tmp", 0, false);
        f.append(1).append(10).append(100).append(999).append(1000).append(1001).append(998).append(1002).append(999);
        f.flush();
    }
    {
        ReadFixture f("tmp", false);
        f.updateLidMap(1000);
        assertUpdateLidMap(f);
    }
    {
        WriteFixture f("tmp", 0);
        f.updateLidMap(1000);
        assertUpdateLidMap(f);
    }
}

using vespalib::compression::CompressionConfig;

TEST(FileChunkTest, require_that_operator_eq_detects_inequality) {
    using C = WriteableFileChunk::Config;
    EXPECT_TRUE(C() == C());
    EXPECT_TRUE(C({}, 1) == C({}, 1));
    EXPECT_FALSE(C({}, 2) == C({}, 1));
    EXPECT_FALSE(C({}, 1) == C({}, 2));
    EXPECT_FALSE(C({CompressionConfig::LZ4, 9, 60}, 2) == C({}, 2));
}

GTEST_MAIN_RUN_ALL_TESTS()
