// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include "config-motd.h"
#include <vespa/config/print.h>
#include <vespa/config/print/fileconfigreader.hpp>
#include <vespa/config/print/istreamconfigreader.hpp>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sys/stat.h>

using namespace config;

template <typename T>
struct RawFixture {
    RawSpec spec;
    std::unique_ptr<T> cfg;
    RawFixture()
        : spec("myField \"foo\"\n"),
          cfg(ConfigGetter<T>::getConfig("test", spec))
    { }
    ~RawFixture();
};

template <typename T>
RawFixture<T>::~RawFixture() = default;

TEST(PrintTest, requireThatConfigIsWrittenToFile)
{
    RawFixture<MyConfig> f;
    FileConfigWriter writer("test_1.json");
    ASSERT_TRUE(writer.write(*f.cfg, JsonConfigFormatter()));
    struct stat st;
    int ret = stat("test_1.json", &st);
    ASSERT_EQ(0, ret);
    ASSERT_TRUE(st.st_size > 0);
}

TEST(PrintTest, requireThatCanPrintAsJson)
{
    RawFixture<MyConfig> f;
    FileConfigWriter writer("test_2.json");
    ASSERT_TRUE(writer.write(*f.cfg, JsonConfigFormatter()));
    FileConfigReader<MyConfig> reader("test_2.json");
    std::unique_ptr<MyConfig> cfg2 = reader.read(JsonConfigFormatter());
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST(PrintTest, requireThatCanPrintToOstream)
{
    RawFixture<MyConfig> f;
    std::ostringstream ss;
    OstreamConfigWriter writer(ss);
    ASSERT_TRUE(writer.write(*f.cfg));
    ASSERT_EQ("myField \"foo\"\n", ss.str());
}

TEST(PrintTest, requireThatCanReadFromIstream)
{
    RawFixture<MyConfig> f;
    std::stringstream ss;
    ss << "myField \"foo\"\n";
    IstreamConfigReader<MyConfig> reader(ss);
    std::unique_ptr<MyConfig> cfg = reader.read();
    ASSERT_EQ(std::string("foo"), cfg->myField);
}

TEST(PrintTest, requireThatCanPrintToAscii)
{
    RawFixture<MyConfig> f;
    vespalib::asciistream ss;
    AsciiConfigWriter writer(ss);
    ASSERT_TRUE(writer.write(*f.cfg));
    ASSERT_EQ("myField \"foo\"\n", ss.view());
}

TEST(PrintTest, requireThatCanPrintAsConfigFormat)
{
    RawFixture<MyConfig> f;
    FileConfigWriter writer("test_3.cfg");
    ASSERT_TRUE(writer.write(*f.cfg));
    FileConfigReader<MyConfig> reader("test_3.cfg");
    std::unique_ptr<MyConfig> cfg2 = reader.read();
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST(PrintTest, require_that_invalid_file_throws_exception)
{
    RawFixture<MyConfig> f;
    FileConfigReader<MyConfig> reader("nonexistant.cfg");
    VESPA_EXPECT_EXCEPTION(reader.read(), vespalib::IllegalArgumentException, "Unable to open file");
}

TEST(PrintTest, requireThatCanLoadWrittenWithConfigFormat)
{
    RawFixture<MyConfig> f;
    FileConfigWriter writer("test_3.cfg");
    ASSERT_TRUE(writer.write(*f.cfg));
    std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig("test_3", FileSpec("test_3.cfg"));
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST(PrintTest, requireThatAllFieldsArePrintedCorrectly)
{
    std::unique_ptr<MotdConfig> cfg = ConfigGetter<MotdConfig>::getConfig(
        "motd", FileSpec(TEST_PATH("motd.cfg")));
    FileConfigWriter writer("motd2.cfg");
    ASSERT_TRUE(writer.write(*cfg, FileConfigFormatter()));
    std::unique_ptr<MotdConfig> cfg2 = ConfigGetter<MotdConfig>::getConfig(
        "motd2", FileSpec("motd2.cfg"));
    ASSERT_TRUE(*cfg2 == *cfg);
}

TEST(PrintTest, require_that_reading_cfg_format_throws_exception) {
    FileConfigReader<MyConfig> reader("test_1.json");
    VESPA_EXPECT_EXCEPTION(reader.read(FileConfigFormatter()), vespalib::IllegalArgumentException, "Reading cfg format is not supported");
}

GTEST_MAIN_RUN_ALL_TESTS()
