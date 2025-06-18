// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-function-test.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/configgen/configpayload.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <fstream>
#include <cinttypes>

#include <vespa/log/log.h>

LOG_SETUP("functiontest_test");

using namespace config;

namespace {

void
checkVariableAccess(const FunctionTestConfig & config)
{
    EXPECT_EQ(false, config.boolVal);
    EXPECT_EQ(true, config.boolWithDef);
    EXPECT_EQ(5, config.intVal);
    EXPECT_EQ(-14, config.intWithDef);
    EXPECT_EQ(12345678901L, config.longVal);
    EXPECT_EQ(-9876543210L, config.longWithDef);
    EXPECT_NEAR(41.23, config.doubleVal, 0.000001);
    EXPECT_NEAR(-12.0, config.doubleWithDef, 0.000001);
    EXPECT_EQ("foo", config.stringVal);
    EXPECT_EQ("bar", config.stringwithdef);
    EXPECT_EQ("FOOBAR", FunctionTestConfig::getEnumValName(config.enumVal));
    EXPECT_EQ("BAR2",
               FunctionTestConfig::getEnumwithdefName(config.enumwithdef));
    EXPECT_EQ(":parent:", config.refval);
    EXPECT_EQ(":parent:", config.refwithdef);
    EXPECT_EQ("etc", config.fileVal);
    EXPECT_EQ(1u, config.boolarr.size());
    EXPECT_EQ(0u, config.intarr.size());
    EXPECT_EQ(2u, config.longarr.size());
    LOG(error, "0: %" PRId64, config.longarr[0]);
    LOG(error, "1: %" PRId64, config.longarr[1]);
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), config.longarr[0]);
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), config.longarr[1]);
    EXPECT_EQ(2u, config.doublearr.size());
    EXPECT_EQ(1u, config.stringarr.size());
    EXPECT_EQ(1u, config.enumarr.size());
    EXPECT_EQ(3u, config.refarr.size());
    EXPECT_EQ(1u, config.fileArr.size());
    EXPECT_EQ("bin", config.fileArr[0]);

    EXPECT_EQ("basicFoo", config.basicStruct.foo);
    EXPECT_EQ(3, config.basicStruct.bar);
    EXPECT_EQ(1u, config.basicStruct.intArr.size());
    EXPECT_EQ(310, config.basicStruct.intArr[0]);
    EXPECT_EQ("inner0", config.rootStruct.inner0.name);
    EXPECT_EQ(11, config.rootStruct.inner0.index);
    EXPECT_EQ("inner1", config.rootStruct.inner1.name);
    EXPECT_EQ(12, config.rootStruct.inner1.index);
    EXPECT_EQ(1u, config.rootStruct.innerArr.size());
    EXPECT_EQ(true, config.rootStruct.innerArr[0].boolVal);
    EXPECT_EQ("deep", config.rootStruct.innerArr[0].stringVal);

    // TODO: replace ':parent:' with 'configId' when references are handled properly also in C++.
    EXPECT_EQ(2u, config.myarray.size());
    EXPECT_EQ(":parent:", config.myarray[0].refval);
    EXPECT_EQ("file0", config.myarray[0].fileVal);
    EXPECT_EQ(1, config.myarray[0].myStruct.a);
    EXPECT_EQ(2, config.myarray[0].myStruct.b);
    EXPECT_EQ(":parent:", config.myarray[1].refval);
    EXPECT_EQ("file1", config.myarray[1].fileVal);
    EXPECT_EQ(-1, config.myarray[1].myStruct.a);
    EXPECT_EQ(-2, config.myarray[1].myStruct.b);
}

std::string
readFile(const std::string & fileName)
{
    std::ifstream f(fileName.c_str());
    EXPECT_FALSE(f.fail());
    std::string content;
    std::string line;
    while (getline(f, line)) {
        content += line;
    }
    return content;
}

}

struct LazyTestFixture
{
    const DirSpec _spec;
    ConfigSubscriber _subscriber;
    ConfigHandle<FunctionTestConfig>::UP _handle;
    std::unique_ptr<FunctionTestConfig> _config;

    LazyTestFixture(const std::string & dirName);
    ~LazyTestFixture();
};

LazyTestFixture::LazyTestFixture(const std::string & dirName)
        : _spec(TEST_PATH(dirName)),
          _subscriber(_spec),
          _handle(_subscriber.subscribe<FunctionTestConfig>(""))
{ }
LazyTestFixture::~LazyTestFixture() { }

struct TestFixture : public LazyTestFixture
{
    TestFixture(const std::string & dirName)
        : LazyTestFixture(dirName)
    {
        EXPECT_TRUE(_subscriber.nextConfigNow());
        _config = _handle->getConfig();
    }
};

struct ErrorFixture
{
    LazyTestFixture & f;
    ErrorFixture(LazyTestFixture & f1) : f(f1) { }
    void run() {
        f._subscriber.nextConfigNow();
        bool thrown = false;
        try {
            f._handle->getConfig();
        } catch (const InvalidConfigException & e) {
            thrown = true;
            LOG(info, "Error: %s", e.getMessage().c_str());
        }
        ASSERT_TRUE(thrown);
    }
};

void attemptLacking(const std::string& param, bool isArray) {
    std::ifstream in(TEST_PATH("defaultvalues/function-test.cfg"), std::ios_base::in);
    std::ostringstream config;
    std::string s;
    while (std::getline(in, s)) {
        if (s.size() > param.size() &&
            s.substr(0, param.size()) == param &&
            (s[param.size()] == ' ' || s[param.size()] == '['))
        {
            // Ignore values matched
        } else {
            config << s << "\n";

        }
    }
    //std::cerr << "Config lacking " << param << "\n"
    //          << config.str() << "\n";
    try{
        RawSpec spec(config.str());
        ConfigSubscriber subscriber(spec);
        ConfigHandle<FunctionTestConfig>::UP handle = subscriber.subscribe<FunctionTestConfig>("foo");
        ASSERT_TRUE(subscriber.nextConfigNow());
        std::unique_ptr<FunctionTestConfig> cfg = handle->getConfig();
        if (isArray) {
            // Arrays are empty by default
            return;
        }
        FAIL() << "Expected to fail when not specifying value " << param <<  " without default";
    } catch (InvalidConfigException& e) {
        if (isArray) {
            FAIL() << "Arrays should be empty by default.";
        }
    }
}

TEST(FunctionTest, testVariableAccess)
{
    TestFixture f("variableaccess");
    checkVariableAccess(*f._config);
}


TEST(FunctionTest, test_variable_access_from_slime)
{
    vespalib::Slime slime;
    std::string json(readFile(TEST_PATH("slime-payload.json")));
    vespalib::slime::JsonFormat::decode(json, slime);
    FunctionTestConfig config(config::ConfigPayload(slime.get()));
    checkVariableAccess(config);
}

TEST(FunctionTest, testDefaultValues)
{
    TestFixture f("defaultvalues");
    EXPECT_EQ(false, f._config->boolVal);
    EXPECT_EQ(false, f._config->boolWithDef);
    EXPECT_EQ(5, f._config->intVal);
    EXPECT_EQ(-545, f._config->intWithDef);
    EXPECT_EQ(1234567890123L, f._config->longVal);
    EXPECT_EQ(-50000000000L, f._config->longWithDef);
    EXPECT_NEAR(41.23, f._config->doubleVal, 0.000001);
    EXPECT_NEAR(-6.43, f._config->doubleWithDef, 0.000001);
    EXPECT_EQ("foo", f._config->stringVal);
    EXPECT_EQ("foobar", f._config->stringwithdef);
    EXPECT_EQ("FOOBAR", FunctionTestConfig::getEnumValName(f._config->enumVal));
    EXPECT_EQ("BAR2",
               FunctionTestConfig::getEnumwithdefName(f._config->enumwithdef));
    EXPECT_EQ(":parent:", f._config->refval);
    EXPECT_EQ(":parent:", f._config->refwithdef);
    EXPECT_EQ("vespa.log", f._config->fileVal);
    EXPECT_EQ(1u, f._config->boolarr.size());
    EXPECT_EQ(0u, f._config->intarr.size());
    EXPECT_EQ(0u, f._config->longarr.size());
    EXPECT_EQ(2u, f._config->doublearr.size());
    EXPECT_EQ(1u, f._config->stringarr.size());
    EXPECT_EQ(1u, f._config->enumarr.size());
    EXPECT_EQ(0u, f._config->refarr.size());
    EXPECT_EQ(0u, f._config->fileArr.size());

    EXPECT_EQ(3, f._config->basicStruct.bar);
    EXPECT_EQ(1u, f._config->basicStruct.intArr.size());
    EXPECT_EQ(10, f._config->basicStruct.intArr[0]);
    EXPECT_EQ(11, f._config->rootStruct.inner0.index);
    EXPECT_EQ(12, f._config->rootStruct.inner1.index);
    EXPECT_EQ(1u, f._config->rootStruct.innerArr.size());
    EXPECT_EQ("deep", f._config->rootStruct.innerArr[0].stringVal);

    EXPECT_EQ(2u, f._config->myarray.size());
    EXPECT_EQ(1, f._config->myarray[0].myStruct.a);
    EXPECT_EQ(-1, f._config->myarray[1].myStruct.a);
    EXPECT_EQ("command.com", f._config->myarray[0].fileVal);
    EXPECT_EQ("display.sys", f._config->myarray[1].fileVal);
}

TEST(FunctionTest, testLackingDefaults)
{
    attemptLacking("bool_val", false);
    attemptLacking("int_val", false);
    attemptLacking("long_val", false);
    attemptLacking("double_val", false);
    attemptLacking("string_val", false);
    attemptLacking("enum_val", false);
    attemptLacking("refval", false);
    attemptLacking("fileVal", false);

    attemptLacking("boolarr", true);
    attemptLacking("intarr", true);
    attemptLacking("longarr", true);
    attemptLacking("doublearr", true);
    attemptLacking("enumarr", true);
    attemptLacking("stringarr", true);
    attemptLacking("refarr", true);
    attemptLacking("fileArr", true);
    attemptLacking("myarray", true);

    attemptLacking("basicStruct.bar", false);
    attemptLacking("rootStruct.inner0.index", false);
    attemptLacking("rootStruct.inner1.index", false);

    // NOTE: When this line is lacking in C++, the array will be empty, and no exception is thrown. In Java, the array
    //       is initialized to length 1 (by the preceeding line 'rootStruct.innerArr[1]'), and an exception is thrown
    //       when the value is lacking.
    attemptLacking("rootStruct.innerArr[0].stringVal", true);

    attemptLacking("myarray[0].stringval", true);
    attemptLacking("myarray[0].refval", false);
    attemptLacking("myarray[0].anotherarray", true);
    attemptLacking("myarray[0].anotherarray", true);
    attemptLacking("myarray[0].myStruct.a", false);
}

TEST(FunctionTest, testRandomOrder)
{
    TestFixture f("randomorder");
    EXPECT_EQ(false, f._config->boolVal);
    EXPECT_EQ(true, f._config->boolWithDef);
    EXPECT_EQ(5, f._config->intVal);
    EXPECT_EQ(-14, f._config->intWithDef);
    EXPECT_EQ(666000666000L, f._config->longVal);
    EXPECT_EQ(-333000333000L, f._config->longWithDef);
    EXPECT_NEAR(41.23, f._config->doubleVal, 0.000001);
    EXPECT_NEAR(-12.0, f._config->doubleWithDef, 0.000001);
    EXPECT_EQ("foo", f._config->stringVal);
    EXPECT_EQ("bar", f._config->stringwithdef);
    EXPECT_EQ("FOOBAR", FunctionTestConfig::getEnumValName(f._config->enumVal));
    EXPECT_EQ("BAR2",
    FunctionTestConfig::getEnumwithdefName(f._config->enumwithdef));
    EXPECT_EQ(":parent:", f._config->refval);
    EXPECT_EQ(":parent:", f._config->refwithdef);
    EXPECT_EQ("autoexec.bat", f._config->fileVal);
    EXPECT_EQ(1u, f._config->boolarr.size());
    EXPECT_EQ(0u, f._config->intarr.size());
    EXPECT_EQ(0u, f._config->longarr.size());
    EXPECT_EQ(2u, f._config->doublearr.size());
    EXPECT_EQ(1u, f._config->stringarr.size());
    EXPECT_EQ(1u, f._config->enumarr.size());
    EXPECT_EQ(0u, f._config->refarr.size());
    EXPECT_EQ(0u, f._config->fileArr.size());
    EXPECT_EQ(2u, f._config->myarray.size());
}

TEST(FunctionTest, testErrorRangeInt32)
{
    LazyTestFixture f1("errorval_int");
    ErrorFixture f2(f1);
    f2.run();
}

TEST(FunctionTest, testErrorRangeInt64)
{
    LazyTestFixture f1("errorval_long");
    ErrorFixture f2(f1);
    f2.run();
}

TEST(FunctionTest, testErrorRangeDouble)
{
    LazyTestFixture f1("errorval_double");
    ErrorFixture f2(f1);
    f2.run();
}

GTEST_MAIN_RUN_ALL_TESTS()
