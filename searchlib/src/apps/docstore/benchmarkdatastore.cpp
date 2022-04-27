// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <unistd.h>
#include <random>

#include <vespa/log/log.h>
LOG_SETUP("documentstore.benchmark");

using namespace search;

class BenchmarkDataStoreApp
{
    void usage(const char *self);
    int benchmark(const vespalib::string & directory, size_t numReads, size_t numThreads, size_t perChunk, const vespalib::string & readType);
    void read(size_t numReads, size_t perChunk, const IDataStore * dataStore);
public:
    int main(int argc, char **argv);
};



void
BenchmarkDataStoreApp::usage(const char *self)
{
    printf("Usage: %s <direcory> <numreads> <numthreads> <objects per read> <normal,directio,mmap>\n", self);
    fflush(stdout);
}

int
BenchmarkDataStoreApp::main(int argc, char **argv)
{
    if (argc >= 2) {
        size_t numThreads(16);
        size_t numReads(1000000);
        size_t perChunk(1);
        vespalib::string readType("directio");
        vespalib::string directory(argv[1]);
        if (argc >= 3) {
            numReads = strtoul(argv[2], NULL, 0);
            if (argc >= 4) {
                numThreads = strtoul(argv[3], NULL, 0);
                if (argc >= 5) {
                    perChunk = strtoul(argv[4], NULL, 0);
                    if (argc >= 5) {
                        readType = argv[5];
                    }
                }
            }
        }
        return benchmark(directory, numReads, numThreads, perChunk, readType);
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage(argv[0]);
        return 1;
    }
    return 0;
}

void BenchmarkDataStoreApp::read(size_t numReads, size_t perChunk, const IDataStore * dataStore)
{
    vespalib::DataBuffer buf;
    std::minstd_rand rng;
    const size_t docIdLimit(dataStore->getDocIdLimit());
    assert(docIdLimit > 0);
    rng.seed(getpid());
    int32_t rnd(0);
    for ( size_t i(0); i < numReads; i++) {
        rnd = rng();
        uint32_t lid(rnd%docIdLimit);
        for (uint32_t j(lid); j < std::min(docIdLimit, lid+perChunk); j++) {
            dataStore->read(j, buf);
            buf.clear();
        }
    }
}

int
BenchmarkDataStoreApp::benchmark(const vespalib::string & dir, size_t numReads, size_t numThreads, size_t perChunk, const vespalib::string & readType)
{
    int retval(0);
    LogDataStore::Config config;
    GrowStrategy growStrategy;
    TuneFileSummary tuning;
    if (readType == "directio") {
        tuning._randRead.setWantDirectIO();
    } else if (readType == "normal") {
        tuning._randRead.setWantNormal();
    } else if (readType == "mmap") {
        tuning._randRead.setWantMemoryMap();
    }
    search::index::DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    transactionlog::NoSyncProxy noTlSyncer;
    LogDataStore store(executor, dir, config, growStrategy, tuning,
                       fileHeaderContext,
                       noTlSyncer, NULL, true);
    vespalib::ThreadStackExecutor bmPool(numThreads, 128_Ki);
    LOG(info, "Start read benchmark with %lu threads doing %lu reads in chunks of %lu reads. Totally %lu objects", numThreads, numReads, perChunk, numThreads * numReads * perChunk);
    for (size_t i(0); i < numThreads; i++) {
        bmPool.execute(vespalib::makeLambdaTask([&]() { read(numReads, perChunk, static_cast<const IDataStore *>(&store)); }));
    }
    bmPool.sync();
    LOG(info, "Benchmark done.");
    return retval;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    BenchmarkDataStoreApp app;
    return app.main(argc, argv);
}
