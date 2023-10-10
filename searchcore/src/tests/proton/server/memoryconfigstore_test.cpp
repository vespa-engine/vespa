// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for memoryconfigstore.

#include <vespa/log/log.h>
LOG_SETUP("memoryconfigstore_test");

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/testkit/testapp.h>

using search::SerialNum;
using search::index::Schema;
using search::index::schema::DataType;

using namespace proton;

namespace {

DocumentDBConfig::SP
getConfig(int64_t generation, const Schema::SP &schema)
{
    return test::DocumentDBConfigBuilder(generation, schema, "client", "test").build();
}


DocumentDBConfig::SP
getConfig(int64_t generation)
{
    return getConfig(generation, Schema::SP());
}


TEST("require that configs can be stored and loaded") {
    MemoryConfigStore config_store;
    SerialNum serial(12);
    config_store.saveConfig(*getConfig(10), serial);
    DocumentDBConfig::SP config;
    config_store.loadConfig(*getConfig(14), serial, config);
    ASSERT_TRUE(config.get());
    EXPECT_EQUAL(10, config->getGeneration());
}

TEST("require that best serial number is the most recent one") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQUAL(5u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQUAL(5u, config_store.getBestSerialNum());
}

TEST("require that oldest serial number is the first one or 0") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQUAL(5u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQUAL(2u, config_store.getOldestSerialNum());
}

TEST("require that existing serial numbers are valid") {
    MemoryConfigStore config_store;
    EXPECT_FALSE(config_store.hasValidSerial(5));
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_TRUE(config_store.hasValidSerial(5));
}

TEST("require that prev valid serial number is the last one before the arg") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(10));
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQUAL(5u, config_store.getPrevValidSerial(10));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(5));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(4));
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(1));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(2));
    EXPECT_EQUAL(2u, config_store.getPrevValidSerial(4));
    EXPECT_EQUAL(2u, config_store.getPrevValidSerial(5));
    EXPECT_EQUAL(5u, config_store.getPrevValidSerial(10));
}

TEST("require that prune removes old configs") {
    MemoryConfigStore config_store;
    config_store.saveConfig(*getConfig(10), 5);
    config_store.saveConfig(*getConfig(10), 6);
    EXPECT_TRUE(config_store.hasValidSerial(5));
    config_store.prune(5);
    EXPECT_FALSE(config_store.hasValidSerial(5));
    EXPECT_TRUE(config_store.hasValidSerial(6));
    config_store.prune(10);
    EXPECT_FALSE(config_store.hasValidSerial(6));
}

TEST("require that MemoryConfigStores preserves state of "
     "MemoryConfigStore between instantiations") {
    MemoryConfigStores config_stores;
    const std::string name("foo");
    ConfigStore::UP config_store = config_stores.getConfigStore(name);
    config_store->saveConfig(*getConfig(10), 5);
    EXPECT_TRUE(config_store->hasValidSerial(5));
    config_store.reset();
    config_store = config_stores.getConfigStore(name);
    EXPECT_TRUE(config_store->hasValidSerial(5));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
