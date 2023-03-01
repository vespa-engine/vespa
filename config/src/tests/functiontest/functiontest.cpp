// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-function-test.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/config/configgen/configpayload.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <fstream>
#include <cinttypes>

#include <vespa/log/log.h>

LOG_SETUP("functiontest_test");

using namespace config;

namespace {

void
checkVariableAccess(const FunctionTestConfig & config)
{
    EXPECT_EQUAL(false, config.boolVal);
    EXPECT_EQUAL(true, config.boolWithDef);
    EXPECT_EQUAL(5, config.intVal);
    EXPECT_EQUAL(-14, config.intWithDef);
    EXPECT_EQUAL(12345678901LL, config.longVal);
    EXPECT_EQUAL(-9876543210LL, config.longWithDef);
    EXPECT_APPROX(41.23, config.doubleVal, 0.000001);
    EXPECT_APPROX(-12, config.doubleWithDef, 0.000001);
    EXPECT_EQUAL("foo", config.stringVal);
    EXPECT_EQUAL("bar", config.stringwithdef);
    EXPECT_EQUAL("FOOBAR", FunctionTestConfig::getEnumValName(config.enumVal));
    EXPECT_EQUAL("BAR2",
               FunctionTestConfig::getEnumwithdefName(config.enumwithdef));
    EXPECT_EQUAL(":parent:", config.refval);
    EXPECT_EQUAL(":parent:", config.refwithdef);
    EXPECT_EQUAL("etc", config.fileVal);
    EXPECT_EQUAL(1u, config.boolarr.size());
    EXPECT_EQUAL(0u, config.intarr.size());
    EXPECT_EQUAL(2u, config.longarr.size());
    LOG(error, "0: %" PRId64, config.longarr[0]);
    LOG(error, "1: %" PRId64, config.longarr[1]);
    EXPECT_EQUAL(std::numeric_limits<int64_t>::max(), config.longarr[0]);
    EXPECT_EQUAL(std::numeric_limits<int64_t>::min(), config.longarr[1]);
    EXPECT_EQUAL(2u, config.doublearr.size());
    EXPECT_EQUAL(1u, config.stringarr.size());
    EXPECT_EQUAL(1u, config.enumarr.size());
    EXPECT_EQUAL(3u, config.refarr.size());
    EXPECT_EQUAL(1u, config.fileArr.size());
    EXPECT_EQUAL("bin", config.fileArr[0]);

    EXPECT_EQUAL("basicFoo", config.basicStruct.foo);
    EXPECT_EQUAL(3, config.basicStruct.bar);
    EXPECT_EQUAL(1u, config.basicStruct.intArr.size());
    EXPECT_EQUAL(310, config.basicStruct.intArr[0]);
    EXPECT_EQUAL("inner0", config.rootStruct.inner0.name);
    EXPECT_EQUAL(11, config.rootStruct.inner0.index);
    EXPECT_EQUAL("inner1", config.rootStruct.inner1.name);
    EXPECT_EQUAL(12, config.rootStruct.inner1.index);
    EXPECT_EQUAL(1u, config.rootStruct.innerArr.size());
    EXPECT_EQUAL(true, config.rootStruct.innerArr[0].boolVal);
    EXPECT_EQUAL("deep", config.rootStruct.innerArr[0].stringVal);

    // TODO: replace ':parent:' with 'configId' when references are handled properly also in C++.
    EXPECT_EQUAL(2u, config.myarray.size());
    EXPECT_EQUAL(":parent:", config.myarray[0].refval);
    EXPECT_EQUAL("file0", config.myarray[0].fileVal);
    EXPECT_EQUAL(1, config.myarray[0].myStruct.a);
    EXPECT_EQUAL(2, config.myarray[0].myStruct.b);
    EXPECT_EQUAL(":parent:", config.myarray[1].refval);
    EXPECT_EQUAL("file1", config.myarray[1].fileVal);
    EXPECT_EQUAL(-1, config.myarray[1].myStruct.a);
    EXPECT_EQUAL(-2, config.myarray[1].myStruct.b);
}

std::string
readFile(const std::string & fileName)
{
    std::ifstream f(fileName.c_str());
    ASSERT_FALSE(f.fail());
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
        ASSERT_TRUE(_subscriber.nextConfigNow());
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
        TEST_FATAL(("Expected to fail when not specifying value " + param
                + " without default").c_str());
    } catch (InvalidConfigException& e) {
        if (isArray) {
            TEST_FATAL("Arrays should be empty by default.");
        }
    }
}

TEST_F("testVariableAccess", TestFixture("variableaccess")) {
    checkVariableAccess(*f._config);
}


TEST("test variable access from slime") {
    vespalib::Slime slime;
    std::string json(readFile(TEST_PATH("slime-payload.json")));
    vespalib::slime::JsonFormat::decode(json, slime);
    FunctionTestConfig config(config::ConfigPayload(slime.get()));
    checkVariableAccess(config);
}

TEST_F("testDefaultValues", TestFixture("defaultvalues")) {
    EXPECT_EQUAL(false, f._config->boolVal);
    EXPECT_EQUAL(false, f._config->boolWithDef);
    EXPECT_EQUAL(5, f._config->intVal);
    EXPECT_EQUAL(-545, f._config->intWithDef);
    EXPECT_EQUAL(1234567890123LL, f._config->longVal);
    EXPECT_EQUAL(-50000000000LL, f._config->longWithDef);
    EXPECT_APPROX(41.23, f._config->doubleVal, 0.000001);
    EXPECT_APPROX(-6.43, f._config->doubleWithDef, 0.000001);
    EXPECT_EQUAL("foo", f._config->stringVal);
    EXPECT_EQUAL("foobar", f._config->stringwithdef);
    EXPECT_EQUAL("FOOBAR", FunctionTestConfig::getEnumValName(f._config->enumVal));
    EXPECT_EQUAL("BAR2",
               FunctionTestConfig::getEnumwithdefName(f._config->enumwithdef));
    EXPECT_EQUAL(":parent:", f._config->refval);
    EXPECT_EQUAL(":parent:", f._config->refwithdef);
    EXPECT_EQUAL("vespa.log", f._config->fileVal);
    EXPECT_EQUAL(1u, f._config->boolarr.size());
    EXPECT_EQUAL(0u, f._config->intarr.size());
    EXPECT_EQUAL(0u, f._config->longarr.size());
    EXPECT_EQUAL(2u, f._config->doublearr.size());
    EXPECT_EQUAL(1u, f._config->stringarr.size());
    EXPECT_EQUAL(1u, f._config->enumarr.size());
    EXPECT_EQUAL(0u, f._config->refarr.size());
    EXPECT_EQUAL(0u, f._config->fileArr.size());

    EXPECT_EQUAL(3, f._config->basicStruct.bar);
    EXPECT_EQUAL(1u, f._config->basicStruct.intArr.size());
    EXPECT_EQUAL(10, f._config->basicStruct.intArr[0]);
    EXPECT_EQUAL(11, f._config->rootStruct.inner0.index);
    EXPECT_EQUAL(12, f._config->rootStruct.inner1.index);
    EXPECT_EQUAL(1u, f._config->rootStruct.innerArr.size());
    EXPECT_EQUAL("deep", f._config->rootStruct.innerArr[0].stringVal);

    EXPECT_EQUAL(2u, f._config->myarray.size());
    EXPECT_EQUAL(1, f._config->myarray[0].myStruct.a);
    EXPECT_EQUAL(-1, f._config->myarray[1].myStruct.a);
    EXPECT_EQUAL("command.com", f._config->myarray[0].fileVal);
    EXPECT_EQUAL("display.sys", f._config->myarray[1].fileVal);
}

TEST("testLackingDefaults") {
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

TEST_F("testRandomOrder", TestFixture("randomorder")) {
    EXPECT_EQUAL(false, f._config->boolVal);
    EXPECT_EQUAL(true, f._config->boolWithDef);
    EXPECT_EQUAL(5, f._config->intVal);
    EXPECT_EQUAL(-14, f._config->intWithDef);
    EXPECT_EQUAL(666000666000LL, f._config->longVal);
    EXPECT_EQUAL(-333000333000LL, f._config->longWithDef);
    EXPECT_APPROX(41.23, f._config->doubleVal, 0.000001);
    EXPECT_APPROX(-12, f._config->doubleWithDef, 0.000001);
    EXPECT_EQUAL("foo", f._config->stringVal);
    EXPECT_EQUAL("bar", f._config->stringwithdef);
    EXPECT_EQUAL("FOOBAR", FunctionTestConfig::getEnumValName(f._config->enumVal));
    EXPECT_EQUAL("BAR2",
    FunctionTestConfig::getEnumwithdefName(f._config->enumwithdef));
    EXPECT_EQUAL(":parent:", f._config->refval);
    EXPECT_EQUAL(":parent:", f._config->refwithdef);
    EXPECT_EQUAL("autoexec.bat", f._config->fileVal);
    EXPECT_EQUAL(1u, f._config->boolarr.size());
    EXPECT_EQUAL(0u, f._config->intarr.size());
    EXPECT_EQUAL(0u, f._config->longarr.size());
    EXPECT_EQUAL(2u, f._config->doublearr.size());
    EXPECT_EQUAL(1u, f._config->stringarr.size());
    EXPECT_EQUAL(1u, f._config->enumarr.size());
    EXPECT_EQUAL(0u, f._config->refarr.size());
    EXPECT_EQUAL(0u, f._config->fileArr.size());
    EXPECT_EQUAL(2u, f._config->myarray.size());
}

TEST_FF("testErrorRangeInt32", LazyTestFixture("errorval_int"), ErrorFixture(f1)) { f2.run(); }
TEST_FF("testErrorRangeInt64", LazyTestFixture("errorval_long"), ErrorFixture(f1)) { f2.run(); }
TEST_FF("testErrorRangeDouble", LazyTestFixture("errorval_double"), ErrorFixture(f1)) { f2.run(); }

#if 0
TEST_F("testEquality", TestFixture("variableaccess")) {
    FunctionTestConfig myconfig(*f._config);
    EXPECT_EQUAL(_config, myconfig);
    myconfig.intVal = 2;
    EXPECT_TRUE(_config != myconfig);
    EXPECT_TRUE(_config.myarray == myconfig.myarray);
    myconfig.myarray[1].anotherarray[1].foo = 5;
    EXPECT_TRUE(_config.myarray != myconfig.myarray);
    EXPECT_EQUAL(_config.myarray[0], myconfig.myarray[0]);
    EXPECT_EQUAL(_config.myarray[1].refval, myconfig.myarray[1].refval);
}
#endif

TEST_MAIN() { TEST_RUN_ALL(); }
