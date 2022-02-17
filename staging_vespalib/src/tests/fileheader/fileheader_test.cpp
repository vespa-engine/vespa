// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/fastos/file.h>

using namespace vespalib;

class Test : public vespalib::TestApp {
private:
    void testTag();
    void testTagErrors();
    void testTagIteration();
    void testGenericHeader();
    void testBufferReader();
    void testBufferWriter();
    void testBufferAccess();
    void testFileReader();
    void testFileWriter();
    void testFileHeader();
    void testFileAlign();
    void testFileSize();
    void testReadErrors();
    bool testReadError(DataBuffer &buf, const std::string &expected);
    void testWriteErrors();
    void testRewriteErrors();
    void testLayout();

    void testReadSize(bool mapped);
    void testReadSizeErrors(bool mapped);
    bool testReadSizeError(DataBuffer &buf, const std::string &expected, bool mapped);

public:
    int Main() override {
        TEST_INIT("fileheader_test");

        testTag();           TEST_FLUSH();
        testTagErrors();     TEST_FLUSH();
        testTagIteration();  TEST_FLUSH();
        testGenericHeader(); TEST_FLUSH();
        testBufferReader();  TEST_FLUSH();
        testBufferWriter();  TEST_FLUSH();
        testBufferAccess();  TEST_FLUSH();
        testFileReader();    TEST_FLUSH();
        testFileWriter();    TEST_FLUSH();
        testFileHeader();    TEST_FLUSH();
        testFileAlign();     TEST_FLUSH();
        testFileSize();      TEST_FLUSH();
        testReadErrors();    TEST_FLUSH();
        testWriteErrors();   TEST_FLUSH();
        testRewriteErrors(); TEST_FLUSH();
        testLayout();        TEST_FLUSH();
        testReadSize(false);      TEST_FLUSH();
        testReadSizeErrors(false); TEST_FLUSH();
        testReadSize(true);      TEST_FLUSH();
        testReadSizeErrors(true); TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

void
Test::testTag()
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
                EXPECT_EQUAL(GenericHeader::Tag::TYPE_FLOAT, tag.getType());
                EXPECT_EQUAL("foo", tag.getName());
                EXPECT_TRUE(tag.asString().empty());
                EXPECT_APPROX(6.9, tag.asFloat(), 1E-6);
                EXPECT_EQUAL(0, tag.asInteger());

                uint32_t len = tag.getSize();
                DataBuffer buf(len);
                EXPECT_EQUAL(len, tag.write(buf));

                GenericHeader::Tag tmp;
                EXPECT_EQUAL(len, tmp.read(buf));
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
                EXPECT_EQUAL(GenericHeader::Tag::TYPE_INTEGER, tag.getType());
                EXPECT_EQUAL("foo", tag.getName());
                EXPECT_TRUE(tag.asString().empty());
                EXPECT_EQUAL(0.0, tag.asFloat());
                EXPECT_EQUAL(69l, tag.asInteger());

                uint32_t len = tag.getSize();
                DataBuffer buf(len);
                EXPECT_EQUAL(len, tag.write(buf));

                GenericHeader::Tag tmp;
                EXPECT_EQUAL(len, tmp.read(buf));
                tag = tmp;
            }
        }
    }
    {
        GenericHeader::Tag tag("foo", "bar");
        for (uint32_t i = 0; i < 2; ++i) {
            EXPECT_EQUAL(GenericHeader::Tag::TYPE_STRING, tag.getType());
            EXPECT_EQUAL("foo", tag.getName());
            EXPECT_EQUAL("bar", tag.asString());
            EXPECT_EQUAL(0.0, tag.asFloat());
            EXPECT_EQUAL(0, tag.asInteger());

            uint32_t len = tag.getSize();
            DataBuffer buf(len);
            EXPECT_EQUAL(len, tag.write(buf));

            GenericHeader::Tag tmp;
            EXPECT_EQUAL(len, tmp.read(buf));
            tag = tmp;
        }
    }
    {
        GenericHeader::Tag trueTag("foo", true);
        GenericHeader::Tag falseTag("foo", false);
        EXPECT_EQUAL(GenericHeader::Tag::TYPE_INTEGER, trueTag.getType());
        EXPECT_EQUAL(GenericHeader::Tag::TYPE_INTEGER, falseTag.getType());
        EXPECT_EQUAL(1, trueTag.asInteger());
        EXPECT_EQUAL(0, falseTag.asInteger());
        EXPECT_TRUE(trueTag.asBool());
        EXPECT_FALSE(falseTag.asBool());
    }
}

void
Test::testTagErrors()
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
        EXPECT_EQUAL("Can not deserialize empty tag.", e.getMessage());
    }
    EXPECT_EQUAL("bar", tag.getName());
    EXPECT_EQUAL(GenericHeader::Tag::TYPE_FLOAT, tag.getType());
    EXPECT_EQUAL(6.9, tag.asFloat());
}

void
Test::testTagIteration()
{
    GenericHeader header;
    header.putTag(GenericHeader::Tag("foo", 6.9));
    header.putTag(GenericHeader::Tag("bar", 6699));
    header.putTag(GenericHeader::Tag("baz", "666999"));

    EXPECT_EQUAL(3u, header.getNumTags());
    EXPECT_EQUAL("bar", header.getTag(0).getName());
    EXPECT_EQUAL("baz", header.getTag(1).getName());
    EXPECT_EQUAL("foo", header.getTag(2).getName());
}

void
Test::testGenericHeader()
{
    GenericHeader header;
    EXPECT_TRUE(header.isEmpty());
    EXPECT_EQUAL(0u, header.getNumTags());
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("foo", 6.9));
    EXPECT_TRUE(!header.isEmpty());
    EXPECT_EQUAL(1u, header.getNumTags());
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("bar", 6699));
    EXPECT_TRUE(!header.isEmpty());
    EXPECT_EQUAL(2u, header.getNumTags());
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQUAL(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());

    header.putTag(GenericHeader::Tag("baz", "666999"));
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQUAL(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQUAL("666999", header.getTag("baz").asString());

    header.removeTag("bar");
    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQUAL("666999", header.getTag("baz").asString());

    header.removeTag("foo");
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQUAL("666999", header.getTag("baz").asString());

    header.removeTag("baz");
    EXPECT_TRUE(!header.hasTag("foo"));
    EXPECT_TRUE(header.getTag("foo").isEmpty());
    EXPECT_TRUE(!header.hasTag("bar"));
    EXPECT_TRUE(header.getTag("bar").isEmpty());
    EXPECT_TRUE(!header.hasTag("baz"));
    EXPECT_TRUE(header.getTag("baz").isEmpty());
}

void
Test::testBufferReader()
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
            EXPECT_EQUAL(sum + i, (uint8_t)dst[i]);
        }
        sum += len;
    }
    EXPECT_EQUAL(256u, sum);
}

void
Test::testBufferWriter()
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
        EXPECT_EQUAL(len, (uint32_t)writer.putData(src, len));
        sum += len;
    }
    EXPECT_EQUAL(256u, sum);

    // flip dst
    for (uint32_t i = 0; i < 256; ++i) {
        uint8_t b = dst.readInt8();
        EXPECT_EQUAL(i, (uint32_t)b);
    }
}

void
Test::testBufferAccess()
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
        EXPECT_EQUAL(len, header.write(writer));
    }
    {
        GenericHeader header;
        GenericHeader::BufferReader reader(buf);
        EXPECT_EQUAL(len, header.read(reader));

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQUAL(6699, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQUAL("666999", header.getTag("baz").asString());
        EXPECT_TRUE(header.hasTag("big"));
        EXPECT_EQUAL(0x1234567890abcdefLL, header.getTag("big").asInteger());
    }
}

void
Test::testFileReader()
{
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate("fileheader.tmp"));

        uint8_t buf[256];
        for (uint32_t i = 0; i < 256; ++i) {
            buf[i] = (uint8_t)i;
        }
        EXPECT_EQUAL(256, file.Write2(buf, 256));
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly("fileheader.tmp"));
        FileHeader::FileReader reader(file);

        char buf[7];
        uint32_t sum = 0;
        while(sum < 256) {
            uint32_t len = (uint32_t)reader.getData(buf, 7);
            for (uint32_t i = 0; i < len; ++i) {
                EXPECT_EQUAL(sum + i, (uint8_t)buf[i]);
            }
            sum += len;
        }
        EXPECT_EQUAL(256u, sum);

        ASSERT_TRUE(file.Close());
        file.Delete();
    }
}

void
Test::testFileWriter()
{
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate("fileheader.tmp"));
        FileHeader::FileWriter writer(file);

        uint32_t sum = 0;
        while(sum < 256) {
            char src[7];
            for (uint32_t i = 0; i < 7; ++i) {
                src[i] = (uint8_t)(sum + i);
            }
            uint32_t len = std::min(7u, 256 - sum);
            EXPECT_EQUAL(len, (uint32_t)writer.putData(src, len));
            sum += len;
        }
        EXPECT_EQUAL(256u, sum);
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly("fileheader.tmp"));

        uint8_t buf[256];
        EXPECT_EQUAL(256, file.Read(buf, 256));
        for (uint32_t i = 0; i < 256; ++i) {
            EXPECT_EQUAL(i, (uint32_t)buf[i]);
        }

        ASSERT_TRUE(file.Close());
        file.Delete();
    }
}

void
Test::testFileHeader()
{
    uint32_t len = 0;
    {
        FileHeader header;
        header.putTag(FileHeader::Tag("foo", 6.9));
        header.putTag(FileHeader::Tag("bar", 6699));
        header.putTag(FileHeader::Tag("baz", "666999"));

        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate("fileheader.tmp"));
        len = header.writeFile(file);
        EXPECT_EQUAL(len, header.getSize());
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadWrite("fileheader.tmp"));

        FileHeader header;
        EXPECT_EQUAL(len, header.readFile(file));
        EXPECT_EQUAL(len, header.getSize());

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQUAL(6699, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQUAL("666999", header.getTag("baz").asString());

        header.putTag(FileHeader::Tag("foo", 9.6));
        header.putTag(FileHeader::Tag("bar", 9966));
        header.putTag(FileHeader::Tag("baz", "999666"));
        EXPECT_EQUAL(len, header.getSize());
        EXPECT_EQUAL(len, header.rewriteFile(file));
    }
    {
        FileHeader header;

        FastOS_File file;
        ASSERT_TRUE(file.OpenReadOnly("fileheader.tmp"));
        EXPECT_EQUAL(len, header.readFile(file));
        EXPECT_EQUAL(len, header.getSize());
        ASSERT_TRUE(file.Close());
        file.Delete();

        EXPECT_TRUE(header.hasTag("foo"));
        EXPECT_EQUAL(9.6, header.getTag("foo").asFloat());
        EXPECT_TRUE(header.hasTag("bar"));
        EXPECT_EQUAL(9966, header.getTag("bar").asInteger());
        EXPECT_TRUE(header.hasTag("baz"));
        EXPECT_EQUAL("999666", header.getTag("baz").asString());
    }
}

void
Test::testFileAlign()
{
    for (uint32_t alignTo = 1; alignTo < 16; ++alignTo) {
        FileHeader header(alignTo);
        header.putTag(FileHeader::Tag("foo", "bar"));
        EXPECT_EQUAL(0u, header.getSize() % alignTo);
    }
}

void
Test::testFileSize()
{
    for (uint32_t minSize = 0; minSize < 512; ++minSize) {
        FileHeader header(1u, minSize);
        header.putTag(FileHeader::Tag("foo", "bar"));
        EXPECT_TRUE(header.getSize() >= minSize);
    }
}

void
Test::testReadErrors()
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
Test::testReadError(DataBuffer &buf, const std::string &expected)
{
    GenericHeader header;
    header.putTag(GenericHeader::Tag("foo", "bar"));
    try {
        GenericHeader::BufferReader reader(buf);
        header.read(reader);
        EXPECT_TRUE(false);
        return false;
    } catch (IllegalHeaderException &e) {
        if (!EXPECT_EQUAL(expected, e.getMessage())) {
            return false;
        }
    }
    if (!EXPECT_EQUAL(1u, header.getNumTags())) {
        return false;
    }
    if (!EXPECT_EQUAL("bar", header.getTag("foo").asString())) {
        return false;
    }
    return true;
}

void
Test::testWriteErrors()
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
        EXPECT_EQUAL("Failed to write header.", e.getMessage());
    }

    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(69, header.getTag("foo").asInteger());
}

void
Test::testRewriteErrors()
{
    FileHeader header;
    header.putTag(FileHeader::Tag("foo", "bar"));
    uint32_t len = header.getSize();

    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenWriteOnlyTruncate("fileheader.tmp"));
        EXPECT_EQUAL(len, header.writeFile(file));
    }
    {
        FastOS_File file;
        ASSERT_TRUE(file.OpenReadWrite("fileheader.tmp"));
        header.putTag(FileHeader::Tag("baz", "cox"));
        EXPECT_TRUE(len != header.getSize());
        try {
            header.rewriteFile(file);
            EXPECT_TRUE(false);
        } catch (IllegalHeaderException &e) {
            EXPECT_EQUAL("Failed to rewrite resized header.", e.getMessage());
        }
    }
}

void
Test::testLayout()
{
    FileHeader header;
    {
        FastOS_File file;
        const std::string fileName = TEST_PATH("fileheader.dat");
        ASSERT_TRUE(file.OpenReadOnly(fileName.c_str()));
        uint32_t len = header.readFile(file);
        EXPECT_EQUAL(len, header.getSize());
    }

    EXPECT_TRUE(header.hasTag("foo"));
    EXPECT_EQUAL(6.9, header.getTag("foo").asFloat());
    EXPECT_TRUE(header.hasTag("bar"));
    EXPECT_EQUAL(6699, header.getTag("bar").asInteger());
    EXPECT_TRUE(header.hasTag("baz"));
    EXPECT_EQUAL("666999", header.getTag("baz").asString());
}


void
Test::testReadSize(bool mapped)
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
        EXPECT_EQUAL(21u, headerLen);
}


void
Test::testReadSizeErrors(bool mapped)
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


bool
Test::testReadSizeError(DataBuffer &buf, const std::string &expected,
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
        if (!EXPECT_EQUAL(expected, e.getMessage())) {
            return false;
        }
    }
    EXPECT_EQUAL(headerLen, 0u);
    return true;
}

