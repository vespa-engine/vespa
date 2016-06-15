// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("fileheaderinspect_test");

#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace search;
using namespace vespalib;

class Test : public vespalib::TestApp {
private:
    bool writeHeader(const FileHeader &header, const vespalib::string &fileName);
    vespalib::string readFile(const vespalib::string &fileName);

    void testError();
    void testEscape();
    void testDelimiter();
    void testQuiet();
    void testVerbose();

public:
    int Main() {
        TEST_INIT("fileheaderinspect_test");

        testError();     TEST_FLUSH();
        testEscape();    TEST_FLUSH();
        testDelimiter(); TEST_FLUSH();
        testQuiet();     TEST_FLUSH();
        testVerbose();   TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

bool
Test::writeHeader(const FileHeader &header, const vespalib::string &fileName)
{
    FastOS_File file;
    if (!EXPECT_TRUE(file.OpenWriteOnlyTruncate(fileName.c_str()))) {
        return false;
    }
    if (!EXPECT_EQUAL(header.getSize(), header.writeFile(file))) {
        return false;
    }
    file.Close();
    return true;
}

vespalib::string
Test::readFile(const vespalib::string &fileName)
{
    FastOS_File file;
    ASSERT_TRUE(file.OpenReadOnly(fileName.c_str()));

    char buf[1024];
    uint32_t len = file.Read(buf, 1024);
    EXPECT_TRUE(len != 1024); // make sure we got everything

    vespalib::string str(buf, len);
    file.Close();
    return str;
}

void
Test::testError()
{
    EXPECT_TRUE(system("../../apps/fileheaderinspect/vespa-header-inspect notfound.dat") != 0);
}

void
Test::testEscape()
{
    FileHeader header;
    header.putTag(FileHeader::Tag("fanart", "\fa\na\r\t"));
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/fileheaderinspect/vespa-header-inspect -q fileheader.dat > out") == 0);
    EXPECT_EQUAL("fanart;string;\\fa\\na\\r\\t\n", readFile("out"));
}

void
Test::testDelimiter()
{
    FileHeader header;
    header.putTag(FileHeader::Tag("string", "string"));
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/fileheaderinspect/vespa-header-inspect -d i -q fileheader.dat > out") == 0);
    EXPECT_EQUAL("str\\ingistr\\ingistr\\ing\n", readFile("out"));
}

void
Test::testVerbose()
{
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/fileheaderinspect/vespa-header-inspect fileheader.dat > out") == 0);
    vespalib::string str = readFile("out");
    EXPECT_TRUE(!str.empty());
    for (uint32_t i = 0, numTags = header.getNumTags(); i < numTags; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        EXPECT_TRUE(str.find(tag.getName()) != vespalib::string::npos);

        vespalib::asciistream out;
        out << tag;
        EXPECT_TRUE(str.find(out.str()) != vespalib::string::npos);
    }
}

void
Test::testQuiet()
{
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/fileheaderinspect/vespa-header-inspect -q fileheader.dat > out") == 0);
    vespalib::string str = readFile("out");
    EXPECT_TRUE(!str.empty());
    for (uint32_t i = 0, numTags = header.getNumTags(); i < numTags; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        size_t pos = str.find(tag.getName());
        EXPECT_TRUE(pos != vespalib::string::npos);

        vespalib::asciistream out;
        out << ";" << tag;
        EXPECT_TRUE(str.find(out.str(), pos) != vespalib::string::npos);
    }
}
