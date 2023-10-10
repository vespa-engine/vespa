// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vdstestlib/config/dirconfig.h>
#include <fstream>
#include <iostream>

using namespace vdstestlib;

namespace vespalib {

class Test : public vespalib::TestApp
{
public:
    void testNormalUsage();
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("dirconfig_test");
    srandom(1);
    testNormalUsage();
    TEST_DONE();
}

#define ASSERT_FILE_CONTENT(file, content) \
{ \
    std::ifstream in(file); \
    EXPECT_TRUE(bool(in)); \
    std::ostringstream ost; \
    std::string line; \
    while (getline(in, line, '\n')) { \
        ost << line << '\n'; \
    } \
    EXPECT_EQUAL(content, ost.str()); \
}

void Test::testNormalUsage() {
    DirConfig config1;
    DirConfig config2;
    EXPECT_EQUAL(strncmp("dir:dirconfig.tmp.", config1.getConfigId().c_str(), 18), 0);
    EXPECT_EQUAL(26u, config1.getConfigId().size());
    EXPECT_EQUAL('/', config1.getConfigId()[24]);
    EXPECT_EQUAL('0', config1.getConfigId()[25]);
    EXPECT_EQUAL(strncmp("dir:dirconfig.tmp.", config2.getConfigId().c_str(), 18), 0);
    EXPECT_EQUAL(26u, config2.getConfigId().size());
    EXPECT_EQUAL('/', config2.getConfigId()[24]);
    EXPECT_EQUAL('1', config2.getConfigId()[25]);

    try{
        config1.getConfig("testconfig");
        TEST_FATAL("Not supposed to get here");
    } catch (vespalib::Exception& e) {
        EXPECT_EQUAL("No config named testconfig", e.getMessage());
    }
    DirConfig::Config& file1(config1.addConfig("testconfig"));
    try{
        config1.addConfig("testconfig");
        TEST_FATAL("Not supposed to get here");
    } catch (vespalib::Exception& e) {
        EXPECT_EQUAL("There is already a config named testconfig",
                   e.getMessage());
    }
    EXPECT_EQUAL(&file1, &config1.getConfig("testconfig"));
    file1.set("intval", "5");
    file1.set("intval", "7");
    file1.set("stringval", "\"foo\"");
    file1.set("tmpval", "4");
    file1.remove("tmpval");
        // Trigger publish
    config1.getConfigId();

    ASSERT_FILE_CONTENT(config1.getDir() + "/testconfig.cfg",
                        "intval 7\n"
                        "stringval \"foo\"\n");

    DirConfig::Config& file2(config2.addConfig("testconfig"));
    file2.set("longval", "6");
    file2.clear();
    file2.set("intval", "4");

    DirConfig::Config& file3(config1.addConfig("config2"));
    file3.set("intval", "3");
    file3.set("myarray[2]");
    file3.set("myarray[0].foo", "4");
    file3.set("myarray[1].foo", "2");

    config1.publish();
    config2.publish();

    ASSERT_FILE_CONTENT(config2.getDir() + "/testconfig.cfg",
                        "intval 4\n");
    ASSERT_FILE_CONTENT(config1.getDir() + "/config2.cfg",
                        "intval 3\n"
                        "myarray[2]\n"
                        "myarray[0].foo 4\n"
                        "myarray[1].foo 2\n");

}

} // vespalib

TEST_APPHOOK(vespalib::Test)
