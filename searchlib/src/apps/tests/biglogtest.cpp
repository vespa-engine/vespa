// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/data/databuffer.h>

using namespace search;
using search::index::DummyFileHeaderContext;

class Test : public vespalib::TestApp {
private:
    struct Blob {
        ssize_t sz;
        char *buf;
        Blob(size_t s) : sz(s), buf(s == 0 ? 0 : new char[s]) {}
    };
    typedef std::map<uint32_t, uint32_t> Map;

    void makeBlobs();
    void cleanBlobs();
    void checkBlobs(const IDataStore &datastore, const Map &lidToBlobMap);

    template <typename DS>
    void testDIO();

    std::string _dir;
    std::vector<Blob> _blobs;
    vespalib::RandomGen _randomgenerator;

public:
    int Main() override {
        TEST_INIT("big_logdatastore_test");

        if (_argc > 0) {
            DummyFileHeaderContext::setCreator(_argv[0]);
        }
        makeBlobs();

        _dir = "logged";
        TEST_DO(testDIO<LogDataStore>());

        cleanBlobs();

        TEST_DONE();
    }

    Test() : _dir(""), _blobs(), _randomgenerator(42) {}
    ~Test() {}
};

TEST_APPHOOK(Test);

void
Test::makeBlobs()
{
    _randomgenerator.setSeed(42);
    _blobs.push_back(Blob(0));
    size_t usemem = 444222111;
    while (usemem > 0) {
        size_t sizeclass = 6 + _randomgenerator.nextUint32() % 20;
        size_t blobsize = _randomgenerator.nextUint32() % (1<<sizeclass);
        if (blobsize > usemem) blobsize = usemem;
        _blobs.push_back(Blob(blobsize));
        char *p = _blobs.back().buf;
        for (size_t j=0; j < blobsize; ++j) {
            *p++ = _randomgenerator.nextUint32();
        }
        usemem -= blobsize;
    }
}

void
Test::cleanBlobs()
{
    printf("count %lu blobs sizes:", _blobs.size());
    while (_blobs.size() > 0) {
        char *p = _blobs.back().buf;
        printf(" %lu", _blobs.back().sz);
        delete[] p;
        _blobs.pop_back();
    }
    printf("\n");
}


void
Test::checkBlobs(const IDataStore &datastore,
                 const Map &lidToBlobMap)
{
    for (Map::const_iterator it = lidToBlobMap.begin();
         it != lidToBlobMap.end();
         ++it)
    {
        uint32_t lid = it->first;
        uint32_t bno = it->second;
        vespalib::DataBuffer got;
        EXPECT_EQUAL(datastore.read(lid, got), _blobs[bno].sz);
        EXPECT_TRUE(memcmp(got.getData(), _blobs[bno].buf, _blobs[bno].sz) == 0);
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
      _executor(1, 128_Ki),
      _noTlSyncer(),
      _datastore(_executor, dir, _config, GrowStrategy(), tuning, _fileHeaderContext, _noTlSyncer, NULL)
{}

factory<LogDataStore>::~factory() {}

template <typename DS>
void
Test::testDIO()
{
    uint64_t serial = 0;

    FastOS_File::EmptyDirectory(_dir.c_str());
    FastOS_File::RemoveDirectory(_dir.c_str());
    EXPECT_TRUE(FastOS_File::MakeDirectory(_dir.c_str()));

    Map lidToBlobMap;
    vespalib::DataBuffer buf;
    {
        factory<DS> ds(_dir);
        for (uint32_t lid=0; lid<15; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
        }
        uint64_t flushToken = ds().initFlush(serial);
        ds().flush(flushToken);
        for (uint32_t lid=10; lid<30; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
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
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
        }
        for (uint32_t lid=23; lid<28; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
        }
        for (uint32_t lid=100033; lid<100088; ++lid) {
            uint32_t blobno = _randomgenerator.nextUint32() % _blobs.size();
            lidToBlobMap[lid] = blobno;
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
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
            ds().write(++serial, lid, _blobs[blobno].buf, _blobs[blobno].sz);
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
    FastOS_File::EmptyDirectory(_dir.c_str());
    FastOS_File::RemoveDirectory(_dir.c_str());
    TEST_FLUSH();
}
