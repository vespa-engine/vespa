// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/fastos/file.h>
#include <filesystem>

using namespace vespalib;

namespace {

vespalib::string fileheader_tmp("fileheader.tmp");

}

class FileHeaderTest : public ::testing::Test {
protected:
    FileHeaderTest();
    ~FileHeaderTest() override;
    bool testReadError(DataBuffer &buf, const std::string &expected);
    void testReadSize(bool mapped);
    void testReadSizeErrors(bool mapped);
    bool testReadSizeError(DataBuffer &buf, const std::string &expected, bool mapped);
};

FileHeaderTest::FileHeaderTest() = default;
FileHeaderTest::~FileHeaderTest() = default;

TEST_F(FileHeaderTest, test_tag)
{
    {
        std::vector<GenericHeader::Tag> tags;
        tags.push_back(GenericHeader::Tag("foo", 6.9));
        tags.push_back(GenericHeader::Tag("foo", 6.9f));
        for (std::vector<GenericHeader::Tag>::iterator it = tags.begin();
             it != tags.end(); ++it)
        {
            GenericHeader::Tag tag = *it;
            for (uint32_t i = 0; i < 2; ++i) {
                EXPECT_EQ(GenericHeader::Tag::TYPE_FLOAT, tag.getType());
                EXPECT_EQ("foo", tag.getName());
                EXPECT_TRUE(tag.asString().empty());
                EXPECT_NEAR(6.9, tag.asFloat(), 1E-6);
                EXPECT_EQ(0, tag.asInteger());

                uint32_t len = tag.getSize();
                DataBuffer buf(len);
                EXPECT_EQ(len, tag.write(buf));

                GenericHeader::Tag tmp;
                EXPECT_EQ(len, tmp.read(buf));
                tag = tmp;
            }
        }
    }
    {
        std::vector<GenericHeader::Tag> tags;
        tags.push_back(GenericHeader::Tag("foo", (int8_t)69));
        tags.push_back(GenericHeader::Tag("foo", (uint8_t)69));
        tags.push_back(GenericHeader::Tag("foo", (int16_t)69));
        tags.push_back(GenericHeader::Tag("foo", (uint16_t)69));
        tags.push_back(GenericHeader::Tag("foo", (int32_t)69));
        tags.push_back(GenericHeader::Tag("foo", (uint32_t)69));
        tags.push_back(GenericHeader::Tag("foo", (int64_t)69));
        for (std::vector<GenericHeader::Tag>::iterator it = tags.begin();
             it != tags.end(); ++it)
        {
            GenericHeader::Tag tag = *it;
            for (uint32_t i = 0; i < 2; ++i) {
                EXPECT_EQ(GenericHeader::Tag::TYPE_INTEGER, tag.getType());
                EXPECT_EQ("foo", tag.getName());
                EXPECT_TRUE(tag.asString().empty());
                EXPECT_EQ(0.0, tag.asFloat());
                EXPECT_EQ(69l, tag.asInteger());

                uint32_t len = tag.getSize();
                DataBuffer buf(len);
                EXPECT_EQ(len, tag.write(buf));

                GenericHeader::Tag tmp;
                EXPECT_EQ(len, tmp.read(buf));
                tag = tmp;
            }
        }
    }
    {
        GenericHeader::Tag tag("foo", "bar");
        for (uint32_t i = 0; i < 2; ++i) {
            EXPECT_EQ(GenericHeader::Tag::TYPE_STRING, tag.getType());
            EXPECT_EQ("foo", tag.getName());
            EXPECT_EQ("bar", tag.asString());
            EXPECT_EQ(0.0, tag.asFloat());
            EXPECT_EQ(0, tag.asInteger());

            uint32_t len = tag.getSize();
            DataBuffer buf(len);
            EXPECT_EQ(len, tag.write(buf));

            GenericHeader::Tag tmp;
            EXPECT_EQ(len, tmp.read(buf));
            tag = tmp;
        }
    }
    {
        GenericHeader::Tag trueTag("foo", true);
        GenericHeader::Tag falseTag("foo", false);
        EXPECT_EQ(GenericHeader::Tag::TYPE_INTEGER, trueTag.getType());
        EXPECT_EQ(GenericHeader::Tag::TYPE_INTEGER, falseTag.getType());
        EXPECT_EQ(1, trueTag.asInteger());
        EXPECT_EQ(0, falseTag.asInteger());
        EXPECT_TRUE(trueTag.asBool());
        EXPECT_FALSE(falseTag.asBool());
    }
}

TEST_F(FileHeaderTest, test_tag_errors)
{
    DataBuffer buf(1024);
    buf.writeBytes("foo", 3);
    buf.writeInt8(0);
    buf.writeInt8((uint8_t)GenericHeader::Tag::TYPE_EMPTY);

    GenericHeader::Tag tag("bar", 6.9);
    try {
        tag.read(buf);
        EXPECT_TRUE(false);
    } catch (IllegalHeaderException &e) {
        EXPECT_EQ("Can not deserialize empty tag.", e.getMessage());
    }
    EXPECT_EQ("bar", tag.getName());
    EXPECT_EQ(GenericHeader::Tag::TYPE_FLOAT, tag.getType());
    EXPECT_EQ(6.9, tag.asFloat());
}

TEST_F(FileHeaderTest, test_tag_iteration)
{
    GenericHeader header;
    header.putTag(GenericHeader::Tag("foo", 6.9));
    header.putTag(GenericHeader::Tag("bar", 6699));
    header.putTag(GenericHeader::Tag("baz", "666999"));

    EXPECT_EQ(3u, header.getNumTags());
    EXPECT_EQ("bar", header.getTag(0).getName());
    EXPECT_EQ("baz", header.getTag(1).getName());
    EXPECT_EQ("foo", header.getTag(2).getName());
}

TEST_F(FileHeaderTest, test_generic_header)
{
    GenericHeader header;
    EXPECT_TRUE(header.isEmpty());
    EXPECT_EQ(0u, header.getNumTags());
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("foo", 6.9));
    EXPECT_TRUE(!header.isEmpty());
    EXPECT_EQ(1u, header.getNumTags());
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("bar", 6699));
    EXPECT_TRUE(!header.isEmpty());
    EXPECT_EQ(2u, header.getNumTags());
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQ(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("baz", "666999"));
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQ(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQ("666999", header.getTag("baz").asString());

    header.removeTag("bar");
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQ("666999", header.getTag("baz").asString());

    header.removeTag("foo");
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQ("666999", header.getTag("baz").asString());

    header.removeTag("baz");
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());
}

TEST_F(FileHeaderTest, test_buffer_reader)
{
    DataBuffer src(256);
    for (uint32_t i = 0; i < 256; ++i) {
        src.writeInt8((uint8_t)i);
    }

    GenericHeader::BufferReader reader(src);

    char dst[7];
    uint32_t sum = 0;
    while (sum < 256) {
        uint32_t len = (uint32_t)reader.getData(dst, 7);
        for (uint32_t i = 0; i < len; ++i) {
            EXPECT_EQ(sum + i, (uint8_t)dst[i]);
        }
        sum += len;
    }
    EXPECT_EQ(256u, sum);
}

TEST_F(FileHeaderTest, test_buffer_writer)
{
    DataBuffer dst(256);
    GenericHeader::BufferWriter writer(dst);

    uint32_t sum = 0;
    while(sum < 256) {
        char src[7];
        for (uint32_t i = 0; i < 7; ++i) {
            src[i] = (uint8_t)(sum + i);
        }
        uint32_t len = std::min(7u, 256 - sum);
        EXPECT_EQ(len, (uint32_t)writer.putData(src, len));
        sum += len;
    }
    EXPECT_EQ(256u, sum);

    // flip dst
    for (uint32_t i = 0; i < 256; ++i) {
        uint8_t b = dst.readInt8();
        EXPECT_EQ(i, (uint32_t)b);
    }
}

TEST_F(FileHeaderTest, test_buffer_access)
{
    DataBuffer buf;
    uint32_t len;
    {
        GenericHeader header;
        header.putTag(GenericHeader::Tag("foo", 6.9));
        header.putTag(GenericHeader::Tag("bar", 6699));
        header.putTag(GenericHeader::Tag("baz", "666999"));

        int64_t bval = 0x1234567890abcdefLL;
        header.putTag(GenericHeader::Tag("big", bval));

        len = header.getSize();
        buf.ensureFree(len);
        GenericHeader::BufferWriter writer(buf);
        EXPECT_EQ(len, header.write(writer));
    }
    {
        GenericHeader header;
        GenericHeader::BufferReader reader(buf);
        EXPECT_EQ(len, header.read(reader));

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQ(6.9, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQ(6699, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQ("666999", header.getTag("baz").asString());
        EXPECT_TRUE(header.hasTag("big"));
        EXPECT_EQ(0x1234567890abcdefLL, header.getTag("big").asInteger());
    }
}

TEST_F(FileHeaderTest, test_file_reader)
{
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate(fileheader_tmp.c_str()));

        uint8_t buf[256];
        for (uint32_t i = 0; i < 256; ++i) {
            buf[i] = (uint8_t)i;
        }
        EXPECT_EQ(256, file.Write2(buf, 256));
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly(fileheader_tmp.c_str()));
        FileHeader::FileReader reader(file);

        char buf[7];
        uint32_t sum = 0;
        while(sum < 256) {
            uint32_t len = (uint32_t)reader.getData(buf, 7);
            for (uint32_t i = 0; i < len; ++i) {
                EXPECT_EQ(sum + i, (uint8_t)buf[i]);
            }
            sum += len;
        }
        EXPECT_EQ(256u, sum);

        ASSERT_TRUE(file.Close());
        std::filesystem::remove(std::filesystem::path(fileheader_tmp));
    }
}

TEST_F(FileHeaderTest, test_file_writer)
{
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate(fileheader_tmp.c_str()));
        FileHeader::FileWriter writer(file);

        uint32_t sum = 0;
        while(sum < 256) {
            char src[7];
            for (uint32_t i = 0; i < 7; ++i) {
                src[i] = (uint8_t)(sum + i);
            }
            uint32_t len = std::min(7u, 256 - sum);
            EXPECT_EQ(len, (uint32_t)writer.putData(src, len));
            sum += len;
        }
        EXPECT_EQ(256u, sum);
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly(fileheader_tmp.c_str()));

        uint8_t buf[256];
        EXPECT_EQ(256, file.Read(buf, 256));
        for (uint32_t i = 0; i < 256; ++i) {
            EXPECT_EQ(i, (uint32_t)buf[i]);
        }

        ASSERT_TRUE(file.Close());
        std::filesystem::remove(std::filesystem::path(fileheader_tmp));
    }
}

TEST_F(FileHeaderTest, test_file_header)
{
    uint32_t len = 0;
    {
        FileHeader header;
        header.putTag(FileHeader::Tag("foo", 6.9));
        header.putTag(FileHeader::Tag("bar", 6699));
        header.putTag(FileHeader::Tag("baz", "666999"));

        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate(fileheader_tmp.c_str()));
        len = header.writeFile(file);
        EXPECT_EQ(len, header.getSize());
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadWrite(fileheader_tmp.c_str()));

        FileHeader header;
        EXPECT_EQ(len, header.readFile(file));
        EXPECT_EQ(len, header.getSize());

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQ(6.9, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQ(6699, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQ("666999", header.getTag("baz").asString());

        header.putTag(FileHeader::Tag("foo", 9.6));
        header.putTag(FileHeader::Tag("bar", 9966));
        header.putTag(FileHeader::Tag("baz", "999666"));
        EXPECT_EQ(len, header.getSize());
        EXPECT_EQ(len, header.rewriteFile(file));
    }
    {
        FileHeader header;

        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly(fileheader_tmp.c_str()));
        EXPECT_EQ(len, header.readFile(file));
        EXPECT_EQ(len, header.getSize());
        ASSERT_TRUE(file.Close());
        std::filesystem::remove(std::filesystem::path(fileheader_tmp));

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQ(9.6, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQ(9966, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQ("999666", header.getTag("baz").asString());
    }
}

TEST_F(FileHeaderTest, test_file_align)
{
    for (uint32_t alignTo = 1; alignTo < 16; ++alignTo) {
        FileHeader header(alignTo);
        header.putTag(FileHeader::Tag("foo", "bar"));
        EXPECT_EQ(0u, header.getSize() % alignTo);
    }
}

TEST_F(FileHeaderTest, test_file_size)
{
    for (uint32_t minSize = 0; minSize < 512; ++minSize) {
        FileHeader header(1u, minSize);
        header.putTag(FileHeader::Tag("foo", "bar"));
        EXPECT_TRUE(header.getSize() >= minSize);
    }
}

TEST_F(FileHeaderTest, test_read_errors)
{
    {
        DataBuffer buf;
        EXPECT_TRUE(testReadError(buf, "Failed to read header info."));
    }
    {
        DataBuffer buf;
        buf.writeInt32(0xDEADBEAF);
        buf.writeInt32(8);
        EXPECT_TRUE(testReadError(buf, "Failed to verify magic bits."));
    }
    {
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(8);
        EXPECT_TRUE(testReadError(buf, "Failed to verify header size."));
    }
    {
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(16);
        buf.writeInt32(-1);
        buf.writeInt32(0);
        EXPECT_TRUE(testReadError(buf, "Failed to verify header version."));
    }
    {
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(21);
        buf.writeInt32(GenericHeader::VERSION);
        buf.writeInt32(1);
        buf.writeBytes("foo", 3);
        buf.writeInt8(0);
        buf.writeInt8((uint8_t)GenericHeader::Tag::TYPE_EMPTY);
        EXPECT_TRUE(testReadError(buf, "Can not deserialize empty tag."));
    }
}

bool
FileHeaderTest::testReadError(DataBuffer &buf, const std::string &expected)
{
    GenericHeader header;
    header.putTag(GenericHeader::Tag("foo", "bar"));
    try {
        GenericHeader::BufferReader reader(buf);
        header.read(reader);
        EXPECT_TRUE(false);
        return false;
    } catch (IllegalHeaderException &e) {
        bool failed = false;
        EXPECT_EQ(expected, e.getMessage()) << (failed = true, "");
        if (failed) {
            return false;
        }
    }
    bool failed = false;
    EXPECT_EQ(1u, header.getNumTags()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ("bar", header.getTag("foo").asString()) << (failed = true, "");
    return !failed;
}

TEST_F(FileHeaderTest, test_write_errors)
{
    GenericHeader header;
    header.putTag(GenericHeader::Tag("foo", 69));

    DataBuffer buf;
    buf.ensureFree(4);
    buf.moveFreeToData(buf.getFreeLen() - 4);
    EXPECT_TRUE(header.getSize() > buf.getFreeLen());
    try {
        GenericHeader::BufferWriter writer(buf);
        header.write(writer);
        EXPECT_TRUE(false);
    } catch (IllegalHeaderException &e) {
        EXPECT_EQ("Failed to write header.", e.getMessage());
    }

    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(69, header.getTag("foo").asInteger());
}

TEST_F(FileHeaderTest, test_rewrite_errors)
{
    FileHeader header;
    header.putTag(FileHeader::Tag("foo", "bar"));
    uint32_t len = header.getSize();

    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate(fileheader_tmp.c_str()));
        EXPECT_EQ(len, header.writeFile(file));
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadWrite(fileheader_tmp.c_str()));
        header.putTag(FileHeader::Tag("baz", "cox"));
        EXPECT_TRUE(len != header.getSize());
        try {
            header.rewriteFile(file);
            EXPECT_TRUE(false);
        } catch (IllegalHeaderException &e) {
            EXPECT_EQ("Failed to rewrite resized header.", e.getMessage());
        }
    }
}

TEST_F(FileHeaderTest, test_layout)
{
    FileHeader header;
    {
        FastOS_File file;
        const std::string fileName = TEST_PATH("fileheader.dat");
        ASSERT_TRUE(file.OpenReadOnly(fileName.c_str()));
        uint32_t len = header.readFile(file);
        EXPECT_EQ(len, header.getSize());
    }

    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQ(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQ(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQ("666999", header.getTag("baz").asString());
}


void
FileHeaderTest::testReadSize(bool mapped)
{
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(21);
        buf.writeInt32(GenericHeader::VERSION);
        buf.writeInt32(1);
        uint32_t headerLen;
        if (mapped) {
            GenericHeader::MMapReader reader(buf.getData(), buf.getDataLen());
            headerLen = FileHeader::readSize(reader);
        } else {
            GenericHeader::BufferReader reader(buf);
            headerLen = FileHeader::readSize(reader);
        }
        EXPECT_EQ(21u, headerLen);
}

TEST_F(FileHeaderTest, test_read_size_unmapped)
{
    testReadSize(false);
}

TEST_F(FileHeaderTest, test_read_size_mapped)
{
    testReadSize(true);
}

void
FileHeaderTest::testReadSizeErrors(bool mapped)
{
    {
        DataBuffer buf;
        EXPECT_TRUE(testReadSizeError(buf, "Failed to read header info.",
                                      mapped));
    }
    {
        DataBuffer buf;
        buf.writeInt32(0xDEADBEAF);
        buf.writeInt32(8);
        buf.writeInt32(0);
        buf.writeInt32(0);
        EXPECT_TRUE(testReadSizeError(buf, "Failed to verify magic bits.",
                                      mapped));
    }
    {
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(8);
        buf.writeInt32(GenericHeader::VERSION);
        buf.writeInt32(0);
        EXPECT_TRUE(testReadSizeError(buf, "Failed to verify header size.",
                                      mapped));
    }
    {
        DataBuffer buf;
        buf.writeInt32(GenericHeader::MAGIC);
        buf.writeInt32(16);
        buf.writeInt32(-1);
        buf.writeInt32(0);
        EXPECT_TRUE(testReadSizeError(buf,
                                      "Failed to verify header version.",
                                      mapped));
    }
}

TEST_F(FileHeaderTest, test_read_size_errors_unmapped)
{
    testReadSizeErrors(false);
}

TEST_F(FileHeaderTest, test_read_size_errors_mapped)
{
    testReadSizeErrors(true);
}

bool
FileHeaderTest::testReadSizeError(DataBuffer &buf, const std::string &expected,
                        bool mapped)
{
    uint32_t headerLen = 0u;
    try {
        if (mapped) {
            GenericHeader::MMapReader reader(buf.getData(), buf.getDataLen());
            headerLen = FileHeader::readSize(reader);
        } else {
            GenericHeader::BufferReader reader(buf);
            headerLen = FileHeader::readSize(reader);
        }
        EXPECT_TRUE(false);
        return false;
    } catch (IllegalHeaderException &e) {
        bool failed = false;
        EXPECT_EQ(expected, e.getMessage()) << (failed = true, "");
        if (failed) {
            return false;
        }
    }
    EXPECT_EQ(headerLen, 0u);
    return true;
}

GTEST_MAIN_RUN_ALL_TESTS()
