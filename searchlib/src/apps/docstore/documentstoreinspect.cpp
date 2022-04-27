// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <cinttypes>

using namespace search;

class DocumentStoreInspectApp
{
    void usage(const char *self);
    int verify(const vespalib::string & directory);
    int dumpIdxFile(const vespalib::string & file);
public:
    int main(int argc, char **argv);
};



void
DocumentStoreInspectApp::usage(const char *self)
{
    printf("Usage: %s dumpidxfile [--idxfile idxFile]\n", self);
    fflush(stdout);
}

int DocumentStoreInspectApp::dumpIdxFile(const vespalib::string & file)
{
    FastOS_File idxFile(file.c_str());
    idxFile.enableMemoryMap(0);
    if (idxFile.OpenReadOnly()) {
        if (idxFile.IsMemoryMapped()) {
            int64_t fileSize = idxFile.GetSize();
            uint32_t docIdLimit = std::numeric_limits<uint32_t>::max();
            uint64_t idxHeaderLen = FileChunk::readIdxHeader(idxFile, docIdLimit);
            vespalib::nbostream is(static_cast<const char *>
                                   (idxFile.MemoryMapPtr(0)) + idxHeaderLen,
                                   fileSize - idxHeaderLen);
            size_t chunk(0);
            size_t entries(0);
            for (; ! is.empty(); chunk++) {
                ChunkMeta cm;
                cm.deserialize(is);
                fprintf(stdout, "Chunk(%zd) : LastSerial(%" PRIu64 "), Entries(%d), Offset(%" PRIu64 "), Size(%d)\n",
                                chunk, cm.getLastSerial(), cm.getNumEntries(), cm.getOffset(), cm.getSize());
                for (size_t i(0), m(cm.getNumEntries()); i < m; i++, entries++) {
                    LidMeta lm;
                    lm.deserialize(is);
                    fprintf(stdout, "Entry(%ld.%ld) : Lid(%d), Size(%d)\n", chunk, i, lm.getLid(), lm.size());
                }
            }
            fprintf(stdout, "Processed %ld chunks with total entries = %ld\n", chunk, entries);
        } else {
            fprintf(stderr, "Failed memorymapping file '%s' due to %s\n", idxFile.GetFileName(), idxFile.getLastErrorString().c_str());
        }
    } else {
        fprintf(stderr, "Failed opening file '%s' readonly due to %s\n", idxFile.GetFileName(), idxFile.getLastErrorString().c_str());
    }
    return 0;
}

int
DocumentStoreInspectApp::main(int argc, char **argv)
{
    vespalib::string cmd;
    if (argc >= 2) {
        cmd = argv[1];
        if (cmd == "dumpidxfile") {
            vespalib::string idxfile;
            if (argc >= 4) {
                if (vespalib::string(argv[2]) == vespalib::string("--idxfile")) {
                    idxfile = argv[3];
                    dumpIdxFile(idxfile);
                } else {
                    fprintf(stderr, "Unknown option '%s'.\n", argv[2]);
                    usage(argv[0]);
                    return 1;
                }
            } else {
                fprintf(stderr, "Too few arguments\n");
                usage(argv[0]);
                return 1;
            }
        } else {
            fprintf(stderr, "Unknown command '%s'.\n", cmd.c_str());
            usage(argv[0]);
            return 1;
        }
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage(argv[0]);
        return 1;
    }
    return 0;
}

int
DocumentStoreInspectApp::verify(const vespalib::string & dir)
{
    int retval(0);

    LogDataStore::Config config;
    GrowStrategy growStrategy;
    TuneFileSummary tuning;
    search::index::DummyFileHeaderContext fileHeaderContext;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    transactionlog::NoSyncProxy noTlSyncer;

    LogDataStore store(executor, dir, config, growStrategy, tuning,
                       fileHeaderContext, noTlSyncer, NULL, true);
    store.verify(false);
    return retval;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    DocumentStoreInspectApp app;
    return app.main(argc, argv);
}
