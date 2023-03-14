// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/docstore/logdatastore.h>
#include <vespa/searchlib/docstore/randreaders.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/exception.h>
#include <cinttypes>
#include <cassert>

using namespace search;

class CreateIdxFileFromDatApp
{
    void usage(const char *self);
    int createIdxFile(const vespalib::string & datFileName, const vespalib::string & idxFileName);
public:
    int main(int argc, char **argv);
};

void
CreateIdxFileFromDatApp::usage(const char *self)
{
    printf("Usage: %s <datfile> <idxfile>\n", self);
    fflush(stdout);
}

namespace {
bool tryDecode(size_t chunks, size_t offset, const char * p, size_t sz, size_t nextSync)
{
    bool success(false);
    for (size_t lengthError(0); !success && (sz + lengthError <= nextSync); lengthError++) {
        try {
            Chunk chunk(chunks, p, sz + lengthError);
            success = true;
        } catch (const vespalib::Exception & e) {
            fprintf(stdout, "Chunk %ld, with size=%ld failed with lengthError %ld due to '%s'\n", offset, sz, lengthError, e.what());
        }
    }
    return success;
}

bool validUncompressed(const char * n, size_t offset) {
    return (n[1] == vespalib::compression::CompressionConfig::NONE) &&
           (n[2] == 0) &&
           (n[3] == 0) &&
           (n[4] == 0) &&
           (n[5] != 0) &&
           tryDecode(0, offset, n, 6ul + 4ul + uint8_t(n[5]), 6ul + 4ul + uint8_t(n[5]) + 4);
}

bool validHead(const char * n, size_t offset) {
    return (n[0] == 0) && (validUncompressed(n, offset));
}

uint64_t
generate(uint64_t serialNum, size_t chunks, FastOS_FileInterface & idxFile, size_t sz, const char * current, const char * start, const char * nextStart) __attribute__((noinline));
uint64_t
generate(uint64_t serialNum, size_t chunks, FastOS_FileInterface & idxFile, size_t sz, const char * current, const char * start, const char * nextStart)
{
    vespalib::nbostream os;
    for (size_t lengthError(0); int64_t(sz+lengthError) <= nextStart-start; lengthError++) {
        try {
            Chunk chunk(chunks, current, sz + lengthError);
            fprintf(stdout, "id=%d lastSerial=%" PRIu64 " count=%ld\n", chunk.getId(), chunk.getLastSerial(), chunk.count());
            const Chunk::LidList & lidlist = chunk.getLids();
            if (chunk.getLastSerial() < serialNum) {
                fprintf(stdout, "Serial num grows down prev=%" PRIu64 ", current=%" PRIu64 "\n", serialNum, chunk.getLastSerial());
            }
            serialNum = std::max(serialNum, chunk.getLastSerial());
            ChunkMeta cmeta(current-start, sz + lengthError, serialNum, chunk.count());
            cmeta.serialize(os);
            for (auto it(lidlist.begin()); it != lidlist.end(); it++) {
                LidMeta lm(it->getLid(), it->netSize());
                lm.serialize(os);
            }
            break;
        } catch (const vespalib::Exception & e) {
            fprintf(stdout, "Failed with lengthError %ld due to '%s'\n", lengthError, e.what());
        }
    }
    ssize_t written = idxFile.Write2(os.data(), os.size());
    assert(written == ssize_t(os.size()));
    return serialNum;
}

}
int CreateIdxFileFromDatApp::createIdxFile(const vespalib::string & datFileName, const vespalib::string & idxFileName)
{
    MMapRandRead datFile(datFileName, 0, 0);
    int64_t fileSize = datFile.getSize();
    uint64_t datHeaderLen = FileChunk::readDataHeader(datFile);
    const char * start = static_cast<const char *>(datFile.getMapping());
    const char * end = start + fileSize;
    uint64_t chunks(0);
    uint64_t entries(0);
    uint64_t alignment(512);
    FastOS_File idxFile(idxFileName.c_str());
    assert(idxFile.OpenWriteOnly());
    index::DummyFileHeaderContext fileHeaderContext;
    idxFile.SetPosition(WriteableFileChunk::writeIdxHeader(fileHeaderContext, std::numeric_limits<uint32_t>::max(), idxFile));
    fprintf(stdout, "datHeaderLen=%" PRIu64 "\n", datHeaderLen);
    uint64_t serialNum(0);
    for (const char * current(start + datHeaderLen); current < end; ) {
        if (validHead(current, current-start)) {
            const char * tail(current);
            const char * nextStart(current+alignment);
            for (; nextStart < end; nextStart+=alignment) {
                if (validHead(nextStart, nextStart-start)) {
                    tail = nextStart;
                    while(*(tail-1) == 0) {
                        tail--;
                    }
                    if (tryDecode(chunks, current-start, current, tail - current, nextStart-current)) {
                        break;
                    } else {
                        fprintf(stdout, "chunk %" PRIu64 " possibly starting at %ld ending at %ld false sync at pos=%ld\n",
                                chunks, current-start, tail-start, nextStart-start);
                    }
                }
            }
            if (tail == current) {
                nextStart = end;
                tail = end;
                while(*(tail-1) == 0) {
                    tail--;
                }
            }
            uint64_t sz = tail - current;
            fprintf(stdout, "Most likely found chunk at offset %ld with length %" PRIu64 "\n", current - start, sz);
            serialNum = generate(serialNum, chunks,idxFile, sz, current, start, nextStart);
            chunks++;
            for(current += alignment; current < tail; current += alignment);
        } else {
            current += alignment;
        }
#if 0
        fprintf(stdout, "Next is most likely at offset %ld tail(%p)\n", current - start, tail);
        ChunkMeta cm;
        cm.deserialize(is);
        fprintf(stdout, "Chunk(%ld) : LastSerial(%ld), Entries(%d), Offset(%ld), Size(%d)\n",
                        chunk, cm.getLastSerial(), cm.getNumEntries(), cm.getOffset(), cm.getSize());
        for (size_t i(0), m(cm.getNumEntries()); i < m; i++, entries++) {
            LidMeta lm;
            lm.deserialize(is);
            fprintf(stdout, "Entry(%ld.%ld) : Lid(%d), Size(%d)\n", chunk, i, lm.getLid(), lm.size());
        }
#endif
    }
    fprintf(stdout, "Processed %" PRIu64 " chunks with total entries = %" PRIu64 "\n", chunks, entries);
    return 0;
}

int
CreateIdxFileFromDatApp::main(int argc, char **argv)
{
    vespalib::string cmd;
    if (argc == 3) {
        vespalib::string datFile(argv[1]);
        vespalib::string idxfile(argv[2]);
        createIdxFile(datFile, idxfile);
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage(argv[0]);
        return 1;
    }
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    CreateIdxFileFromDatApp app;
    return app.main(argc, argv);
}
