// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("datastore_test");

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/docstore/chunkformats.h>
#include <vespa/searchlib/docstore/storebybucket.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/document/base/documentid.h>
#include <iostream>

#include <vespa/vespalib/util/exceptions.h>

using document::BucketId;
using namespace search::docstore;
using namespace search;
using search::index::DummyFileHeaderContext;

class MyTlSyncer : public transactionlog::SyncProxy {
    SerialNum _syncedTo;
public:
    MyTlSyncer(void) : _syncedTo(0) { }

    void sync(SerialNum syncTo) {
        _syncedTo = syncTo;
    }
};


namespace {

void
showStats(const DataStoreStorageStats &stats)
{
    fprintf(stdout,
            "Storage stats usage=%9lu bloat=%9lu"
            " lastSerial=%9lu lastFlushedSerial=%9lu"
            " maxBucketSpread=%6.2f\n",
            stats.diskUsage(), stats.diskBloat(),
            stats.lastSerialNum(), stats.lastFlushedSerialNum(),
            stats.maxBucketSpread());
    fflush(stdout);
}

void
showChunks(const std::vector<DataStoreFileChunkStats> &chunkStats)
{
    fprintf(stdout, "Number of chunks is %zu\n", chunkStats.size());
    for (const auto &chunk : chunkStats) {
        fprintf(stdout,
                "Chunk %019lu usage=%9lu bloat=%9lu"
                " lastSerial=%9lu lastFlushedSerial=%9lu"
                " bucketSpread=%6.2f\n",
                chunk.nameId(), chunk.diskUsage(), chunk.diskBloat(),
                chunk.lastSerialNum(), chunk.lastFlushedSerialNum(),
                chunk.maxBucketSpread());
    }
    fflush(stdout);
}

SerialNum
calcLastSerialNum(const std::vector<DataStoreFileChunkStats> &chunkStats)
{
    SerialNum lastSerialNum = 0u;
    for (const auto &chunk : chunkStats) {
        lastSerialNum = std::max(lastSerialNum, chunk.lastSerialNum());
    }
    return lastSerialNum;
}

SerialNum
calcLastFlushedSerialNum(const std::vector<DataStoreFileChunkStats> &chunkStats)
{
    SerialNum lastFlushedSerialNum = 0u;
    for (const auto &chunk : chunkStats) {
        lastFlushedSerialNum = std::max(lastFlushedSerialNum,
                                        chunk.lastFlushedSerialNum());
    }
    return lastFlushedSerialNum;
}

uint64_t
calcDiskUsage(const std::vector<DataStoreFileChunkStats> &chunkStats)
{
    uint64_t diskUsage = 0u;
    for (const auto &chunk : chunkStats) {
        diskUsage += chunk.diskUsage();
    }
    return diskUsage;
}

uint64_t
calcDiskBloat(const std::vector<DataStoreFileChunkStats> &chunkStats)
{
    uint64_t diskBloat = 0u;
    for (const auto &chunk : chunkStats) {
        diskBloat += chunk.diskBloat();
    }
    return diskBloat;
}

void
checkStats(IDataStore &store,
           SerialNum expLastSerial, SerialNum expLastFlushedSerial)
{
    DataStoreStorageStats storageStats(store.getStorageStats());
    std::vector<DataStoreFileChunkStats> chunkStats;
    chunkStats = store.getFileChunkStats();
    showStats(storageStats);
    showChunks(chunkStats);
    EXPECT_EQUAL(expLastSerial, storageStats.lastSerialNum());
    EXPECT_EQUAL(expLastFlushedSerial, storageStats.lastFlushedSerialNum());
    EXPECT_EQUAL(storageStats.lastSerialNum(), calcLastSerialNum(chunkStats));
    EXPECT_EQUAL(storageStats.lastFlushedSerialNum(),
                 calcLastFlushedSerialNum(chunkStats));
    EXPECT_EQUAL(storageStats.diskUsage(),
                 calcDiskUsage(chunkStats));
    EXPECT_EQUAL(storageStats.diskBloat(), calcDiskBloat(chunkStats));
}


}

TEST("testThatLidInfoOrdersFileChunkSize") {
    EXPECT_TRUE(LidInfo(1, 1, 1) == LidInfo(1, 1, 1));
    EXPECT_FALSE(LidInfo(1, 1, 1) < LidInfo(1, 1, 1));

    EXPECT_FALSE(LidInfo(1, 1, 1) == LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 1, 1) < LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 2, 1) < LidInfo(2, 1, 1));
    EXPECT_TRUE(LidInfo(1, 1, 2) < LidInfo(2, 1, 1));
}

TEST("test that DirectIOPadding works accordng to spec") {
    constexpr size_t FILE_SIZE = 4096*3;
    FastOS_File file("directio.test");
    file.EnableDirectIO();
    EXPECT_TRUE(file.OpenReadWrite());
    vespalib::AlignedHeapAlloc buf(FILE_SIZE, 4096);
    memset(buf.get(), 'a', buf.size());
    EXPECT_EQUAL(FILE_SIZE, file.Write2(buf.get(), FILE_SIZE));
    size_t padBefore(0);
    size_t padAfter(0);

    EXPECT_TRUE(file.DirectIOPadding(4096, 4096, padBefore, padAfter));
    EXPECT_EQUAL(0u, padBefore);
    EXPECT_EQUAL(0u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4095, 4096, padBefore, padAfter));
    EXPECT_EQUAL(4095u, padBefore);
    EXPECT_EQUAL(1u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4096, padBefore, padAfter));
    EXPECT_EQUAL(1u, padBefore);
    EXPECT_EQUAL(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4096, 4097, padBefore, padAfter));
    EXPECT_EQUAL(0u, padBefore);
    EXPECT_EQUAL(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4096, 4095, padBefore, padAfter));
    EXPECT_EQUAL(0u, padBefore);
    EXPECT_EQUAL(1u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4095, padBefore, padAfter));
    EXPECT_EQUAL(1u, padBefore);
    EXPECT_EQUAL(0u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4096, padBefore, padAfter));
    EXPECT_EQUAL(1u, padBefore);
    EXPECT_EQUAL(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4096, padBefore, padAfter));
    EXPECT_EQUAL(1u, padBefore);
    EXPECT_EQUAL(4095u, padAfter);

    EXPECT_FALSE(file.DirectIOPadding(FILE_SIZE-1, 4096, padBefore, padAfter));
    EXPECT_EQUAL(0u, padBefore);
    EXPECT_EQUAL(0u, padAfter);
    EXPECT_EQUAL(FILE_SIZE, file.GetSize());

    FastOS_File file2("directio.test");
    file2.EnableDirectIO();
    EXPECT_TRUE(file2.OpenWriteOnlyExisting(true));
    EXPECT_TRUE(file2.SetPosition(file2.GetSize()));
    EXPECT_EQUAL(FILE_SIZE, file2.GetSize());
    EXPECT_EQUAL(FILE_SIZE, file2.Write2(buf.get(), FILE_SIZE));
    EXPECT_EQUAL(FILE_SIZE*2, file2.GetSize());
    EXPECT_TRUE(file2.Close());

    EXPECT_TRUE(file.DirectIOPadding(4097, 4096, padBefore, padAfter));
    EXPECT_EQUAL(1u, padBefore);
    EXPECT_EQUAL(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(FILE_SIZE-1, 4096, padBefore, padAfter));
    EXPECT_EQUAL(4095u, padBefore);
    EXPECT_EQUAL(1u, padAfter);

    EXPECT_TRUE(file.Close());
    FastOS_File::Delete(file.GetFileName());
}

TEST("testGrowing") {
    FastOS_File::EmptyAndRemoveDirectory("growing");
    EXPECT_TRUE(FastOS_File::MakeDirectory("growing"));
    LogDataStore::Config config(100000, 0.1, 3.0, 0.2, 8, true,
                                WriteableFileChunk::Config(
                                        document::CompressionConfig(
                                                document::CompressionConfig::
                                                LZ4, 9, 60),
                                        1000,
                                        20));
    vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
    DummyFileHeaderContext fileHeaderContext;
    MyTlSyncer tlSyncer;
    {
        LogDataStore datastore(executor,
                               "growing",
                               config,
                               GrowStrategy(),
                               TuneFileSummary(),
                               fileHeaderContext,
                               tlSyncer,
                               NULL);
        srand(7);
        char buffer[12000];
        SerialNum lastSyncToken(0);
        for (size_t i(0); i < sizeof(buffer); i++) {
            buffer[i] = rand() & 0xff;
        }
        for (size_t i(1); i < 10000; i++) {
            long r = rand()%10000;
            assert(i > lastSyncToken);
            lastSyncToken = i;
            datastore.write(i, i, &buffer[r], uint8_t(buffer[r])*4);
        }
        datastore.flush(datastore.initFlush(lastSyncToken));
        for (size_t i(1); i < 200; i++) {
            assert(i + 20000 > lastSyncToken);
            lastSyncToken = i + 20000;
            datastore.remove(i + 20000, i);
        }
        for (size_t i(201); i < 2000; i+= 2) {
            assert(i + 20000 > lastSyncToken);
            lastSyncToken = i + 20000;
            datastore.remove(i + 20000, i);
        }
        datastore.flush(datastore.initFlush(lastSyncToken));
        datastore.compact(30000);
        datastore.remove(31000, 0);
        checkStats(datastore, 31000, 30000);
    }
    {
        LogDataStore datastore(executor,
                               "growing",
                               config,
                               GrowStrategy(),
                               TuneFileSummary(),
                               fileHeaderContext,
                               tlSyncer,
                               NULL);
        checkStats(datastore, 30000, 30000);
    }

    FastOS_File::EmptyAndRemoveDirectory("growing");
}

void fetchAndTest(IDataStore & datastore, uint32_t lid, const void *a, size_t sz)
{
    vespalib::DataBuffer buf;
    EXPECT_EQUAL(static_cast<ssize_t>(sz), datastore.read(lid, buf));
    EXPECT_EQUAL(buf.getDataLen(), sz);
    EXPECT_TRUE(memcmp(a, buf.getData(), sz) == 0);
}

TEST("testTruncatedIdxFile"){
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
    MyTlSyncer tlSyncer;
    {
        // Files comes from the 'growing test'.
        LogDataStore datastore(executor,
                               vespalib::TestApp::GetSourceDirectory() + "bug-7257706", config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(354ul, datastore.lastSyncToken());
    }
    {
        LogDataStore datastore(executor, "bug-7257706-truncated", config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(331ul, datastore.lastSyncToken());
    }
    {
        LogDataStore datastore(executor, "bug-7257706-truncated", config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(331ul, datastore.lastSyncToken());
    }
}

TEST("testThatEmptyIdxFilesAndDanglingDatFilesAreRemoved") {
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
    MyTlSyncer tlSyncer;
    LogDataStore datastore(executor, "dangling-test", config,
                           GrowStrategy(), TuneFileSummary(),
                           fileHeaderContext, tlSyncer, NULL);
    EXPECT_EQUAL(354ul, datastore.lastSyncToken());
    EXPECT_EQUAL(4096u + 480u, datastore.getDiskHeaderFootprint());
    EXPECT_EQUAL(datastore.getDiskHeaderFootprint() + 94016u, datastore.getDiskFootprint());
}

TEST("testWriteRead") {
    FastOS_File::RemoveDirectory("empty");
    const char * bufA = "aaaaaaaaaaaaaaaaaaaaa";
    const char * bufB = "bbbbbbbbbbbbbbbb";
    const vespalib::ConstBufferRef a[2] = { vespalib::ConstBufferRef(bufA, strlen(bufA)), vespalib::ConstBufferRef(bufB, strlen(bufB))};
    LogDataStore::Config config;
    {
        EXPECT_TRUE(FastOS_File::MakeDirectory("empty"));
        DummyFileHeaderContext fileHeaderContext;
        vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
        MyTlSyncer tlSyncer;
        LogDataStore datastore(executor, "empty", config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, NULL);
        ASSERT_TRUE(datastore.lastSyncToken() == 0);
        size_t headerFootprint = datastore.getDiskHeaderFootprint();
        EXPECT_LESS(0u, headerFootprint);
        EXPECT_EQUAL(datastore.getDiskFootprint(), headerFootprint);
        EXPECT_EQUAL(datastore.getDiskBloat(), 0ul);
        EXPECT_EQUAL(datastore.getMaxCompactGain(), 0ul);
        datastore.write(1, 0, a[0].c_str(), a[0].size());
        fetchAndTest(datastore, 0, a[0].c_str(), a[0].size());
        datastore.write(2, 0, a[1].c_str(), a[1].size());
        fetchAndTest(datastore, 0, a[1].c_str(), a[1].size());
        fetchAndTest(datastore, 1, NULL, 0);
        datastore.remove(3, 0);
        fetchAndTest(datastore, 0, "", 0);

        SerialNum lastSyncToken(0);
        for(size_t i=0; i < 100; i++) {
            datastore.write(i+4, i, a[i%2].c_str(), a[i%2].size());
            assert(i +4 > lastSyncToken);
            lastSyncToken = i + 4;
            fetchAndTest(datastore, i, a[i%2].c_str(), a[i%2].size());
        }
        for(size_t i=0; i < 100; i++) {
            fetchAndTest(datastore, i, a[i%2].c_str(), a[i%2].size());
        }
        EXPECT_EQUAL(datastore.getDiskFootprint(),
                     2711ul + headerFootprint);
        EXPECT_EQUAL(datastore.getDiskBloat(), 0ul);
        EXPECT_EQUAL(datastore.getMaxCompactGain(), 0ul);
        datastore.flush(datastore.initFlush(lastSyncToken));
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
        MyTlSyncer tlSyncer;
        LogDataStore datastore(executor, "empty", config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, NULL);
        size_t headerFootprint = datastore.getDiskHeaderFootprint();
        EXPECT_LESS(0u, headerFootprint);
        EXPECT_EQUAL(4944ul + headerFootprint, datastore.getDiskFootprint());
        EXPECT_EQUAL(0ul, datastore.getDiskBloat());
        EXPECT_EQUAL(0ul, datastore.getMaxCompactGain());

        for(size_t i=0; i < 100; i++) {
            fetchAndTest(datastore, i, a[i%2].c_str(), a[i%2].size());
        }
        for(size_t i=0; i < 100; i++) {
            datastore.write(i+3+100, i, a[(i+1)%2].c_str(), a[(i+1)%2].size());
            fetchAndTest(datastore, i, a[(i+1)%2].c_str(), a[(i+1)%2].size());
        }
        for(size_t i=0; i < 100; i++) {
            fetchAndTest(datastore, i, a[(i+1)%2].c_str(), a[(i+1)%2].size());
        }

        EXPECT_EQUAL(7594ul + headerFootprint, datastore.getDiskFootprint());
        EXPECT_EQUAL(0ul, datastore.getDiskBloat());
        EXPECT_EQUAL(0ul, datastore.getMaxCompactGain());
    }
    FastOS_File::EmptyAndRemoveDirectory("empty");
}

TEST("requireThatSyncTokenIsUpdatedAfterFlush") {
#if 0
    std::string file = "sync.dat";
    FastOS_File::Delete(file.c_str());
    {
        vespalib::DataBuffer buf;
        SimpleDataStore store(file);
        EXPECT_EQUAL(0u, store.lastSyncToken());
        makeData(buf, 10);
        store.write(0, buf, 10);
        store.flush(4);
        EXPECT_EQUAL(4u, store.lastSyncToken());
    }
    FastOS_File::Delete(file.c_str());
#endif
}

class GuardDirectory {
public:
    GuardDirectory(const vespalib::string & dir) : _dir(dir)
    {
        FastOS_File::EmptyAndRemoveDirectory(_dir.c_str());
        EXPECT_TRUE(FastOS_File::MakeDirectory(_dir.c_str()));
    }
    ~GuardDirectory() {
        FastOS_File::EmptyAndRemoveDirectory(_dir.c_str());
    }
    const vespalib::string & getDir() const { return _dir; }
private:
    vespalib::string _dir;
};

TEST("requireThatFlushTimeIsAvailableAfterFlush") {
    GuardDirectory testDir("flushtime");
    fastos::TimeStamp before(fastos::ClockSystem::now());
    DummyFileHeaderContext fileHeaderContext;
    LogDataStore::Config config;
    vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
    MyTlSyncer tlSyncer;
    LogDataStore store(executor,
                       testDir.getDir(),
                       config,
                       GrowStrategy(),
                       TuneFileSummary(),
                       fileHeaderContext,
                       tlSyncer,
                       NULL);
    EXPECT_EQUAL(0, store.getLastFlushTime().time());
    uint64_t flushToken = store.initFlush(5);
    EXPECT_EQUAL(5u, flushToken);
    store.flush(flushToken);
    fastos::TimeStamp after(fastos::ClockSystem::now());
    // the file name of the dat file is 'magic', using the clock instead of stating the file
    EXPECT_LESS_EQUAL(before.time(), store.getLastFlushTime().time());
    EXPECT_GREATER_EQUAL(after.time(), store.getLastFlushTime().time());
}

TEST("requireThatChunksObeyLimits") {
    Chunk c(0, Chunk::Config(256, 2));
    EXPECT_TRUE(c.hasRoom(1000)); // At least 1 is allowed no matter what the size is.
    c.append(1, "abc", 3);
    EXPECT_TRUE(c.hasRoom(229));
    EXPECT_FALSE(c.hasRoom(230));
    c.append(2, "abc", 3);
    EXPECT_FALSE(c.hasRoom(20));
}

TEST("requireThatChunkCanProduceUniqueList") {
    const char *d = "ABCDEF";
    Chunk c(0, Chunk::Config(100, 20));
    c.append(1, d, 1);
    c.append(2, d, 2);
    c.append(3, d, 3);
    c.append(2, d, 4);
    c.append(1, d, 5);
    EXPECT_EQUAL(5u, c.count());
    const Chunk::LidList & all = c.getLids();
    EXPECT_EQUAL(5u, all.size());
    Chunk::LidList unique = c.getUniqueLids();
    EXPECT_EQUAL(3u, unique.size());
    EXPECT_EQUAL(1u, unique[0].getLid());
    EXPECT_EQUAL(5u, unique[0].netSize());
    EXPECT_EQUAL(2u, unique[1].getLid());
    EXPECT_EQUAL(4u, unique[1].netSize());
    EXPECT_EQUAL(3u, unique[2].getLid());
    EXPECT_EQUAL(3u, unique[2].netSize());
}

void testChunkFormat(ChunkFormat & cf, size_t expectedLen, const vespalib::string & expectedContent)
{
    document::CompressionConfig cfg;
    uint64_t MAGIC_CONTENT(0xabcdef9876543210);
    cf.getBuffer() << MAGIC_CONTENT;
    vespalib::DataBuffer buffer;
    cf.pack(7, buffer, cfg);
    EXPECT_EQUAL(expectedLen, buffer.getDataLen());
    std::ostringstream os;
    os << vespalib::HexDump(buffer.getData(), buffer.getDataLen());
    EXPECT_EQUAL(expectedContent, os.str());
}

TEST("requireThatChunkFormatsDoesNotChangeBetweenReleases") {
    ChunkFormatV1 v1(10);
    testChunkFormat(v1, 26, "26 000000000010ABCDEF987654321000000000000000079CF5E79B");
    ChunkFormatV2 v2(10);
    testChunkFormat(v2, 34, "34 015BA32DE7000000220000000010ABCDEF987654321000000000000000074D000694");
}

class DummyBucketizer : public IBucketizer
{
public:
    DummyBucketizer(uint32_t mod) : _mod(mod) { }
    BucketId getBucketOf(const vespalib::GenerationHandler::Guard &, uint32_t lid) const override {
        return BucketId(58, lid%_mod);
    }
    vespalib::GenerationHandler::Guard getGuard() const override {
        return vespalib::GenerationHandler::Guard();
    }
private:
    uint32_t _mod;
};

TEST("testBucketDensityComputer") {
    DummyBucketizer bucketizer(100);
    BucketDensityComputer bdc(&bucketizer);
    vespalib::GenerationHandler::Guard guard = bdc.getGuard();
    EXPECT_EQUAL(0u, bdc.getNumBuckets());
    bdc.recordLid(guard, 1, 1);
    EXPECT_EQUAL(1u, bdc.getNumBuckets());
    bdc.recordLid(guard, 2, 1);
    EXPECT_EQUAL(2u, bdc.getNumBuckets());
    bdc.recordLid(guard, 3, 1);
    EXPECT_EQUAL(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 2, 1);
    EXPECT_EQUAL(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 4, 0);
    EXPECT_EQUAL(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 4, 1);
    EXPECT_EQUAL(4u, bdc.getNumBuckets());

    BucketDensityComputer nonRecording(nullptr);
    guard = nonRecording.getGuard();
    EXPECT_EQUAL(0u, nonRecording.getNumBuckets());
    nonRecording.recordLid(guard, 1, 1);
    EXPECT_EQUAL(0u, nonRecording.getNumBuckets());
}

vespalib::string
createPayload(BucketId b) {
    constexpr const char * BUF = "Buffer for testing Bucket drain order.";
    vespalib::asciistream os;
    os << BUF << " " << b;
    return os.str();
}
uint32_t userId(size_t i) { return i%100; }

void
add(StoreByBucket & sbb, size_t i) {
    constexpr size_t USED_BITS=5;
    vespalib::asciistream os;
    os << "id:a:b:n=" << userId(i) << ":" << i;
    document::DocumentId docId(os.str());
    BucketId b = docId.getGlobalId().convertToBucketId();
    EXPECT_EQUAL(userId(i), docId.getGlobalId().getLocationSpecificBits());
    b.setUsedBits(USED_BITS);
    vespalib::string s = createPayload(b);
    sbb.add(b, i%10, i, s.c_str(), s.size());
}

class VerifyBucketOrder : public StoreByBucket::IWrite {
public:
    VerifyBucketOrder() : _lastLid(0), _lastBucketId(0), _uniqueUser(), _uniqueBucket() { }
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override {
        (void) sz;
        (void) chunkId;
        if (_lastBucketId != bucketId) {
            EXPECT_TRUE(_uniqueBucket.find(bucketId.getRawId()) == _uniqueBucket.end());
            _uniqueBucket.insert(bucketId.getRawId());
        }
        if (userId(_lastLid) != userId(lid)) {
            EXPECT_TRUE(_uniqueUser.find(userId(lid)) == _uniqueUser.end());
            _uniqueUser.insert(userId(lid));
        }
        _lastLid = lid;
        _lastBucketId = bucketId;
    }
private:
    uint32_t _lastLid;
    BucketId _lastBucketId;
    uint32_t _lastUser;
    vespalib::hash_set<uint32_t> _uniqueUser;
    vespalib::hash_set<uint64_t> _uniqueBucket;
};

TEST("test that StoreByBucket gives bucket by bucket and ordered within") {
    StoreByBucket sbb;
    for (size_t i(1); i <=500; i++) {
        add(sbb, i);
    }
    for (size_t i(1000); i > 500; i--) {
        add(sbb, i);
    }
    EXPECT_EQUAL(1u, sbb.getChunkCount());
    EXPECT_EQUAL(32u, sbb.getBucketCount());
    EXPECT_EQUAL(1000u, sbb.getLidCount());
    VerifyBucketOrder vbo;
    sbb.drain(vbo);
}

TEST_MAIN() {
    DummyFileHeaderContext::setCreator("logdatastore_test");
    TEST_RUN_ALL();
}
