// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/random.h>
#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/data/databuffer.h>
#include <filesystem>
#include <memory>

using namespace search;
using search::index::DummyFileHeaderContext;

class BigLogTest : public ::testing::Test {
private:
    struct Blob {
        ssize_t sz;
        std::unique_ptr<char[]> buf;
        Blob(size_t s) : sz(s), buf(s == 0 ? nullptr : new char[s]) {}
    };
    using Map = std::map<uint32_t, uint32_t>;

    static void makeBlobs();
    static void cleanBlobs();
    void checkBlobs(const IDataStore &datastore, const Map &lidToBlobMap);

    std::string _dir;
    static std::vector<Blob> _blobs;
    static vespalib::RandomGen _randomgenerator;

protected:
    template <typename DS>
    void testDIO();

public:
    BigLogTest();
    ~BigLogTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

std::vector<BigLogTest::Blob> BigLogTest::_blobs;
vespalib::RandomGen BigLogTest::_randomgenerator(42);

BigLogTest::BigLogTest()
    : _dir("logged")
{
}

BigLogTest::~BigLogTest() = default;

void
BigLogTest::SetUpTestSuite()
{
    makeBlobs();
}

void
BigLogTest::TearDownTestSuite()
{
    cleanBlobs();
}

void
BigLogTest::makeBlobs()
{
    _randomgenerator.setSeed(42);
    _blobs.push_back(Blob(0));
    size_t usemem = 444222111;
    while (usemem > 0) {
        size_t sizeclass = 6 + _randomgenerator.nextUint32() % 20;
        size_t blobsize = _randomgenerator.nextUint32() % (1<<sizeclass);
        if (blobsize > usemem) blobsize = usemem;
        _blobs.push_back(Blob(blobsize));
        char *p = _blobs.back().buf.get();
        for (size_t j=0; j < blobsize; ++j) {
            *p++ = _randomgenerator.nextUint32();
        }
        usemem -= blobsize;
    }
}

void
BigLogTest::cleanBlobs()
{
    printf("count %lu blobs sizes:", _blobs.size());
    while (_blobs.size() > 0) {
        printf(" %lu", _blobs.back().sz);
        _blobs.pop_back();
    }
    printf("\n");
}

void
BigLogTest::checkBlobs(const IDataStore &datastore,
                       const Map &lidToBlobMap)
{
    for (Map::const_iterator it = lidToBlobMap.begin();
         it != lidToBlobMap.end();
         ++it)
    {
        uint32_t lid = it->first;
        uint32_t bno = it->second;
        vespalib::DataBuffer got;
        EXPECT_EQ(_blobs[bno].sz, datastore.read(lid, got));
        EXPECT_EQ(0, memcmp(got.getData(), _blobs[bno].buf.get(), _blobs[bno].sz));
    }
}

struct DioTune
{
    TuneFileSummary tuning;
    DioTune() {
        tuning._seqRead.setWantDirectIO();
        tuning._write.setWantDirectIO();
        tuning._randRead.setWantDirectIO();
    }
};

template <typename DS>
struct factory {};

template <>
struct factory<LogDataStore> : DioTune
{
    DummyFileHeaderContext _fileHeaderContext;
    LogDataStore::Config   _config;
    vespalib::ThreadStackExecutor _executor;
    transactionlog::NoSyncProxy _noTlSyncer;
    LogDataStore _datastore;
    factory(std::string dir);
    ~factory();
    IDataStore & operator() () { return _datastore; }

};

factory<LogDataStore>::factory(std::string dir)
    : DioTune(),
      _fileHeaderContext(),
      _config(),
      _executor(1),
      _noTlSyncer(),
      _datastore(_executor, dir, _config, GrowStrategy(), tuning, _fileHeaderContext, _noTlSyncer, NULL)
{}

factory<LogDataStore>::~factory() {}

template <typename DS>
void
BigLogTest::testDIO()
{
    uint64_t serial = 0;

    std::filesystem::remove_all(std::filesystem::path(_dir));
    std::filesystem::create_directory(std::filesystem::path(_dir));

    Map lidToBlobMap;
    vespalib::DataBuffer buf;
    {
        factory<DS> ds(_dir);
        for (uint32_t lid=0; lid<15; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        uint64_t flushToken = ds().initFlush(serial);
        ds().flush(flushToken);
        for (uint32_t lid=10; lid<30; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        checkBlobs(ds(), lidToBlobMap);
        flushToken = ds().initFlush(serial);
        ds().flush(flushToken);
        checkBlobs(ds(), lidToBlobMap);
    }
    {
        factory<DS> ds(_dir);
        checkBlobs(ds(), lidToBlobMap);

        for (uint32_t lid=3; lid<8; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        for (uint32_t lid=23; lid<28; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        for (uint32_t lid=100033; lid<100088; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        checkBlobs(ds(), lidToBlobMap);

        ds().remove(++serial, 1);
        lidToBlobMap[1] = 0;
        ds().remove(++serial, 11);
        lidToBlobMap[11] = 0;
        ds().remove(++serial, 21);
        lidToBlobMap[21] = 0;
        ds().remove(++serial, 31);
        lidToBlobMap[31] = 0;

        checkBlobs(ds(), lidToBlobMap);
        uint64_t flushToken = ds().initFlush(serial);
        ds().flush(flushToken);
        checkBlobs(ds(), lidToBlobMap);
    }
    {
        factory<DS> ds(_dir);

        ASSERT_TRUE(ds().read(1, buf) <= 0);
        ASSERT_TRUE(ds().read(11, buf) <= 0);
        ASSERT_TRUE(ds().read(21, buf) <= 0);
        ASSERT_TRUE(ds().read(31, buf) <= 0);

        checkBlobs(ds(), lidToBlobMap);
        uint64_t flushToken = ds().initFlush(serial);
        ds().flush(flushToken);
    }
    {
        factory<DS> ds(_dir);
        checkBlobs(ds(), lidToBlobMap);

        for (uint32_t lid=1234567; lid < 1234999; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf.get(), _blobs[blobno].sz);
        }
        checkBlobs(ds(), lidToBlobMap);
        uint64_t flushToken = ds().initFlush(22);
        ds().flush(flushToken);
        checkBlobs(ds(), lidToBlobMap);
    }
    {
        factory<DS> ds(_dir);
        checkBlobs(ds(), lidToBlobMap);
    }
    std::filesystem::remove_all(std::filesystem::path(_dir));
}

TEST_F(BigLogTest, logdatastore_dio)
{
    testDIO<LogDataStore>();
}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    return RUN_ALL_TESTS();
}
