// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>

using namespace search;
using namespace vespalib;

void writeHeader(const FileHeader &header, const std::string &fileName) {
    FastOS_File file;
    ASSERT_TRUE(file.OpenWriteOnlyTruncate(fileName.c_str()));
    ASSERT_EQ(header.getSize(), header.writeFile(file));
}

std::string readFile(const std::string &fileName) {
    FastOS_File file;
    bool success = file.OpenReadOnly(fileName.c_str());
    assert(success);

    char buf[4_Ki];
    uint32_t len = file.Read(buf, sizeof(buf));
    EXPECT_LT(len, sizeof(buf)); // make sure we got everything

    std::string str(buf, len);
    return str;
}

TEST(VespaFileheaderInspectTest, testError) {
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect notfound.dat") != 0);
}

TEST(VespaFileheaderInspectTest, testEscape) {
    FileHeader header;
    header.putTag(FileHeader::Tag("fanart", "\fa\na\r\t"));
    writeHeader(header, "fileheader.dat");
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -q fileheader.dat > out") == 0);
    EXPECT_EQ("fanart;string;\\fa\\na\\r\\t\n", readFile("out"));
}

TEST(VespaFileheaderInspectTest, testDelimiter) {
    FileHeader header;
    header.putTag(FileHeader::Tag("string", "string"));
    writeHeader(header, "fileheader.dat");
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -d i -q fileheader.dat > out") == 0);
    EXPECT_EQ("str\\ingistr\\ingistr\\ing\n", readFile("out"));
}

TEST(VespaFileheaderInspectTest, testQuiet) {
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    writeHeader(header, "fileheader.dat");
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect -q fileheader.dat > out") == 0);
    std::string str = readFile("out");
    EXPECT_TRUE(!str.empty());
    for (uint32_t i = 0, numTags = header.getNumTags(); i < numTags; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        size_t pos = str.find(tag.getName());
        EXPECT_TRUE(pos != std::string::npos);

        vespalib::asciistream out;
        out << ";" << tag;
        EXPECT_TRUE(str.find(out.str(), pos) != std::string::npos);
    }
}

TEST(VespaFileheaderInspectTest, testVerbose) {
    FileHeader header;
    FileHeaderTk::addVersionTags(header);
    writeHeader(header, "fileheader.dat");
    EXPECT_TRUE(system("../../apps/vespa-fileheader-inspect/vespa-fileheader-inspect fileheader.dat > out") == 0);
    std::string str = readFile("out");
    EXPECT_TRUE(!str.empty());
    for (uint32_t i = 0, numTags = header.getNumTags(); i < numTags; ++i) {
        const FileHeader::Tag &tag = header.getTag(i);
        EXPECT_TRUE(str.find(tag.getName()) != std::string::npos);

        vespalib::asciistream out;
        out << tag;
        EXPECT_TRUE(str.find(out.str()) != std::string::npos);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
