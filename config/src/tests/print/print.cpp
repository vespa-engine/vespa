// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/print.h>
#include <vespa/config/print/fileconfigreader.hpp>
#include <vespa/config/print/istreamconfigreader.hpp>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include "config-my.h"
#include "config-motd.h"
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
};


TEST_F("requireThatConfigIsWrittenToFile", RawFixture<MyConfig>) {
    FileConfigWriter writer("test_1.json");
    ASSERT_TRUE(writer.write(*f.cfg, JsonConfigFormatter()));
    struct stat st;
    int ret = stat("test_1.json", &st);
    ASSERT_EQUAL(0, ret);
    ASSERT_TRUE(st.st_size > 0);
}

TEST_F("requireThatCanPrintAsJson", RawFixture<MyConfig>) {
    FileConfigWriter writer("test_2.json");
    ASSERT_TRUE(writer.write(*f.cfg, JsonConfigFormatter()));
    FileConfigReader<MyConfig> reader("test_2.json");
    std::unique_ptr<MyConfig> cfg2 = reader.read(JsonConfigFormatter());
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST_F("requireThatCanPrintToOstream", RawFixture<MyConfig>) {
    std::ostringstream ss;
    OstreamConfigWriter writer(ss);
    ASSERT_TRUE(writer.write(*f.cfg));
    ASSERT_EQUAL("myField \"foo\"\n", ss.str());
}

TEST_F("requireThatCanReadFromIstream", RawFixture<MyConfig>) {
    std::stringstream ss;
    ss << "myField \"foo\"\n";
    IstreamConfigReader<MyConfig> reader(ss);
    std::unique_ptr<MyConfig> cfg = reader.read();
    ASSERT_EQUAL(std::string("foo"), cfg->myField);
}

TEST_F("requireThatCanPrintToAscii", RawFixture<MyConfig>) {
    vespalib::asciistream ss;
    AsciiConfigWriter writer(ss);
    ASSERT_TRUE(writer.write(*f.cfg));
    ASSERT_EQUAL("myField \"foo\"\n", ss.str());
}

TEST_F("requireThatCanPrintAsConfigFormat", RawFixture<MyConfig>) {
    FileConfigWriter writer("test_3.cfg");
    ASSERT_TRUE(writer.write(*f.cfg));
    FileConfigReader<MyConfig> reader("test_3.cfg");
    std::unique_ptr<MyConfig> cfg2 = reader.read();
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST_F("require that invalid file throws exception", RawFixture<MyConfig>) {
    FileConfigReader<MyConfig> reader("nonexistant.cfg");
    EXPECT_EXCEPTION(reader.read(), vespalib::IllegalArgumentException, "Unable to open file");

}

TEST_F("requireThatCanLoadWrittenWithConfigFormat", RawFixture<MyConfig>) {
    FileConfigWriter writer("test_3.cfg");
    ASSERT_TRUE(writer.write(*f.cfg));
    std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig("test_3", FileSpec("test_3.cfg"));
    ASSERT_TRUE(*cfg2 == *f.cfg);
}

TEST("requireThatAllFieldsArePrintedCorrectly") {
    std::unique_ptr<MotdConfig> cfg = ConfigGetter<MotdConfig>::getConfig(
        "motd", FileSpec(TEST_PATH("motd.cfg")));
    FileConfigWriter writer("motd2.cfg");
    ASSERT_TRUE(writer.write(*cfg, FileConfigFormatter()));
    std::unique_ptr<MotdConfig> cfg2 = ConfigGetter<MotdConfig>::getConfig(
        "motd2", FileSpec("motd2.cfg"));
    ASSERT_TRUE(*cfg2 == *cfg);
}

TEST("require that reading cfg format throws exception") {
    FileConfigReader<MyConfig> reader("test_1.json");
    EXPECT_EXCEPTION(reader.read(FileConfigFormatter()), vespalib::IllegalArgumentException, "Reading cfg format is not supported");
}


TEST_MAIN() { TEST_RUN_ALL(); }
