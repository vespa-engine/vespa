// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/docstore/filechunk.h>
#include <vespa/searchlib/docstore/writeablefilechunk.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("file_chunk_test");

using namespace search;

using common::FileHeaderContext;
using vespalib::ThreadStackExecutor;

constexpr uint64_t initSerialNum = 10;

struct MyFileHeaderContext : public FileHeaderContext {
    virtual void addTags(vespalib::GenericHeader &header, const vespalib::string &name) const override {
        (void) header;
        (void) name;
    }
};

struct Fixture {
    test::DirectoryHandler dir;
    ThreadStackExecutor executor;
    TuneFileSummary tuneFile;
    MyFileHeaderContext fileHeaderCtx;
    WriteableFileChunk chunk;

    Fixture(const vespalib::string &baseName,
            uint32_t docIdLimit,
            bool dirCleanup = true)
        : dir(baseName),
          executor(1, 0x10000),
          tuneFile(),
          fileHeaderCtx(),
          chunk(executor,
                FileChunk::FileId(0),
                FileChunk::NameId(1234),
                baseName,
                initSerialNum,
                docIdLimit,
                WriteableFileChunk::Config(),
                tuneFile,
                fileHeaderCtx,
                nullptr,
                false)
    {
        dir.cleanup(dirCleanup);
    }
    ~Fixture() {}
};

TEST_F("require that idx file without docIdLimit in header can be read", Fixture("without_doc_id_limit", 1000, false))
{
    EXPECT_EQUAL(std::numeric_limits<uint32_t>::max(), f.chunk.getDocIdLimit());
}

TEST("require that docIdLimit is written to and read from idx file header")
{
    {
        Fixture f("tmp", 1000, false);
    }
    {
        Fixture f("tmp", 0);
        EXPECT_EQUAL(1000u, f.chunk.getDocIdLimit());
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }

