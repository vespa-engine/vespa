// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/docstore/chunkformats.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/docstore/storebybucket.h>
#include <vespa/searchlib/docstore/visitcache.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <iomanip>

using document::BucketId;
using namespace search::docstore;
using namespace search;
using namespace vespalib::alloc;
using search::index::DummyFileHeaderContext;

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

TEST("test that DirectIOPadding works accordng to spec") {
    constexpr ssize_t FILE_SIZE = 4096*3;
    FastOS_File file("directio.test");
    file.EnableDirectIO();
    EXPECT_TRUE(file.OpenReadWrite());
    Alloc buf(Alloc::alloc(FILE_SIZE, MemoryAllocator::HUGEPAGE_SIZE, 4096));
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
    LogDataStore::Config config; //(100000, 0.1, 3.0, 0.2, 8, true, CompressionConfig::LZ4,
                                // WriteableFileChunk::Config(CompressionConfig(CompressionConfig::LZ4, 9, 60), 1000));
    config.setMaxFileSize(100000).setMaxDiskBloatFactor(0.1).setMaxBucketSpread(3.0).setMinFileSizeFactor(0.2)
            .compact2ActiveFile(true).compactCompression({CompressionConfig::LZ4})
            .setFileConfig({{CompressionConfig::LZ4, 9, 60}, 1000});
    vespalib::ThreadStackExecutor executor(8, 128*1024);
    DummyFileHeaderContext fileHeaderContext;
    MyTlSyncer tlSyncer;
    {
        LogDataStore datastore(executor, "growing", config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
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
        LogDataStore datastore(executor, "growing", config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
        checkStats(datastore, 30000, 30000);
    }

    FastOS_File::EmptyAndRemoveDirectory("growing");
}

class TmpDirectory {
public:
    TmpDirectory(const vespalib::string & dir) : _dir(dir)
    {
        FastOS_File::EmptyAndRemoveDirectory(_dir.c_str());
        ASSERT_TRUE(FastOS_File::MakeDirectory(_dir.c_str()));
    }
    ~TmpDirectory() {
        FastOS_File::EmptyAndRemoveDirectory(_dir.c_str());
    }
    const vespalib::string & getDir() const { return _dir; }
private:
    vespalib::string _dir;
};

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
    vespalib::ThreadStackExecutor executor(1, 128*1024);
    MyTlSyncer tlSyncer;
    {
        // Files comes from the 'growing test'.
        LogDataStore datastore(executor, TEST_PATH("bug-7257706"), config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(354ul, datastore.lastSyncToken());
    }
    const char * magic = "mumbo jumbo";
    {
        LogDataStore datastore(executor, "bug-7257706-truncated", config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(331ul, datastore.lastSyncToken());
        datastore.write(332, 7, magic, strlen(magic));
        datastore.write(333, 8, magic, strlen(magic));
        datastore.flush(datastore.initFlush(334));
    }
    {
        LogDataStore datastore(executor, "bug-7257706-truncated", config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, NULL);
        EXPECT_EQUAL(334ul, datastore.lastSyncToken());
    }
}

TEST("testThatEmptyIdxFilesAndDanglingDatFilesAreRemoved") {
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1, 128*1024);
    MyTlSyncer tlSyncer;
    LogDataStore datastore(executor, "dangling-test", config,
                           GrowStrategy(), TuneFileSummary(),
                           fileHeaderContext, tlSyncer, NULL);
    EXPECT_EQUAL(354ul, datastore.lastSyncToken());
    EXPECT_EQUAL(4096u + 480u, datastore.getDiskHeaderFootprint());
    EXPECT_EQUAL(datastore.getDiskHeaderFootprint() + 94016u, datastore.getDiskFootprint());
}

TEST("testThatIncompleteCompactedFilesAreRemoved") {
    LogDataStore::Config config;
    DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1, 128*1024);
    MyTlSyncer tlSyncer;
    LogDataStore datastore(executor, "incompletecompact-test", config,
                           GrowStrategy(), TuneFileSummary(),
                           fileHeaderContext, tlSyncer, NULL);
    EXPECT_EQUAL(354ul, datastore.lastSyncToken());
    EXPECT_EQUAL(3*(4096u + 480u), datastore.getDiskHeaderFootprint());
    LogDataStore::NameIdSet files = datastore.getAllActiveFiles();
    EXPECT_EQUAL(3u, files.size());
    EXPECT_TRUE(files.find(FileChunk::NameId(1422358701368384000)) != files.end());
    EXPECT_TRUE(files.find(FileChunk::NameId(2000000000000000000)) != files.end());
    EXPECT_TRUE(files.find(FileChunk::NameId(2422358701368384000)) != files.end());
}

class VisitStore {
public:
    VisitStore() :
        _myDir("visitcache"),
        _config(),
        _fileHeaderContext(),
        _executor(1, 128*1024),
        _tlSyncer(),
        _datastore(_executor, _myDir.getDir(), _config, GrowStrategy(),
                   TuneFileSummary(), _fileHeaderContext, _tlSyncer, NULL)
    { }
    ~VisitStore();
    IDataStore & getStore() { return _datastore; }
private:
    TmpDirectory                  _myDir;
    LogDataStore::Config          _config;
    DummyFileHeaderContext        _fileHeaderContext;
    vespalib::ThreadStackExecutor _executor;
    MyTlSyncer                    _tlSyncer;
    LogDataStore                  _datastore;
};

VisitStore::~VisitStore() {
}

TEST("test visit cache does not cache empty ones and is able to access some backing store.") {
    const char * A7 = "aAaAaAa";
    VisitStore store;
    IDataStore & datastore = store.getStore();

    VisitCache visitCache(datastore, 100000, CompressionConfig::Type::LZ4);
    EXPECT_EQUAL(0u, visitCache.read({1}).size());
    EXPECT_TRUE(visitCache.read({1}).empty());
    datastore.write(1,1, A7, 7);
    EXPECT_EQUAL(0u, visitCache.read({2}).size());
    CompressedBlobSet cbs = visitCache.read({1});
    EXPECT_FALSE(cbs.empty());
    EXPECT_EQUAL(19u, cbs.size());
    BlobSet bs(cbs.getBlobSet());
    EXPECT_EQUAL(7u, bs.get(1).size());
    EXPECT_EQUAL(0, strncmp(A7, bs.get(1).c_str(), 7));
    datastore.write(2,2, A7, 7);
    datastore.write(3,3, A7, 7);
    datastore.write(4,4, A7, 7);
    visitCache.remove(1);
    EXPECT_EQUAL(2u, visitCache.read({1,3}).getBlobSet().getPositions().size());
    EXPECT_EQUAL(2u, visitCache.read({2,4,5}).getBlobSet().getPositions().size());
    datastore.remove(5, 3);
    EXPECT_EQUAL(2u, visitCache.read({1,3}).getBlobSet().getPositions().size());
    visitCache.remove(3);
    EXPECT_EQUAL(1u, visitCache.read({1,3}).getBlobSet().getPositions().size());
}

using vespalib::string;
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

document::DocumenttypesConfig
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
makeDoc(const DocumentTypeRepo &repo, uint32_t i, bool extra_field)
{
    asciistream idstr;
    idstr << "id:test:test:: " << i;
    DocumentId id(idstr.str());
    const DocumentType *docType = repo.getDocumentType(doc_type_name);
    Document::UP doc(new Document(*docType, id));
    ASSERT_TRUE(doc.get());
    asciistream mainstr;
    mainstr << "static text" << i << " body something";
    for (uint32_t j = 0; j < 10; ++j) {
        mainstr << (j + i * 1000) << " ";
    }
    mainstr << " and end field";
    doc->set("main", mainstr.c_str());
    if (extra_field) {
        doc->set("extra", "foo");
    }

    return doc;
}

}

class VisitCacheStore {
public:
    VisitCacheStore();
    ~VisitCacheStore();
    IDocumentStore & getStore() { return _datastore; }
    void write(uint32_t id) {
        write(id, makeDoc(_repo, id, true));
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
        verifyDoc(*_datastore.read(id, _repo), id);
    }
    void verifyDoc(const Document & doc, uint32_t id) {
        EXPECT_TRUE(doc == *_inserted[id]);
    }
    void verifyVisit(const std::vector<uint32_t> & lids, bool allowCaching) {
        verifyVisit(lids, lids, allowCaching);
    }
    void verifyVisit(const std::vector<uint32_t> & lids, const std::vector<uint32_t> & expected, bool allowCaching) {
        VerifyVisitor vv(*this, expected, allowCaching);
        _datastore.visit(lids, _repo, vv);
    }
private:
    class VerifyVisitor : public IDocumentVisitor {
    public:
        VerifyVisitor(VisitCacheStore & vcs, std::vector<uint32_t> lids, bool allowCaching);
        ~VerifyVisitor();
        void visit(uint32_t lid, Document::UP doc) override {
            EXPECT_TRUE(_expected.find(lid) != _expected.end());
            EXPECT_TRUE(_actual.find(lid) == _actual.end());
            _actual.insert(lid);
            _vcs.verifyDoc(*doc, lid);
        }
        bool allowVisitCaching() const override { return _allowVisitCaching; }
    private:
        VisitCacheStore              &_vcs;
        vespalib::hash_set<uint32_t>  _expected;
        vespalib::hash_set<uint32_t>  _actual;
        bool                          _allowVisitCaching;
    };
    TmpDirectory                     _myDir;    
    document::DocumentTypeRepo       _repo;
    LogDocumentStore::Config         _config;
    DummyFileHeaderContext           _fileHeaderContext;
    vespalib::ThreadStackExecutor    _executor;
    MyTlSyncer                       _tlSyncer;
    LogDocumentStore                 _datastore;
    std::map<uint32_t, Document::UP> _inserted;
    SerialNum                        _serial;
};

VisitCacheStore::VerifyVisitor::VerifyVisitor(VisitCacheStore & vcs, std::vector<uint32_t> lids, bool allowCaching)
        : _vcs(vcs), _expected(), _actual(), _allowVisitCaching(allowCaching)
{
    for (uint32_t lid : lids) {
        _expected.insert(lid);
    }
}
VisitCacheStore::VerifyVisitor::~VerifyVisitor() {
    EXPECT_EQUAL(_expected.size(), _actual.size());
}

VisitCacheStore::VisitCacheStore() :
    _myDir("visitcache"),
    _repo(makeDocTypeRepoConfig()),
    _config(DocumentStore::Config(CompressionConfig::LZ4, 1000000, 0).allowVisitCaching(true),
            LogDataStore::Config().setMaxFileSize(50000).setMaxBucketSpread(3.0)
                    .setFileConfig(WriteableFileChunk::Config(CompressionConfig(), 16384))),
    _fileHeaderContext(),
    _executor(1, 128*1024),
    _tlSyncer(),
    _datastore(_executor, _myDir.getDir(), _config, GrowStrategy(),
               TuneFileSummary(), _fileHeaderContext, _tlSyncer, nullptr),
    _inserted(),
    _serial(1)
{ }
VisitCacheStore::~VisitCacheStore() {}

void
verifyCacheStats(CacheStats cs, size_t hits, size_t misses, size_t elements, size_t memory_used) {
    EXPECT_EQUAL(hits, cs.hits);
    EXPECT_EQUAL(misses, cs.misses);
    EXPECT_EQUAL(elements, cs.elements);
    EXPECT_LESS_EQUAL(memory_used,  cs.memory_used + 20);  // We allow +- 20 as visitorder and hence compressability is non-deterministic.
    EXPECT_GREATER_EQUAL(memory_used+20,  cs.memory_used);
}

TEST("test that the integrated visit cache works.") {
    VisitCacheStore vcs;
    IDocumentStore & ds = vcs.getStore();
    for (size_t i(1); i <= 100; i++) {
        vcs.write(i);
    }
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 0, 0, 0, 0));

    for (size_t i(1); i <= 100; i++) {
        vcs.verifyRead(i);
    }
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 0, 100, 100, 20574));
    for (size_t i(1); i <= 100; i++) {
        vcs.verifyRead(i);
    }
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 100, 100, 100, 20574)); // From the individual cache.

    vcs.verifyVisit({7,9,17,19,67,88}, false);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 100, 100, 100, 20574));
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 100, 101, 101, 21135));
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 101, 101, 21135));
    vcs.rewrite(8);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 101, 100, 20922)); // From the individual cache.
    vcs.rewrite(7);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 101, 98, 20148)); // From the both caches.
    vcs.verifyVisit({7,9,17,19,67,88}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 102, 99, 20732));
    vcs.verifyVisit({7,9,17,19,67,88,89}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 103, 99, 20783));
    vcs.rewrite(17);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 103, 97, 19943));
    vcs.verifyVisit({7,9,17,19,67,88,89}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 104, 98, 20587));
    vcs.remove(17);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 104, 97, 19943));
    vcs.verifyVisit({7,9,17,19,67,88,89}, {7,9,19,67,88,89}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 105, 98, 20526));

    vcs.verifyVisit({41, 42}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 106, 99, 20820));
    vcs.verifyVisit({43, 44}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 107, 100, 21124));
    vcs.verifyVisit({41, 42, 43, 44}, true);
    TEST_DO(verifyCacheStats(ds.getCacheStats(), 101, 108, 99, 20944));
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
        vespalib::ThreadStackExecutor executor(1, 128*1024);
        MyTlSyncer tlSyncer;
        LogDataStore datastore(executor, "empty", config, GrowStrategy(),
                               TuneFileSummary(), fileHeaderContext, tlSyncer, NULL);
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
        vespalib::ThreadStackExecutor executor(1, 128*1024);
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

TEST("requireThatFlushTimeIsAvailableAfterFlush") {
    TmpDirectory testDir("flushtime");
    fastos::TimeStamp before(fastos::ClockSystem::now());
    DummyFileHeaderContext fileHeaderContext;
    LogDataStore::Config config;
    vespalib::ThreadStackExecutor executor(1, 128*1024);
    MyTlSyncer tlSyncer;
    LogDataStore store(executor, testDir.getDir(), config, GrowStrategy(),
                       TuneFileSummary(), fileHeaderContext, tlSyncer, nullptr);
    EXPECT_EQUAL(0, store.getLastFlushTime().time());
    uint64_t flushToken = store.initFlush(5);
    EXPECT_EQUAL(5u, flushToken);
    store.flush(flushToken);
    fastos::TimeStamp after(fastos::ClockSystem::now());
    // the file name of the dat file is 'magic', using the clock instead of stating the file
    EXPECT_LESS_EQUAL(before.time(), store.getLastFlushTime().time());
    EXPECT_GREATER_EQUAL(after.time(), store.getLastFlushTime().time());
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

LogDataStore::Config
getBasicConfig(size_t maxFileSize)
{
    return LogDataStore::Config().setMaxFileSize(maxFileSize);
}

vespalib::string
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

    Fixture(const vespalib::string &dirName = "tmp",
            bool dirCleanup = true,
            size_t maxFileSize = 4096 * 2)
        : executor(1, 0x10000),
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
        vespalib::string data = genData(lid, numBytes);
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
    void compactLidSpace(uint32_t wantedDocIdLimit) {
        store.compactLidSpace(wantedDocIdLimit);
        assertDocIdLimit(wantedDocIdLimit);
    }
    void assertDocIdLimit(uint32_t expDocIdLimit) {
        EXPECT_EQUAL(expDocIdLimit, store.getDocIdLimit());
    }
    void assertNumChunks(size_t numChunks) {
        EXPECT_EQUAL(numChunks, store.getFileChunkStats().size());
    }
    void assertDocIdLimitInFileChunks(const std::vector<uint32_t> expLimits) {
        std::vector<uint32_t> actLimits;
        for (const auto &stat : store.getFileChunkStats()) {
            actLimits.push_back(stat.docIdLimit());
        }
        EXPECT_EQUAL(expLimits, actLimits);
    }
    void assertContent(const std::set<uint32_t> &lids, uint32_t docIdLimit, size_t numBytesPerEntry = 1024) {
        for (uint32_t lid = 0; lid < docIdLimit; ++lid) {
            vespalib::DataBuffer buffer;
            size_t size = store.read(lid, buffer);
            if (lids.find(lid) != lids.end()) {
                vespalib::string expData = genData(lid, numBytesPerEntry);
                EXPECT_EQUAL(expData, vespalib::string(buffer.getData(), buffer.getDataLen()));
                EXPECT_GREATER(size, 0u);
            } else {
                EXPECT_EQUAL("", vespalib::string(buffer.getData(), buffer.getDataLen()));
                EXPECT_EQUAL(0u, size);
            }
        }
    }
};

TEST("require that docIdLimit is updated when inserting entries")
{
    {
        Fixture f("tmp", false);
        f.assertDocIdLimit(0);
        f.write(10);
        f.assertDocIdLimit(11);
        f.write(9);
        f.assertDocIdLimit(11);
        f.write(11);
        f.assertDocIdLimit(12);
        f.assertNumChunks(1);
        f.flush();
    }
    {
        Fixture f("tmp");
        f.assertDocIdLimit(12);
    }
}

TEST("require that docIdLimit at idx file creation time is written to idx file header")
{
    std::vector<uint32_t> expLimits = {std::numeric_limits<uint32_t>::max(),14,104,204};
    {
        Fixture f("tmp", false);
        f.writeUntilNewChunk(10);
        f.writeUntilNewChunk(100);
        f.writeUntilNewChunk(200);
        f.assertDocIdLimitInFileChunks(expLimits);
        f.flush();
    }
    {
        Fixture f("tmp");
        f.assertDocIdLimitInFileChunks(expLimits);
    }
}

TEST("require that lid space can be compacted and entries from old files skipped during load")
{
    {
        Fixture f("tmp", false);
        f.write(10);
        f.writeUntilNewChunk(100);
        f.write(20);
        f.writeUntilNewChunk(200);
        f.write(30);
        TEST_DO(f.assertContent({10,100,101,102,20,200,201,202,30}, 203));

        f.assertDocIdLimit(203);
        f.compactLidSpace(100);
        TEST_DO(f.assertContent({10,20,30}, 203));

        f.writeUntilNewChunk(31);
        f.write(99);
        f.write(300);
        TEST_DO(f.assertContent({10,20,30,31,32,33,99,300}, 301));
        f.assertDocIdLimitInFileChunks({std::numeric_limits<uint32_t>::max(),103,203,100});
        f.flush();
    }
    {
        Fixture f("tmp");
        TEST_DO(f.assertContent({10,20,30,31,32,33,99,300}, 301));
    }
}

TEST_F("require that getLid() is protected by docIdLimit", Fixture)
{
    f.write(1);
    vespalib::GenerationHandler::Guard guard = f.store.getLidReadGuard();
    EXPECT_TRUE(f.store.getLid(guard, 1).valid());
    EXPECT_FALSE(f.store.getLid(guard, 2).valid());
}

TEST_F("require that lid space can be compacted and shrunk", Fixture)
{
    f.write(1).write(2);
    EXPECT_FALSE(f.store.canShrinkLidSpace());

    f.compactLidSpace(2);
    MemoryUsage before = f.store.getMemoryUsage();
    EXPECT_TRUE(f.store.canShrinkLidSpace());
    EXPECT_EQUAL(8u, f.store.getEstimatedShrinkLidSpaceGain()); // one lid info entry
    f.store.shrinkLidSpace();

    MemoryUsage after = f.store.getMemoryUsage();
    EXPECT_LESS(after.usedBytes(), before.usedBytes());
    EXPECT_EQUAL(8u, before.usedBytes() - after.usedBytes());
}

TEST_F("require that lid space can be increased after being compacted and then shrunk", Fixture)
{
    f.write(1).write(3);
    TEST_DO(f.compactLidSpace(2));
    f.write(2);
    TEST_DO(f.assertDocIdLimit(3));
    f.store.shrinkLidSpace();
    TEST_DO(f.assertDocIdLimit(3));
    TEST_DO(f.assertContent({1,2}, 3));
}

TEST_F("require that lid space can be shrunk only after read guards are deleted", Fixture)
{
    f.write(1).write(2);
    EXPECT_FALSE(f.store.canShrinkLidSpace());
    {
        vespalib::GenerationHandler::Guard guard = f.store.getLidReadGuard();
        f.compactLidSpace(2);
        f.write(1); // trigger remove of old generations
        EXPECT_FALSE(f.store.canShrinkLidSpace());
        EXPECT_EQUAL(0u, f.store.getEstimatedShrinkLidSpaceGain());
    }
    f.write(1); // trigger remove of old generations
    EXPECT_TRUE(f.store.canShrinkLidSpace());
    EXPECT_EQUAL(8u, f.store.getEstimatedShrinkLidSpaceGain());
}

LogDataStore::NameIdSet create(std::vector<size_t> list) {
    LogDataStore::NameIdSet l;
    for (size_t id : list) {
        l.emplace(id);
    }
    return l;
}

TEST("require that findIncompleteCompactedFiles does expected filtering") {
    EXPECT_TRUE(LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,202,204})).empty());
    LogDataStore::NameIdSet toRemove = LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,201,204}));
    EXPECT_EQUAL(1u, toRemove.size());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(201)) != toRemove.end());
    toRemove = LogDataStore::findIncompleteCompactedFiles(create({1,2,4,100,200,201,204,205}));
    EXPECT_EQUAL(3u, toRemove.size());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(2)) != toRemove.end());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(201)) != toRemove.end());
    EXPECT_TRUE(toRemove.find(FileChunk::NameId(205)) != toRemove.end());

    EXPECT_EXCEPTION(LogDataStore::findIncompleteCompactedFiles(create({1,3,100,200,201,202,204})).empty(),
                     vespalib::IllegalStateException, "3 consecutive files {200, 201, 202}. Impossible");

}

TEST("require that config equality operator detects inequality") {
    using C = LogDataStore::Config;
    EXPECT_TRUE(C() == C());
    EXPECT_FALSE(C() == C().setMaxFileSize(1));
    EXPECT_FALSE(C() == C().setMaxDiskBloatFactor(0.3));
    EXPECT_FALSE(C() == C().setMaxBucketSpread(0.3));
    EXPECT_FALSE(C() == C().setMinFileSizeFactor(0.3));
    EXPECT_FALSE(C() == C().setFileConfig(WriteableFileChunk::Config({}, 70)));
    EXPECT_FALSE(C() == C().disableCrcOnRead(true));
    EXPECT_FALSE(C() == C().compact2ActiveFile(false));
    EXPECT_FALSE(C() == C().compactCompression({CompressionConfig::ZSTD}));
}

TEST_MAIN() {
    DummyFileHeaderContext::setCreator("logdatastore_test");
    TEST_RUN_ALL();
}
