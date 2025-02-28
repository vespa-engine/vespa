// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for memoryconfigstore.

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::SerialNum;
using search::index::Schema;
using search::index::schema::DataType;

using namespace proton;

namespace {

DocumentDBConfig::SP
getConfig(int64_t generation, std::shared_ptr<const Schema> schema)
{
    return test::DocumentDBConfigBuilder(generation, std::move(schema), "client", "test").build();
}


DocumentDBConfig::SP
getConfig(int64_t generation)
{
    return getConfig(generation, {});
}


TEST(MemoryConfigStoreTest, require_that_configs_can_be_stored_and_loaded) {
    MemoryConfigStore config_store;
    SerialNum serial(12);
    config_store.saveConfig(*getConfig(10), serial);
    DocumentDBConfig::SP config;
    config_store.loadConfig(*getConfig(14), serial, config);
    ASSERT_TRUE(config.get());
    EXPECT_EQ(10, config->getGeneration());
}

TEST(MemoryConfigStoreTest, require_that_best_serial_number_is_the_most_recent_one) {
    MemoryConfigStore config_store;
    EXPECT_EQ(0u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQ(5u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQ(5u, config_store.getBestSerialNum());
}

TEST(MemoryConfigStoreTest, require_that_oldest_serial_number_is_the_first_one_or_0) {
    MemoryConfigStore config_store;
    EXPECT_EQ(0u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQ(5u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQ(2u, config_store.getOldestSerialNum());
}

TEST(MemoryConfigStoreTest, require_that_existing_serial_numbers_are_valid) {
    MemoryConfigStore config_store;
    EXPECT_FALSE(config_store.hasValidSerial(5));
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_TRUE(config_store.hasValidSerial(5));
}

TEST(MemoryConfigStoreTest, require_that_prev_valid_serial_number_is_the_last_one_before_the_arg) {
    MemoryConfigStore config_store;
    EXPECT_EQ(0u, config_store.getPrevValidSerial(10));
    config_store.saveConfig(*getConfig(10), 5);
    EXPECT_EQ(5u, config_store.getPrevValidSerial(10));
    EXPECT_EQ(0u, config_store.getPrevValidSerial(5));
    EXPECT_EQ(0u, config_store.getPrevValidSerial(4));
    config_store.saveConfig(*getConfig(10), 2);
    EXPECT_EQ(0u, config_store.getPrevValidSerial(1));
    EXPECT_EQ(0u, config_store.getPrevValidSerial(2));
    EXPECT_EQ(2u, config_store.getPrevValidSerial(4));
    EXPECT_EQ(2u, config_store.getPrevValidSerial(5));
    EXPECT_EQ(5u, config_store.getPrevValidSerial(10));
}

TEST(MemoryConfigStoreTest, require_that_prune_removes_old_configs) {
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

TEST(MemoryConfigStoreTest, require_that_MemoryConfigStores_preserves_state_of_MemoryConfigStore_between_instantiations) {
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
