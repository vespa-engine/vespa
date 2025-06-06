// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/configupdate.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configvalue.h>
#include <vespa/config/common/configkey.h>
#include <vespa/config/common/errorcode.h>
#include <vespa/config/common/vespa_version.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <map>

using namespace config;

TEST(MiscTest, requireThatConfigUpdateWorks)
{
    StringVector lines;
    lines.push_back("foo");

    ConfigUpdate up(ConfigValue(lines, "myxxhash"), true, 1337);
    ASSERT_EQ(1337, up.getGeneration());
    ASSERT_TRUE(up.hasChanged());

    ConfigUpdate up2(ConfigValue(lines, "myxxhash2"), false, 1338);
    ASSERT_EQ(1338, up2.getGeneration());
    ASSERT_FALSE(up2.hasChanged());
}

TEST(MiscTest, requireThatConfigValueWorks)
{
    StringVector lines;
    lines.push_back("myFooField \"bar\"");
    ConfigValue v1(lines);
    ConfigValue v2(lines);
    ConfigValue v3(lines);
    lines.push_back("myFooField \"bar2\"");
    ConfigValue v4(lines);
    ASSERT_TRUE(v1 == v2);
    ASSERT_TRUE(v1 == v3);
}

TEST(MiscTest, requireThatConfigKeyWorks)
{
    ConfigKey key1("id1", "def1", "namespace1", "xxhash1");
    ConfigKey key2("id1", "def1", "namespace1", "xxhash1");
    ConfigKey key3("id2", "def1", "namespace1", "xxhash1");
    ConfigKey key4("id1", "def2", "namespace1", "xxhash1");
    ConfigKey key5("id1", "def1", "namespace2", "xxhash1");
    ConfigKey key6("id1", "def1", "namespace1", "xxhash2"); // Special case. xxhash64 does not matter, so should be qual to key1 and key2

    ASSERT_TRUE(key1 == key2);

    ASSERT_TRUE(key1 == key1);
    ASSERT_TRUE(key1 == key2);
    ASSERT_TRUE(key1 < key3);
    ASSERT_TRUE(key1 < key4);
    ASSERT_TRUE(key1 < key5);
    ASSERT_TRUE(key1 == key6);

    ASSERT_TRUE(key2 == key1);
    ASSERT_TRUE(key2 == key2);
    ASSERT_TRUE(key2 < key3);
    ASSERT_TRUE(key2 < key4);
    ASSERT_TRUE(key2 < key5);
    ASSERT_TRUE(key2 == key6);

    ASSERT_TRUE(key3 > key1);
    ASSERT_TRUE(key3 > key2);
    ASSERT_TRUE(key3 == key3);
    ASSERT_TRUE(key3 > key4);
    ASSERT_TRUE(key3 > key5);
    ASSERT_TRUE(key3 > key6);

    ASSERT_TRUE(key4 > key1);
    ASSERT_TRUE(key4 > key2);
    ASSERT_TRUE(key4 < key3);
    ASSERT_TRUE(key4 == key4);
    ASSERT_TRUE(key4 > key5);
    ASSERT_TRUE(key4 > key6);

    ASSERT_TRUE(key5 > key1);
    ASSERT_TRUE(key5 > key2);
    ASSERT_TRUE(key5 < key3);
    ASSERT_TRUE(key5 < key4);
    ASSERT_TRUE(key5 == key5);
    ASSERT_TRUE(key5 > key6);

    ASSERT_TRUE(key6 == key1);
    ASSERT_TRUE(key6 == key2); 
    ASSERT_TRUE(key6 < key3);
    ASSERT_TRUE(key6 < key4);
    ASSERT_TRUE(key6 < key5);
    ASSERT_TRUE(key6 == key6);

    std::map<ConfigKey, int> keymap;
    keymap[key1] = 1;
    keymap[key2] = 2;
    keymap[key3] = 3;
    keymap[key4] = 4;
    keymap[key5] = 5;

    ASSERT_EQ(2, keymap[key1]);
    ASSERT_EQ(2, keymap[key2]);
    ASSERT_EQ(3, keymap[key3]);
    ASSERT_EQ(4, keymap[key4]);
    ASSERT_EQ(5, keymap[key5]);
    keymap[key6] = 6;
    ASSERT_EQ(6, keymap[key1]);
    ASSERT_EQ(6, keymap[key2]);
    ASSERT_EQ(6, keymap[key6]);
}

TEST(MiscTest, require_that_config_key_initializes_schema)
{
    StringVector schema;
    schema.push_back("foo");
    schema.push_back("bar");
    ConfigKey key("id1", "def1", "namespace1", "xxhash1", schema);
    const StringVector &vref(key.getDefSchema());
    for (size_t i = 0; i < schema.size(); i++) {
        ASSERT_EQ(schema[i], vref[i]);
    }
}

TEST(MiscTest, require_that_error_codes_are_correctly_translated_to_strings)
{
#define ASSERT_CONFIG(name) ASSERT_EQ(#name, ErrorCode::getName(ErrorCode::name))
    ASSERT_CONFIG(UNKNOWN_CONFIG);
    ASSERT_CONFIG(UNKNOWN_DEFINITION);
    ASSERT_CONFIG(UNKNOWN_VERSION);
    ASSERT_CONFIG(UNKNOWN_CONFIGID);
    ASSERT_CONFIG(UNKNOWN_DEF_MD5);
    ASSERT_CONFIG(UNKNOWN_VESPA_VERSION);
    ASSERT_CONFIG(ILLEGAL_NAME);
    ASSERT_CONFIG(ILLEGAL_VERSION);
    ASSERT_CONFIG(ILLEGAL_CONFIGID);
    ASSERT_CONFIG(ILLEGAL_DEF_MD5);
    ASSERT_CONFIG(ILLEGAL_CONFIG_MD5);
    ASSERT_CONFIG(ILLEGAL_CONFIG_MD5);    
    ASSERT_CONFIG(ILLEGAL_TIMEOUT);
    ASSERT_CONFIG(ILLEGAL_TIMESTAMP);
    ASSERT_CONFIG(ILLEGAL_NAME_SPACE);
    ASSERT_CONFIG(ILLEGAL_PROTOCOL_VERSION);
    ASSERT_CONFIG(ILLEGAL_CLIENT_HOSTNAME);
    ASSERT_CONFIG(OUTDATED_CONFIG);
    ASSERT_CONFIG(INTERNAL_ERROR);
    ASSERT_CONFIG(APPLICATION_NOT_LOADED);
    ASSERT_CONFIG(INCONSISTENT_CONFIG_MD5);
    ASSERT_EQ("Unknown error", ErrorCode::getName(13434));
#undef ASSERT_CONFIG
}

TEST(MiscTest, require_that_source_spec_parses_protocol_version)
{
    const char * envName = "VESPA_CONFIG_PROTOCOL_VERSION";
    EXPECT_EQ(3, ServerSpec().protocolVersion());
    setenv(envName, "2", 1);
    EXPECT_EQ(2, ServerSpec().protocolVersion());
    setenv(envName, "3", 1);
    EXPECT_EQ(3, ServerSpec().protocolVersion());
    setenv(envName, "4", 1);
    EXPECT_EQ(3, ServerSpec().protocolVersion());
    setenv(envName, "illegal", 1);
    EXPECT_EQ(3, ServerSpec().protocolVersion());
    setenv(envName, "1", 1);
    EXPECT_EQ(1, ServerSpec().protocolVersion());
    unsetenv(envName);
}

TEST(MiscTest, require_that_source_spec_parses_trace_level)
{
    const char * envName = "VESPA_CONFIG_PROTOCOL_TRACELEVEL";
    EXPECT_EQ(0, ServerSpec().traceLevel());
    setenv(envName, "3", 1);
    EXPECT_EQ(3, ServerSpec().traceLevel());
    setenv(envName, "illegal", 1);
    EXPECT_EQ(0, ServerSpec().traceLevel());
    unsetenv(envName);
}

TEST(MiscTest, require_that_source_spec_parses_compression_type) {
    const char * envName = "VESPA_CONFIG_PROTOCOL_COMPRESSION";
    EXPECT_TRUE(CompressionType::LZ4 == ServerSpec().compressionType());
    setenv(envName, "UNCOMPRESSED", 1);
    EXPECT_TRUE(CompressionType::UNCOMPRESSED == ServerSpec().compressionType());
    setenv(envName, "illegal", 1);
    EXPECT_TRUE(CompressionType::LZ4 == ServerSpec().compressionType());
    setenv(envName, "LZ4", 1);
    EXPECT_TRUE(CompressionType::LZ4 == ServerSpec().compressionType());
    unsetenv(envName);
}

TEST(MiscTest, require_that_vespa_version_is_set)
{
    VespaVersion vespaVersion = VespaVersion::getCurrentVersion();
    std::string str = vespaVersion.toString();

    EXPECT_TRUE(str.length() > 0);
}


GTEST_MAIN_RUN_ALL_TESTS()
