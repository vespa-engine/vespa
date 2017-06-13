// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/config/config.h>
#include <vespa/config/common/exceptions.h>

namespace documentapi {

struct LoadTypeTest : public CppUnit::TestFixture {

    void testConfig();

    CPPUNIT_TEST_SUITE(LoadTypeTest);
    CPPUNIT_TEST(testConfig);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(LoadTypeTest);

#define ASSERT_CONFIG_FAILURE(configId, error) \
    try { \
        LoadTypeSet createdFromConfigId(vespalib::stringref(configId)); \
        CPPUNIT_FAIL("Config was expected to fail with error: " \
                     + string(error)); \
    } catch (config::InvalidConfigException& e) { \
        CPPUNIT_ASSERT_CONTAIN(string(error), e.getMessage()); \
    }

void
LoadTypeTest::testConfig()
{
        // Using id 0 is illegal. Reserved for default type.
    ASSERT_CONFIG_FAILURE(
            "raw:"
            "type[1]\n"
            "type[0].id 0\n"
            "type[0].name \"foo\"\n"
            "type[0].priority \"\"",
            "Load type identifiers need to be");
        // Using name "default" is illegal. Reserved for default type.
    ASSERT_CONFIG_FAILURE(
            "raw:"
            "type[1]\n"
            "type[0].id 1\n"
            "type[0].name \"default\"\n"
            "type[0].priority \"\"", "Load type names need to be");
        // Identifiers need to be unique.
    ASSERT_CONFIG_FAILURE(
            "raw:"
            "type[2]\n"
            "type[0].id 1\n"
            "type[0].name \"test\"\n"
            "type[0].priority \"\"\n"
            "type[1].id 1\n"
            "type[1].name \"testa\"\n"
            "type[1].priority \"\"",  "Load type identifiers need to be");
        // Names need to be unique.
    ASSERT_CONFIG_FAILURE(
            "raw:"
            "type[2]\n"
            "type[0].id 1\n"
            "type[0].name \"test\"\n"
            "type[0].priority \"\"\n"
            "type[1].id 2\n"
            "type[1].name \"test\"\n"
            "type[1].priority \"\"" , "Load type names need to be");
    LoadTypeSet set("raw:"
            "type[3]\n"
            "type[0].id 1\n"
            "type[0].name \"user\"\n"
            "type[0].priority \"\"\n"
            "type[1].id 2\n"
            "type[1].name \"maintenance\"\n"
            "type[1].priority \"\"\n"
            "type[2].id 3\n"
            "type[2].name \"put\"\n"
            "type[2].priority \"\""
    );
}

} // documentapi
