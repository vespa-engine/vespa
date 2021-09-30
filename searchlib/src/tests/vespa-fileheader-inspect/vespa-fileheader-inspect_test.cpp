// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>

using namespace search;
using namespace vespalib;

bool writeHeader(const FileHeader &header, const vespalib::string &fileName) {
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

vespalib::string readFile(const vespalib::string &fileName) {
    FastOS_File file;
    ASSERT_TRUE(file.OpenReadOnly(fileName.c_str()));

    char buf[4_Ki];
    uint32_t len = file.Read(buf, sizeof(buf));
    EXPECT_LESS(len, sizeof(buf)); // make sure we got everything

    vespalib::string str(buf, len);
    file.Close();
    return str;
}

TEST("testError") {
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect notfound.dat") != 0);
}

TEST("testEscape") {
    FileHeader header;
    header.putTag(FileHeader::Tag("fanart", "\fa\na\r\t"));
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -q fileheader.dat > out") == 0);
    EXPECT_EQUAL("fanart;string;\\fa\\na\\r\\t\n", readFile("out"));
}

TEST("testDelimiter") {
    FileHeader header;
    header.putTag(FileHeader::Tag("string", "string"));
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -d i -q fileheader.dat > out") == 0);
    EXPECT_EQUAL("str\\ingistr\\ingistr\\ing\n", readFile("out"));
}

TEST("testQuiet") {
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -q fileheader.dat > out") == 0);
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

TEST("testVerbose") {
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    ASSERT_TRUE(writeHeader(header, "fileheader.dat"));
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect fileheader.dat > out") == 0);
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

TEST_MAIN() { TEST_RUN_ALL(); }
