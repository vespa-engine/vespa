// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/attribute/attributefilewriter.h>
#include <vespa/searchlib/attribute/attributefilebufferwriter.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/data/databuffer.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("attributefilewriter_test");

using search::index::DummyFileHeaderContext;

namespace search {

namespace {

vespalib::string testFileName("test.dat");
vespalib::string hello("Hello world");

void removeTestFile() { std::filesystem::remove(std::filesystem::path(testFileName)); }

struct Fixture {
    TuneFileAttributes _tuneFileAttributes;
    DummyFileHeaderContext _fileHeaderContext;
    attribute::AttributeHeader _header;
    const vespalib::string _desc;
    AttributeFileWriter _writer;

    Fixture()
        : _tuneFileAttributes(),
          _fileHeaderContext(),
          _header(),
          _desc("Attribute file sample description"),
          _writer(_tuneFileAttributes,
                  _fileHeaderContext,
                  _header,
                  _desc)
    {
        removeTestFile();
    }

    ~Fixture() {
        removeTestFile();
    }

};

}


TEST_F("Test that we can write empty attribute file", Fixture)
{
    EXPECT_TRUE(f._writer.open(testFileName));
    f._writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQUAL(0u, loaded->size());
}


TEST_F("Test that we destroy writer without calling close", Fixture)
{
    EXPECT_TRUE(f._writer.open(testFileName));
}


TEST_F("Test that buffer writer passes on written data", Fixture)
{
    std::vector<int> a;
    const size_t mysize = 3000000;
    const size_t writerBufferSize = AttributeFileBufferWriter::BUFFER_SIZE;
    EXPECT_GREATER(mysize * sizeof(int), writerBufferSize);
    a.reserve(mysize);
    vespalib::Rand48 rnd;
    for (uint32_t i = 0; i < mysize; ++i) {
        a.emplace_back(rnd.lrand48());
    }
    EXPECT_TRUE(f._writer.open(testFileName));
    std::unique_ptr<BufferWriter> writer(f._writer.allocBufferWriter());
    writer->write(&a[0], a.size() * sizeof(int));
    writer->flush();
    writer.reset();
    f._writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQUAL(a.size() * sizeof(int), loaded->size());
    EXPECT_TRUE(memcmp(&a[0], loaded->buffer(), loaded->size()) == 0);
}


TEST_F("Test that we can pass buffer directly", Fixture)
{
    using Buffer = IAttributeFileWriter::Buffer;
    Buffer buf = f._writer.allocBuf(hello.size());
    buf->writeBytes(hello.c_str(), hello.size());
    EXPECT_TRUE(f._writer.open(testFileName));
    f._writer.writeBuf(std::move(buf));
    f._writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQUAL(hello.size(), loaded->size());
    EXPECT_TRUE(memcmp(hello.c_str(), loaded->buffer(), loaded->size()) == 0);
}


}


TEST_MAIN()
{
    TEST_RUN_ALL();
}
