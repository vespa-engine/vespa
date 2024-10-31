// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/docstore/chunkformats.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/docstore/storebybucket.h>
#include <vespa/searchlib/docstore/visitcache.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <vespa/vespalib/test/test_data.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/memory.h>
#include <cassert>
#include <filesystem>
#include <iomanip>
#include <random>

using document::BucketId;
using document::StringFieldValue;
using namespace search::docstore;
using namespace search;
using namespace vespalib::alloc;
using vespalib::CacheStats;
using search::index::DummyFileHeaderContext;
using search::test::DirectoryHandler;

class MyTlSyncer : public transactionlog::SyncProxy {
    SerialNum _syncedTo;
public:
    MyTlSyncer() : _syncedTo(0) { }

    void sync(SerialNum syncTo) override {
        _syncedTo = syncTo;
    }
};

using namespace search;
using namespace search::docstore;
using search::index::DummyFileHeaderContext;
using vespalib::compression::CompressionConfig;

namespace {

void
showStats(const DataStoreStorageStats &stats)
{
    fprintf(stdout,
            "Storage stats usage=%9" PRIu64 " bloat=%9" PRIu64
            " lastSerial=%9" PRIu64 " lastFlushedSerial=%9" PRIu64
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
                "Chunk %019" PRIu64 " usage=%9" PRIu64 " bloat=%9" PRIu64
                " lastSerial=%9" PRIu64 " lastFlushedSerial=%9" PRIu64
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
        lastFlushedSerialNum = std::max(lastFlushedSerialNum, chunk.lastFlushedSerialNum());
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
    EXPECT_EQ(expLastSerial, storageStats.lastSerialNum());
    EXPECT_EQ(expLastFlushedSerial, storageStats.lastFlushedSerialNum());
    EXPECT_EQ(storageStats.lastSerialNum(), calcLastSerialNum(chunkStats));
    EXPECT_EQ(storageStats.lastFlushedSerialNum(), calcLastFlushedSerialNum(chunkStats));
    EXPECT_EQ(storageStats.diskUsage(), calcDiskUsage(chunkStats));
    EXPECT_EQ(storageStats.diskBloat(), calcDiskBloat(chunkStats));
}

}

class LogDataStoreTest : public ::testing::Test, public vespalib::test::TestData<LogDataStoreTest>
{
protected:
    LogDataStoreTest();
    ~LogDataStoreTest();
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    static std::string _truncated_testdir;
};

LogDataStoreTest::LogDataStoreTest()
: ::testing::Test(),
TestData<LogDataStoreTest>()
{
}

LogDataStoreTest::~LogDataStoreTest() = default;

void
LogDataStoreTest::SetUpTestSuite()
{
    setup_test_data(TEST_PATH("bug-7257706"), "build-test-data");
}

void
LogDataStoreTest::TearDownTestSuite()
{
    tear_down_test_data();
}

#ifdef __linux__
TEST_F(LogDataStoreTest, test_that_DirectIOPadding_works_accordng_to_spec)
{
    constexpr ssize_t FILE_SIZE = 4_Ki*3;
    FastOS_File file("directio.test");
    file.EnableDirectIO();
    EXPECT_TRUE(file.OpenReadWrite());
    Alloc buf(Alloc::alloc_aligned(FILE_SIZE, 4_Ki));
    memset(buf.get(), 'a', buf.size());
    EXPECT_EQ(FILE_SIZE, file.Write2(buf.get(), FILE_SIZE));
    size_t padBefore(0);
    size_t padAfter(0);

    EXPECT_TRUE(file.DirectIOPadding(4_Ki, 4096, padBefore, padAfter));
    EXPECT_EQ(0u, padBefore);
    EXPECT_EQ(0u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4095, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(4095u, padBefore);
    EXPECT_EQ(1u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(1u, padBefore);
    EXPECT_EQ(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4_Ki, 4097, padBefore, padAfter));
    EXPECT_EQ(0u, padBefore);
    EXPECT_EQ(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4_Ki, 4095, padBefore, padAfter));
    EXPECT_EQ(0u, padBefore);
    EXPECT_EQ(1u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4095, padBefore, padAfter));
    EXPECT_EQ(1u, padBefore);
    EXPECT_EQ(0u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(1u, padBefore);
    EXPECT_EQ(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(4097, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(1u, padBefore);
    EXPECT_EQ(4095u, padAfter);

    EXPECT_FALSE(file.DirectIOPadding(FILE_SIZE-1, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(0u, padBefore);
    EXPECT_EQ(0u, padAfter);
    EXPECT_EQ(FILE_SIZE, file.getSize());

    FastOS_File file2("directio.test");
    file2.EnableDirectIO();
    EXPECT_TRUE(file2.OpenWriteOnlyExisting(true));
    EXPECT_TRUE(file2.SetPosition(file2.getSize()));
    EXPECT_EQ(FILE_SIZE, file2.getSize());
    EXPECT_EQ(FILE_SIZE, file2.Write2(buf.get(), FILE_SIZE));
    EXPECT_EQ(FILE_SIZE*2, file2.getSize());
    EXPECT_TRUE(file2.Close());

    EXPECT_TRUE(file.DirectIOPadding(4097, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(1u, padBefore);
    EXPECT_EQ(4095u, padAfter);

    EXPECT_TRUE(file.DirectIOPadding(FILE_SIZE-1, 4_Ki, padBefore, padAfter));
    EXPECT_EQ(4095u, padBefore);
    EXPECT_EQ(1u, padAfter);

    EXPECT_TRUE(file.Close());
    std::filesystem::remove(std::filesystem::path(file.GetFileName()));
}
#endif

void verifyGrowing(const std::string& dir, const LogDataStore::Config & config, uint32_t minFiles, uint32_t maxFiles) {
    DirectoryHandler tmpDir(dir);
    vespalib::ThreadStackExecutor executor(4);
    DummyFileHeaderContext fileHeaderContext;
    MyTlSyncer tlSyncer;
    {
        LogDataStore datastore(executor, dir, config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        unsigned int seed = 383451;
        char buffer[12000];
        SerialNum lastSyncToken(0);
        std::minstd_rand rand_gen(seed);
        for (size_t i(0); i < sizeof(buffer); i++) {
            buffer[i] = rand_gen() & 0xff;
        }

        for (size_t i(1); i < 10000; i++) {
            long r = rand_gen()%10000;
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
        datastore.compactBloat(30000);
        datastore.remove(31000, 0);
        checkStats(datastore, 31000, 30000);
        EXPECT_LE(minFiles, datastore.getAllActiveFiles().size());
        EXPECT_GE(maxFiles, datastore.getAllActiveFiles().size());
    }
    {
        LogDataStore datastore(executor, dir, config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        checkStats(datastore, 30000, 30000);
        EXPECT_LE(minFiles, datastore.getAllActiveFiles().size());
        EXPECT_GE(maxFiles, datastore.getAllActiveFiles().size());
    }
}

TEST_F(LogDataStoreTest, testGrowingChunkedBySize)
{
    LogDataStore::Config config;
    config.setMaxFileSize(100000).setMaxBucketSpread(3.0).setMinFileSizeFactor(0.2)
            .compactCompression({CompressionConfig::LZ4})
            .setFileConfig({{CompressionConfig::ZSTD, 9, 60}, 1000});
    // Number of generated files depends on timing
    verifyGrowing(build_testdata() + "/growing1", config, 40, 265);
}

TEST_F(LogDataStoreTest, testGrowingChunkedByNumLids)
{
    LogDataStore::Config config;
    config.setMaxNumLids(1000).setMaxBucketSpread(3.0).setMinFileSizeFactor(0.2)
            .compactCompression({CompressionConfig::LZ4})
            .setFileConfig({{CompressionConfig::ZSTD, 9, 60}, 1000});
    verifyGrowing(build_testdata() + "/growing2", config,10, 10);
}

void fetchAndTest(IDataStore & datastore, uint32_t lid, const void *a, size_t sz)
{
    vespalib::DataBuffer buf;
    EXPECT_EQ(static_cast<ssize_t>(sz), datastore.read(lid, buf));
    EXPECT_EQ(buf.getDataLen(), sz);
    EXPECT_TRUE(vespalib::memcmp_safe(a, buf.getData(), sz) == 0);
}

TEST_F(LogDataStoreTest, testTruncatedIdxFile)
{
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1);
    MyTlSyncer tlSyncer;
    {
        // Files comes from the 'growing test'.
        LogDataStore datastore(executor, source_testdata(), config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        EXPECT_EQ(354ul, datastore.lastSyncToken());
    }
    const char * magic = "mumbo jumbo";
    auto bug_7257706_truncated = build_testdata() + "/bug-7257706-truncated";
    {
        std::filesystem::copy(source_testdata(), bug_7257706_truncated);
        std::filesystem::resize_file(std::filesystem::path(bug_7257706_truncated + "/1422358701368384000.idx"), 3830);
        LogDataStore datastore(executor, bug_7257706_truncated, config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        EXPECT_EQ(331ul, datastore.lastSyncToken());
        datastore.write(332, 7, magic, strlen(magic));
        datastore.write(333, 8, magic, strlen(magic));
        datastore.flush(datastore.initFlush(334));
    }
    {
        LogDataStore datastore(executor, bug_7257706_truncated, config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        EXPECT_EQ(334ul, datastore.lastSyncToken());
    }
    if (!HasFailure()) {
        std::filesystem::remove_all(bug_7257706_truncated);
    }
}

TEST_F(LogDataStoreTest, testThatEmptyIdxFilesAndDanglingDatFilesAreRemoved)
{
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1);
    MyTlSyncer tlSyncer;
    auto dangling_test = build_testdata() + "/dangling-test";
    std::filesystem::copy(source_testdata(), dangling_test);
    std::filesystem::copy(TEST_PATH("dangling"), dangling_test);
    LogDataStore datastore(executor, dangling_test, config,
                           GrowStrategy(), TuneFileSummary(),
                           fileHeaderContext, tlSyncer, nullptr);
    EXPECT_EQ(354ul, datastore.lastSyncToken());
    EXPECT_EQ(4096u + 480u, datastore.getDiskHeaderFootprint());
    EXPECT_EQ(datastore.getDiskHeaderFootprint() + 94016u, datastore.getDiskFootprint());
    if (!HasFailure()) {
        std::filesystem::remove_all(dangling_test);
    }
}

namespace {

class CopyFileChunk {
    std::string _source_dir;
    std::string _destination_dir;
public:
    CopyFileChunk(const std::string& source_dir, const std::string& destination_dir);
    ~CopyFileChunk();
    void copy(const std::string& source_basename, const std::string& destination_basename);
};

CopyFileChunk::CopyFileChunk(const std::string& source_dir, const std::string& destination_dir)
    : _source_dir(source_dir),
      _destination_dir(destination_dir)
{
}

CopyFileChunk::~CopyFileChunk() = default;

void
CopyFileChunk::copy(const std::string& source_basename, const std::string& destination_basename)
{
    auto source_name = _source_dir + "/" + source_basename;
    auto destination_name = _destination_dir + "/" + destination_basename;
    std::filesystem::copy_file(source_name + ".dat", destination_name + ".dat");
    std::filesystem::copy_file(source_name + ".idx", destination_name + ".idx");
}

}

TEST_F(LogDataStoreTest, testThatIncompleteCompactedFilesAreRemoved)
{
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1);
    MyTlSyncer tlSyncer;
    auto incompletecompact_test = build_testdata() + "/incompletecompact-test";
    std::filesystem::copy(source_testdata(), incompletecompact_test);
    std::string source_basename = "1422358701368384000";
    CopyFileChunk cfc(source_testdata(), incompletecompact_test);
    cfc.copy(source_basename, "2000000000000000000");
    cfc.copy(source_basename, "2000000000000000001");
    cfc.copy(source_basename, "2422358701368384000");
    LogDataStore datastore(executor, incompletecompact_test, config,
                           GrowStrategy(), TuneFileSummary(),
                           fileHeaderContext, tlSyncer, nullptr);
    EXPECT_EQ(354ul, datastore.lastSyncToken());
    EXPECT_EQ(3*(4096u + 480u), datastore.getDiskHeaderFootprint());
    LogDataStore::NameIdSet files = datastore.getAllActiveFiles();
    EXPECT_EQ(3u, files.size());
    EXPECT_TRUE(files.find(FileChunk::NameId(1422358701368384000)) != files.end());
    EXPECT_TRUE(files.find(FileChunk::NameId(2000000000000000000)) != files.end());
    EXPECT_TRUE(files.find(FileChunk::NameId(2422358701368384000)) != files.end());
    if (!HasFailure()) {
        std::filesystem::remove_all(incompletecompact_test);
    }
}

class VisitStore {
public:
    VisitStore() :
        _myDir("visitcache"),
        _config(),
        _fileHeaderContext(),
        _executor(1),
        _tlSyncer(),
        _datastore(_executor, _myDir.getDir(), _config, GrowStrategy(),
                   TuneFileSummary(), _fileHeaderContext, _tlSyncer, nullptr)
    { }
    ~VisitStore();
    IDataStore & getStore() { return _datastore; }
private:
    DirectoryHandler              _myDir;
    LogDataStore::Config          _config;
    DummyFileHeaderContext        _fileHeaderContext;
    vespalib::ThreadStackExecutor _executor;
    MyTlSyncer                    _tlSyncer;
    LogDataStore                  _datastore;
};

VisitStore::~VisitStore() =default;

TEST_F(LogDataStoreTest, test_visit_cache_does_not_cache_empty_ones_and_is_able_to_access_some_backing_store)
{
    const char * A7 = "aAaAaAa";
    VisitStore store;
    IDataStore & datastore = store.getStore();

    VisitCache visitCache(datastore, 100000, CompressionConfig::Type::LZ4);
    EXPECT_EQ(12u, visitCache.read({1}).bytesAllocated());
    EXPECT_TRUE(visitCache.read({1}).empty());
    datastore.write(1,1, A7, 7);
    EXPECT_EQ(12u, visitCache.read({2}).bytesAllocated());
    CompressedBlobSet cbs = visitCache.read({1});
    EXPECT_FALSE(cbs.empty());
    EXPECT_EQ(19u, cbs.bytesAllocated());
    BlobSet bs(cbs.getBlobSet());
    EXPECT_EQ(7u, bs.get(1).size());
    EXPECT_EQ(0, strncmp(A7, bs.get(1).c_str(), 7));
    datastore.write(2,2, A7, 7);
    datastore.write(3,3, A7, 7);
    datastore.write(4,4, A7, 7);
    visitCache.remove(1);
    EXPECT_EQ(2u, visitCache.read({1,3}).getBlobSet().getPositions().size());
    EXPECT_EQ(2u, visitCache.read({2,4,5}).getBlobSet().getPositions().size());
    datastore.remove(5, 3);
    EXPECT_EQ(2u, visitCache.read({1,3}).getBlobSet().getPositions().size());
    visitCache.remove(3);
    EXPECT_EQ(1u, visitCache.read({1,3}).getBlobSet().getPositions().size());
}

using std::string;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using vespalib::asciistream;
using index::DummyFileHeaderContext;

namespace {
const string doc_type_name = "test";
const string header_name = doc_type_name + ".header";
const string body_name = doc_type_name + ".body";

document::config::DocumenttypesConfig
makeDocTypeRepoConfig()
{
    const int32_t doc_type_id = 787121340;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id,
                     doc_type_name,
                     document::config_builder::Struct(header_name),
                     document::config_builder::Struct(body_name).
                     addField("main", DataType::T_STRING).
                     addField("extra", DataType::T_STRING));
    return builder.config();
}


Document::UP
makeDoc(const DocumentTypeRepo &repo, uint32_t i, bool extra_field, size_t numReps=0)
{
    asciistream idstr;
    idstr << "id:test:test:: " << i;
    DocumentId id(idstr.view());
    const DocumentType *docType = repo.getDocumentType(doc_type_name);
    Document::UP doc(new Document(repo, *docType, id));
    assert(doc.get());
    asciistream mainstr;
    mainstr << "static text" << i << " body something";
    for (uint32_t j = 0; j < 10+numReps; ++j) {
        mainstr << (j + i * 1000) << " ";
    }
    mainstr << " and end field";
    doc->setValue("main", StringFieldValue::make(mainstr.view()));
    if (extra_field) {
        doc->setValue("extra", StringFieldValue::make("foo"));
    }
    return doc;
}

}

class VisitCacheStore {
public:
    using UpdateStrategy=DocumentStore::Config::UpdateStrategy;
    VisitCacheStore(UpdateStrategy strategy);
    ~VisitCacheStore();
    IDocumentStore & getStore() { return *_datastore; }
    void write(uint32_t id) {
        write(id, 0);
    }
    void write(uint32_t lid, uint32_t numReps) {
        write(lid, makeDoc(_repo, lid, true, numReps));
    }
    void rewrite(uint32_t id) {
        write(id, makeDoc(_repo, id, false));
    }
    void write(uint32_t id, Document::UP doc) {
        getStore().write(_serial++, id, *doc);
        _inserted[id] = std::move(doc);
    }
    void remove(uint32_t id) {
        getStore().remove(_serial++, id);
        _inserted.erase(id);
    }
    void verifyRead(uint32_t id) {
        verifyDoc(*_datastore->read(id, _repo), id);
    }
    void read(uint32_t id) {
        *_datastore->read(id, _repo);
    }
    void verifyDoc(const Document & doc, uint32_t id) {
        EXPECT_TRUE(doc == *_inserted[id]);
    }
    void verifyVisit(const std::vector<uint32_t> & lids, bool allowCaching) {
        verifyVisit(lids, lids, allowCaching);
    }
    void verifyVisit(const std::vector<uint32_t> & lids, const std::vector<uint32_t> & expected, bool allowCaching) {
        VerifyVisitor vv(*this, expected, allowCaching);
        _datastore->visit(lids, _repo, vv);
    }
    void recreate();

private:
    class VerifyVisitor : public IDocumentVisitor {
    public:
        VerifyVisitor(VisitCacheStore & vcs, std::vector<uint32_t> lids, bool allowCaching);
        ~VerifyVisitor() override;
        void visit(uint32_t lid, Document::UP doc) override {
            EXPECT_TRUE(_expected.find(lid) != _expected.end());
            EXPECT_TRUE(_actual.find(lid) == _actual.end());
            _actual.insert(lid);
            _vcs.verifyDoc(*doc, lid);
        }
        bool allowVisitCaching() const override { return _allowVisitCaching; }
    private:
        VisitCacheStore               &_vcs;
        vespalib::hash_set<uint32_t>   _expected;
        vespalib::hash_set<uint32_t>   _actual;
        bool                           _allowVisitCaching;
    };
    DirectoryHandler                   _myDir;
    document::DocumentTypeRepo         _repo;
    LogDocumentStore::Config           _config;
    DummyFileHeaderContext             _fileHeaderContext;
    vespalib::ThreadStackExecutor      _executor;
    MyTlSyncer                         _tlSyncer;
    std::unique_ptr<LogDocumentStore>  _datastore;
    std::map<uint32_t, Document::UP>   _inserted;
    SerialNum                          _serial;
};

VisitCacheStore::VerifyVisitor::VerifyVisitor(VisitCacheStore & vcs, std::vector<uint32_t> lids, bool allowCaching)
    : _vcs(vcs), _expected(), _actual(), _allowVisitCaching(allowCaching)
{
    for (uint32_t lid : lids) {
        _expected.insert(lid);
    }
}
VisitCacheStore::VerifyVisitor::~VerifyVisitor() {
    EXPECT_EQ(_expected.size(), _actual.size());
}


VisitCacheStore::VisitCacheStore(UpdateStrategy strategy) :
    _myDir("visitcache"),
    _repo(makeDocTypeRepoConfig()),
    _config(DocumentStore::Config(CompressionConfig::LZ4, 1000000).updateStrategy(strategy),
            LogDataStore::Config().setMaxFileSize(50000).setMaxBucketSpread(3.0)
                    .setFileConfig(WriteableFileChunk::Config(CompressionConfig(), 16_Ki))),
    _fileHeaderContext(),
    _executor(1),
    _tlSyncer(),
    _datastore(std::make_unique<LogDocumentStore>(_executor, _myDir.getDir(), _config, GrowStrategy(),
                                                  TuneFileSummary(), _fileHeaderContext, _tlSyncer, nullptr)),
    _inserted(),
    _serial(1)
{ }

VisitCacheStore::~VisitCacheStore() = default;

void
VisitCacheStore::recreate() {
    _datastore->flush(_datastore->initFlush(_datastore->tentativeLastSyncToken()));
    _datastore.reset();
    _datastore = std::make_unique<LogDocumentStore>(_executor, _myDir.getDir(), _config, GrowStrategy(),
                                                    TuneFileSummary(), _fileHeaderContext, _tlSyncer, nullptr);

}

void
verifyCacheStats(CacheStats cs, size_t hits, size_t misses, size_t elements, size_t memory_used, std::string_view label) {
    SCOPED_TRACE(label);
    EXPECT_EQ(hits, cs.hits);
    EXPECT_EQ(misses, cs.misses);
    EXPECT_EQ(elements, cs.elements);
    EXPECT_LE(memory_used,  cs.memory_used + 20);  // We allow +- 20 as visitorder and hence compressability is non-deterministic.
    EXPECT_GE(memory_used+20,  cs.memory_used);
}

TEST_F(LogDataStoreTest, Control_static_memory_usage)
{
    VisitCacheStore vcs(DocumentStore::Config::UpdateStrategy::UPDATE);
    IDocumentStore &ds = vcs.getStore();
    vespalib::MemoryUsage usage = ds.getMemoryUsage();
    constexpr size_t mutex_size = sizeof(std::mutex) * 2 * (113 + 1); // sizeof(std::mutex) is platform dependent
    constexpr size_t string_size = sizeof(std::string);
    EXPECT_EQ(74476 + mutex_size + 3 * string_size, usage.allocatedBytes());
    EXPECT_EQ(752u + mutex_size + 3 * string_size, usage.usedBytes());
}

TEST_F(LogDataStoreTest, test_the_update_cache_strategy)
{
    VisitCacheStore vcs(DocumentStore::Config::UpdateStrategy::UPDATE);
    IDocumentStore & ds = vcs.getStore();
    for (size_t i(1); i <= 10; i++) {
        vcs.write(i);
    }
    verifyCacheStats(ds.getCacheStats(), 0, 0, 0, 28, "initial 10 writes");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 1, 241, "read 7");
    vcs.write(8);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 1, 241, "second write 8");
    vcs.write(7, 17);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 1, 302, "second write 7");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 1, 1, 1, 302, "second read 7");
    vcs.remove(8);
    verifyCacheStats(ds.getCacheStats(), 1, 1, 1, 302, "remove 8");
    vcs.remove(7);
    verifyCacheStats(ds.getCacheStats(), 1, 1, 0, 28, "remove 7");
    vcs.write(7);
    verifyCacheStats(ds.getCacheStats(), 1, 1, 0, 28, "third 7");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 1, 2, 1, 241, "third read 7");
    vcs.write(7, 17);
    verifyCacheStats(ds.getCacheStats(), 1, 2, 1, 302, "fourth write 7");
    vcs.recreate();
    IDocumentStore & ds2 = vcs.getStore();
    vcs.verifyRead(7);
    verifyCacheStats(ds2.getCacheStats(), 0, 1, 1, 302, "fourth read 7");
}

TEST_F(LogDataStoreTest, test_the_invalidate_cache_strategy)
{
    VisitCacheStore vcs(DocumentStore::Config::UpdateStrategy::INVALIDATE);
    IDocumentStore & ds = vcs.getStore();
    for (size_t i(1); i <= 10; i++) {
        vcs.write(i);
    }
    verifyCacheStats(ds.getCacheStats(), 0, 0, 0, 28, "initial 10 writes");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 1, 241, "read 7");
    vcs.write(8);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 1, 241, "second write 8");
    vcs.write(7);
    verifyCacheStats(ds.getCacheStats(), 0, 1, 0, 28, "second write 7");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 0, 2, 1, 241, "second read 7");
    vcs.remove(8);
    verifyCacheStats(ds.getCacheStats(), 0, 2, 1, 241, "remove 8");
    vcs.remove(7);
    verifyCacheStats(ds.getCacheStats(), 0, 2, 0, 28, "remove 7");
    vcs.write(7);
    verifyCacheStats(ds.getCacheStats(), 0, 2, 0, 28, "third write 7");
    vcs.verifyRead(7);
    verifyCacheStats(ds.getCacheStats(), 0, 3, 1, 241, "third read 7");
}

TEST_F(LogDataStoreTest, test_that_the_integrated_visit_cache_works)
{
    VisitCacheStore vcs(DocumentStore::Config::UpdateStrategy::INVALIDATE);
    IDocumentStore & ds = vcs.getStore();
    for (size_t i(1); i <= 100; i++) {
        vcs.write(i);
    }
    verifyCacheStats(ds.getCacheStats(), 0, 0, 0, 28, "initial 100 writes");

    for (size_t i(1); i <= 100; i++) {
        vcs.verifyRead(i);
    }
    constexpr size_t BASE_SZ = 20602;
    verifyCacheStats(ds.getCacheStats(), 0, 100, 100, BASE_SZ, "first read 1..100 read");
    for (size_t i(1); i <= 100; i++) {
        vcs.verifyRead(i);
    }
    verifyCacheStats(ds.getCacheStats(), 100, 100, 100, BASE_SZ, "second read 1..100 read"); // From the individual cache.

    vcs.verifyVisit({7,9,17,19,67,88}, false);
    verifyCacheStats(ds.getCacheStats(), 100, 100, 100, BASE_SZ, "first visit");
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    verifyCacheStats(ds.getCacheStats(), 100, 101, 101, BASE_SZ + 16l, "second visit");
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 101, 101, BASE_SZ + 16, "third visit");
    vcs.rewrite(8);
    verifyCacheStats(ds.getCacheStats(), 101, 101, 100, BASE_SZ - 197, "rewrite 8"); // From the individual cache.
    vcs.rewrite(7);
    verifyCacheStats(ds.getCacheStats(), 101, 101, 98, BASE_SZ - 166, "rewrite 7"); // From the both caches.
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 102, 99, BASE_SZ - 410, "fourth visit");
    vcs.verifyVisit({7,9,17,19,67,88,89}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 103, 99, BASE_SZ - 406, "fifth visit");
    vcs.rewrite(17);
    verifyCacheStats(ds.getCacheStats(), 101, 103, 97, BASE_SZ - 391, "rewrite 17");
    vcs.verifyVisit({7,9,17,19,67,88,89}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 104, 98, BASE_SZ - 611, "sixth visit");
    vcs.remove(17);
    verifyCacheStats(ds.getCacheStats(), 101, 104, 97, BASE_SZ - 391, "remove 17");
    vcs.verifyVisit({7,9,17,19,67,88,89}, {7,9,19,67,88,89}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 105, 98, BASE_SZ - 611, "seventh visit");

    vcs.verifyVisit({41, 42}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 106, 99, BASE_SZ - 611, "eight visit");
    vcs.verifyVisit({43, 44}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 107, 100, BASE_SZ - 611, "ninth visit");
    vcs.verifyVisit({41, 42, 43, 44}, true);
    verifyCacheStats(ds.getCacheStats(), 101, 108, 99, BASE_SZ - 611, "tenth visit");
}

TEST_F(LogDataStoreTest, testWriteRead)
{
    auto empty = build_testdata() + "/empty";
    const char * bufA = "aaaaaaaaaaaaaaaaaaaaa";
    const char * bufB = "bbbbbbbbbbbbbbbb";
    const vespalib::ConstBufferRef a[2] = { vespalib::ConstBufferRef(bufA, strlen(bufA)), vespalib::ConstBufferRef(bufB, strlen(bufB))};
    LogDataStore::Config config;
    {
        std::filesystem::create_directory(std::filesystem::path(empty));
        DummyFileHeaderContext fileHeaderContext;
        vespalib::ThreadStackExecutor executor(1);
        MyTlSyncer tlSyncer;
        LogDataStore datastore(executor, empty, config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        ASSERT_TRUE(datastore.lastSyncToken() == 0);
        size_t headerFootprint = datastore.getDiskHeaderFootprint();
        EXPECT_LT(0u, headerFootprint);
        EXPECT_EQ(datastore.getDiskFootprint(), headerFootprint);
        EXPECT_EQ(datastore.getDiskBloat(), 0ul);
        EXPECT_EQ(datastore.getMaxSpreadAsBloat(), 0ul);
        datastore.write(1, 0, a[0].c_str(), a[0].size());
        fetchAndTest(datastore, 0, a[0].c_str(), a[0].size());
        datastore.write(2, 0, a[1].c_str(), a[1].size());
        fetchAndTest(datastore, 0, a[1].c_str(), a[1].size());
        fetchAndTest(datastore, 1, nullptr, 0);
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
        EXPECT_EQ(datastore.getDiskFootprint(),
                     2711ul + headerFootprint);
        EXPECT_EQ(datastore.getDiskBloat(), 0ul);
        EXPECT_EQ(datastore.getMaxSpreadAsBloat(), 0ul);
        datastore.flush(datastore.initFlush(lastSyncToken));
    }
    {
        DummyFileHeaderContext fileHeaderContext;
        vespalib::ThreadStackExecutor executor(1);
        MyTlSyncer tlSyncer;
        LogDataStore datastore(executor, empty, config,
                               GrowStrategy(), TuneFileSummary(),
                               fileHeaderContext, tlSyncer, nullptr);
        size_t headerFootprint = datastore.getDiskHeaderFootprint();
        EXPECT_LT(0u, headerFootprint);
        EXPECT_EQ(4944ul + headerFootprint, datastore.getDiskFootprint());
        EXPECT_EQ(0ul, datastore.getDiskBloat());
        EXPECT_EQ(0ul, datastore.getMaxSpreadAsBloat());

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

        EXPECT_EQ(7594ul + headerFootprint, datastore.getDiskFootprint());
        EXPECT_EQ(0ul, datastore.getDiskBloat());
        EXPECT_EQ(0ul, datastore.getMaxSpreadAsBloat());
    }
    if (!HasFailure()) {
        std::filesystem::remove_all(std::filesystem::path(empty));
    }
}

TEST_F(LogDataStoreTest, requireThatFlushTimeIsAvailableAfterFlush)
{
    DirectoryHandler testDir("flushtime");
    vespalib::system_time before(vespalib::system_clock::now());
    DummyFileHeaderContext fileHeaderContext;
    LogDataStore::Config config;
    vespalib::ThreadStackExecutor executor(1);
    MyTlSyncer tlSyncer;
    LogDataStore store(executor, testDir.getDir(), config, GrowStrategy(),
                       TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
    EXPECT_EQ(0s, std::chrono::duration_cast<std::chrono::seconds>(store.getLastFlushTime().time_since_epoch()));
    uint64_t flushToken = store.initFlush(5);
    EXPECT_EQ(5u, flushToken);
    store.flush(flushToken);
    vespalib::system_time after(vespalib::system_clock::now());
    // the file name of the dat file is 'magic', using the clock instead of stating the file
    EXPECT_LE(before, store.getLastFlushTime());
    EXPECT_GE(after, store.getLastFlushTime());
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

TEST_F(LogDataStoreTest, testBucketDensityComputer)
{
    DummyBucketizer bucketizer(100);
    BucketDensityComputer bdc(&bucketizer);
    vespalib::GenerationHandler::Guard guard = bdc.getGuard();
    EXPECT_EQ(0u, bdc.getNumBuckets());
    bdc.recordLid(guard, 1, 1);
    EXPECT_EQ(1u, bdc.getNumBuckets());
    bdc.recordLid(guard, 2, 1);
    EXPECT_EQ(2u, bdc.getNumBuckets());
    bdc.recordLid(guard, 3, 1);
    EXPECT_EQ(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 2, 1);
    EXPECT_EQ(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 4, 0);
    EXPECT_EQ(3u, bdc.getNumBuckets());
    bdc.recordLid(guard, 4, 1);
    EXPECT_EQ(4u, bdc.getNumBuckets());

    BucketDensityComputer nonRecording(nullptr);
    guard = nonRecording.getGuard();
    EXPECT_EQ(0u, nonRecording.getNumBuckets());
    nonRecording.recordLid(guard, 1, 1);
    EXPECT_EQ(0u, nonRecording.getNumBuckets());
}

LogDataStore::Config
getBasicConfig(size_t maxFileSize)
{
    return LogDataStore::Config().setMaxFileSize(maxFileSize);
}

std::string
genData(uint32_t lid, size_t numBytes)
{
    assert(numBytes >= 6);
    std::ostringstream oss;
    for (size_t i = 0; i < (numBytes - 6); ++i) {
        oss << 'a';
    }
    oss << std::setw(6) << std::setfill('0') << lid;
    return oss.str();
}

struct Fixture {
    vespalib::ThreadStackExecutor executor;
    search::test::DirectoryHandler dir;
    uint64_t serialNum;
    DummyFileHeaderContext fileHeaderCtx;
    MyTlSyncer tlSyncer;
    LogDataStore store;

    uint64_t nextSerialNum() {
        return serialNum++;
    }

    Fixture(const std::string& dirName,
            bool dirCleanup = true,
            size_t maxFileSize = 4_Ki * 2)
        : executor(1),
          dir(dirName),
          serialNum(0),
          fileHeaderCtx(),
          tlSyncer(),
          store(executor, dirName, getBasicConfig(maxFileSize), GrowStrategy(),
                TuneFileSummary(), fileHeaderCtx, tlSyncer, nullptr)
    {
        dir.cleanup(dirCleanup);
    }
    ~Fixture() {}
    void flush() {
        store.initFlush(serialNum);
        store.flush(serialNum);
    }
    Fixture &write(uint32_t lid, size_t numBytes = 1024) {
        std::string data = genData(lid, numBytes);
        store.write(nextSerialNum(), lid, data.c_str(), data.size());
        return *this;
    }
    uint32_t writeUntilNewChunk(uint32_t startLid) {
        size_t numChunksStart = store.getFileChunkStats().size();
        for (uint32_t lid = startLid; ; ++lid) {
            write(lid);
            if (store.getFileChunkStats().size() > numChunksStart) {
                return lid;
            }
        }
    }
    void compactLidSpace(uint32_t wantedDocIdLimit, std::string_view label) {
        SCOPED_TRACE(label);
        store.compactLidSpace(wantedDocIdLimit);
        assertDocIdLimit(wantedDocIdLimit);
    }
    void assertDocIdLimit(uint32_t expDocIdLimit) {
        EXPECT_EQ(expDocIdLimit, store.getDocIdLimit());
    }
    void assertDocIdLimit(uint32_t expDocIdLimit, std::string_view label) {
        SCOPED_TRACE(label);
        assertDocIdLimit(expDocIdLimit);
    }
    void assertNumChunks(size_t numChunks) {
        EXPECT_EQ(numChunks, store.getFileChunkStats().size());
    }
    void assertDocIdLimitInFileChunks(const std::vector<uint32_t> expLimits) {
        std::vector<uint32_t> actLimits;
        for (const auto &stat : store.getFileChunkStats()) {
            actLimits.push_back(stat.docIdLimit());
        }
        EXPECT_EQ(expLimits, actLimits);
    }
    void assertDocIdLimitInFileChunks(const std::vector<uint32_t> expLimits, std::string_view label) {
        SCOPED_TRACE(label);
        assertDocIdLimitInFileChunks(std::move(expLimits));
    }
    void assertContent(const std::set<uint32_t> &lids, uint32_t docIdLimit, size_t numBytesPerEntry, std::string_view label) {
        SCOPED_TRACE(label);
        for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
            vespalib::DataBuffer buffer;
            size_t size = store.read(lid, buffer);
            if (lids.find(lid) != lids.end()) {
                std::string expData = genData(lid, numBytesPerEntry);
                EXPECT_EQ(expData, std::string(buffer.getData(), buffer.getDataLen()));
                EXPECT_GT(size, 0u);
            } else {
                EXPECT_EQ("", std::string(buffer.getData(), buffer.getDataLen()));
                EXPECT_EQ(0u, size);
            }
        }
    }
};

TEST_F(LogDataStoreTest, require_that_docIdLimit_is_updated_when_inserting_entries)
{
    auto tmp1 = build_testdata() + "/tmp1";
    {
        Fixture f(tmp1, false);
        f.assertDocIdLimit(0, "initial");
        f.write(10);
        f.assertDocIdLimit(11, "write 10");
        f.write(9);
        f.assertDocIdLimit(11, "write 9");
        f.write(11);
        f.assertDocIdLimit(12, "write 11");
        f.assertNumChunks(1);
        f.flush();
    }
    {
        Fixture f(tmp1);
        f.assertDocIdLimit(12, "reload");
    }
}

TEST_F(LogDataStoreTest, require_that_docIdLimit_at_idx_file_creation_time_is_written_to_idx_file_header)
{
    auto tmp2 = build_testdata() + "/tmp2";
    std::vector<uint32_t> expLimits = {std::numeric_limits<uint32_t>::max(),14,104,204};
    {
        Fixture f(tmp2, false);
        f.writeUntilNewChunk(10);
        f.writeUntilNewChunk(100);
        f.writeUntilNewChunk(200);
        f.assertDocIdLimitInFileChunks(expLimits, "writes");
        f.flush();
    }
    {
        Fixture f(tmp2);
        f.assertDocIdLimitInFileChunks(expLimits, "reload");
    }
}

TEST_F(LogDataStoreTest, require_that_lid_space_can_be_compacted_and_entries_from_old_files_skipped_during_load)
{
    auto tmp3 = build_testdata() + "/tmp3";
    {
        Fixture f(tmp3, false);
        f.write(10);
        f.writeUntilNewChunk(100);
        f.write(20);
        f.writeUntilNewChunk(200);
        f.write(30);
        f.assertContent({10,100,101,102,20,200,201,202,30}, 203, 1024, "write 30");

        f.assertDocIdLimit(203);
        f.compactLidSpace(100, "compactLidSpace 100");
        f.assertContent({10,20,30}, 203, 1024, "after compactLidSpace");

        f.writeUntilNewChunk(31);
        f.write(99);
        f.write(300);
        f.assertContent({10,20,30,31,32,33,99,300}, 301, 1024, "write 100");
        f.assertDocIdLimitInFileChunks({std::numeric_limits<uint32_t>::max(),103,203,100});
        f.flush();
    }
    {
        Fixture f(tmp3);
        f.assertContent({10,20,30,31,32,33,99,300}, 301, 1024, "reload");
    }
}

TEST_F(LogDataStoreTest, require_that_getLid_is_protected_by_docIdLimit)
{
    auto tmp4 = build_testdata() + "/tmp4";
    Fixture f(tmp4);
    f.write(1);
    vespalib::GenerationHandler::Guard guard = f.store.getLidReadGuard();
    EXPECT_TRUE(f.store.getLid(guard, 1).valid());
    EXPECT_FALSE(f.store.getLid(guard, 2).valid());
}

TEST_F(LogDataStoreTest, require_that_lid_space_can_be_compacted_and_shrunk)
{
    auto tmp5 = build_testdata() + "/tmp5";
    Fixture f(tmp5);
    f.write(1).write(2);
    EXPECT_FALSE(f.store.canShrinkLidSpace());

    f.compactLidSpace(2, "compactLidSpace 2");
    vespalib::MemoryUsage before = f.store.getMemoryUsage();
    EXPECT_TRUE(f.store.canShrinkLidSpace());
    EXPECT_EQ(8u, f.store.getEstimatedShrinkLidSpaceGain()); // one lid info entry
    f.store.shrinkLidSpace();

    vespalib::MemoryUsage after = f.store.getMemoryUsage();
    EXPECT_LT(after.usedBytes(), before.usedBytes());
    EXPECT_EQ(8u, before.usedBytes() - after.usedBytes());
}

TEST_F(LogDataStoreTest, require_that_lid_space_can_be_increased_after_being_compacted_and_then_shrunk)
{
    auto tmp6 = build_testdata() + "/tmp6";
    Fixture f(tmp6);
    f.write(1).write(3);
    f.compactLidSpace(2, "compactLidSpace 2");
    f.write(2);
    f.assertDocIdLimit(3, "write 2");
    f.store.shrinkLidSpace();
    f.assertDocIdLimit(3, "shrinkLidSpace");
    f.assertContent({1,2}, 3, 1024, "shrinkLidSpace");
}

TEST_F(LogDataStoreTest, require_that_there_is_control_of_static_memory_usage)
{
    auto tmp7= build_testdata() + "/tmp7";
    Fixture f(tmp7);
    vespalib::MemoryUsage usage = f.store.getMemoryUsage();
    EXPECT_EQ(456u + sizeof(LogDataStore::NameIdSet) + sizeof(std::mutex) + sizeof(std::string), sizeof(LogDataStore));
    EXPECT_EQ(73916u + 3 * sizeof(std::string), usage.allocatedBytes());
    EXPECT_EQ(192u + 3 * sizeof(std::string), usage.usedBytes());
}

TEST_F(LogDataStoreTest, require_that_lid_space_can_be_shrunk_only_after_read_guards_are_deleted)
{
    auto tmp8 = build_testdata() + "/tmp8";
    Fixture f(tmp8);
    f.write(1).write(2);
    EXPECT_FALSE(f.store.canShrinkLidSpace());
    {
        vespalib::GenerationHandler::Guard guard = f.store.getLidReadGuard();
        f.compactLidSpace(2, "compactLidSpace 2");
        f.write(1); // trigger remove of old generations
        EXPECT_FALSE(f.store.canShrinkLidSpace());
        EXPECT_EQ(0u, f.store.getEstimatedShrinkLidSpaceGain());
    }
    f.write(1); // trigger remove of old generations
    EXPECT_TRUE(f.store.canShrinkLidSpace());
    EXPECT_EQ(8u, f.store.getEstimatedShrinkLidSpaceGain());
}

LogDataStore::NameIdSet create(std::vector<size_t> list) {
    LogDataStore::NameIdSet l;
    for (size_t id : list) {
        l.emplace(id);
    }
    return l;
}

TEST_F(LogDataStoreTest, require_that_findIncompleteCompactedFiles_does_expected_filtering)
{
    EXPECT_TRUE(LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,202,204})).empty());
    LogDataStore::NameIdSet toRemove = LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,201,204}));
    EXPECT_EQ(1u, toRemove.size());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(201)) != toRemove.end());
    toRemove = LogDataStore::findIncompleteCompactedFiles(create({1,2,4,100,200,201,204,205}));
    EXPECT_EQ(3u, toRemove.size());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(2)) != toRemove.end());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(201)) != toRemove.end());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(205)) != toRemove.end());

    VESPA_EXPECT_EXCEPTION((void) LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,201,202,204})).empty(),
                           vespalib::IllegalStateException, "3 consecutive files {200, 201, 202}. Impossible");

}

TEST_F(LogDataStoreTest, require_that_config_equality_operator_detects_inequality)
{
    using C = LogDataStore::Config;
    EXPECT_TRUE(C() == C());
    EXPECT_FALSE(C() == C().setMaxFileSize(1));
    EXPECT_FALSE(C() == C().setMaxBucketSpread(0.3));
    EXPECT_FALSE(C() == C().setMinFileSizeFactor(0.3));
    EXPECT_FALSE(C() == C().setFileConfig(WriteableFileChunk::Config({}, 70)));
    EXPECT_FALSE(C() == C().compactCompression({CompressionConfig::ZSTD}));
}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    DummyFileHeaderContext::setCreator("logdatastore_test");
    return RUN_ALL_TESTS();
}
