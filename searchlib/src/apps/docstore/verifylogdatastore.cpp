// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/fastos/app.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace search;

class VerifyLogDataStoreApp : public FastOS_Application
{
    void usage(void);
    int verify(const vespalib::string & directory);
    int Main(void);
};



void
VerifyLogDataStoreApp::usage(void)
{
    printf("Usage: %s <direcory>\n", _argv[0]);
    fflush(stdout);
}

int
VerifyLogDataStoreApp::Main(void)
{
    if (_argc >= 2) {
        vespalib::string directory(_argv[1]);
        return verify(directory);
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage();
        return 1;
    }
    return 0;
}

int
VerifyLogDataStoreApp::verify(const vespalib::string & dir)
{
    int retval(0);

    LogDataStore::Config config;
    GrowStrategy growStrategy;
    TuneFileSummary tuning;
    search::index::DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(config.getNumThreads(), 128*1024);
    transactionlog::NoSyncProxy noTlSyncer;

    try {
        LogDataStore store(executor, dir, config, growStrategy, tuning,
                           fileHeaderContext,
                           noTlSyncer, NULL, true);
        store.verify(false);
    } catch (const vespalib::Exception & e) {
        fprintf(stderr, "Got exception: %s", e.what());
        retval = 1;
    }
    return retval;
}

FASTOS_MAIN(VerifyLogDataStoreApp);
