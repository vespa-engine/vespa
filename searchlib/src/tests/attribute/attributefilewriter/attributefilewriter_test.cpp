// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefilewriter.h>
#include <vespa/searchlib/attribute/attributefilebufferwriter.h>
#include <vespa/searchlib/attribute/attribute_header.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("attributefilewriter_test");

using search::index::DummyFileHeaderContext;

namespace search {

namespace {

std::string testFileName("test.dat");
std::string hello("Hello world");

void removeTestFile() { std::filesystem::remove(std::filesystem::path(testFileName)); }

}

class AttributeFileWriterTest : public ::testing::Test {
protected:
    TuneFileAttributes _tuneFileAttributes;
    DummyFileHeaderContext _fileHeaderContext;
    attribute::AttributeHeader _header;
    const std::string _desc;
    AttributeFileWriter _writer;

    AttributeFileWriterTest();
    ~AttributeFileWriterTest() override;
};

AttributeFileWriterTest::AttributeFileWriterTest()
    : ::testing::Test(),
      _tuneFileAttributes(),
      _fileHeaderContext(),
      _header(),
      _desc("Attribute file sample description"),
      _writer(_tuneFileAttributes,
              _fileHeaderContext,
              _header,
              _desc) {
    removeTestFile();
}

AttributeFileWriterTest::~AttributeFileWriterTest() {
    removeTestFile();
}

TEST_F(AttributeFileWriterTest, Test_that_we_can_write_empty_attribute_file)
{
    EXPECT_TRUE(_writer.open(testFileName));
    _writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQ(0u, loaded->size());
}


TEST_F(AttributeFileWriterTest, Test_that_we_destroy_writer_without_calling_close)
{
    EXPECT_TRUE(_writer.open(testFileName));
}


TEST_F(AttributeFileWriterTest, Test_that_buffer_writer_passes_on_written_data)
{
    std::vector<int> a;
    const size_t mysize = 3000000;
    const size_t writerBufferSize = AttributeFileBufferWriter::BUFFER_SIZE;
    EXPECT_GT(mysize * sizeof(int), writerBufferSize);
    a.reserve(mysize);
    vespalib::Rand48 rnd;
    for (uint32_t i = 0; i < mysize; ++i) {
        a.emplace_back(rnd.lrand48());
    }
    EXPECT_TRUE(_writer.open(testFileName));
    std::unique_ptr<BufferWriter> writer(_writer.allocBufferWriter());
    writer->write(&a[0], a.size() * sizeof(int));
    writer->flush();
    writer.reset();
    _writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQ(a.size() * sizeof(int), loaded->size());
    EXPECT_TRUE(memcmp(&a[0], loaded->buffer(), loaded->size()) == 0);
}


TEST_F(AttributeFileWriterTest, Test_that_we_can_pass_buffer_directly)
{
    using Buffer = IAttributeFileWriter::Buffer;
    Buffer buf = _writer.allocBuf(hello.size());
    buf->writeBytes(hello.c_str(), hello.size());
    EXPECT_TRUE(_writer.open(testFileName));
    _writer.writeBuf(std::move(buf));
    _writer.close();
    fileutil::LoadedBuffer::UP loaded(FileUtil::loadFile(testFileName));
    EXPECT_EQ(hello.size(), loaded->size());
    EXPECT_TRUE(memcmp(hello.c_str(), loaded->buffer(), loaded->size()) == 0);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
