// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace search;

class VerifyLogDataStoreApp
{
    void usage(const char *self);
    int verify(const vespalib::string & directory);
public:
    int main(int argc, char **argv);
};



void
VerifyLogDataStoreApp::usage(const char *self)
{
    printf("Usage: %s <direcory>\n", self);
    fflush(stdout);
}

int
VerifyLogDataStoreApp::main(int argc, char **argv)
{
    if (argc >= 2) {
        vespalib::string directory(argv[1]);
        return verify(directory);
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage(argv[0]);
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
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
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

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    VerifyLogDataStoreApp app;
    return app.main(argc, argv);
}
