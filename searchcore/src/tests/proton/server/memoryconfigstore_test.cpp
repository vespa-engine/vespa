// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for memoryconfigstore.

#include <vespa/log/log.h>
LOG_SETUP("memoryconfigstore_test");
#include <vespa/fastos/fastos.h>

#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/common/schemautil.h>

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


Schema::SP
getSchema(int step)
{
    Schema::SP schema(new Schema);
    schema->addIndexField(Schema::IndexField("foo1", DataType::STRING));
    if (step < 2) {
        schema->addIndexField(Schema::IndexField("foo2", DataType::STRING));
    }
    if (step < 1) {
        schema->addIndexField(Schema::IndexField("foo3", DataType::STRING));
    }
    return schema;
}

TEST("require that configs can be stored and loaded") {
    MemoryConfigStore config_store;
    SerialNum serial(12);
    config_store.saveConfig(*getConfig(10), Schema(), serial);
    DocumentDBConfig::SP config;
    Schema::SP history;
    config_store.loadConfig(*getConfig(14), serial, config, history);
    ASSERT_TRUE(config.get());
    ASSERT_TRUE(history.get());
    EXPECT_EQUAL(10, config->getGeneration());
}

TEST("require that best serial number is the most recent one") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), Schema(), 5);
    EXPECT_EQUAL(5u, config_store.getBestSerialNum());
    config_store.saveConfig(*getConfig(10), Schema(), 2);
    EXPECT_EQUAL(5u, config_store.getBestSerialNum());
}

TEST("require that oldest serial number is the first one or 0") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), Schema(), 5);
    EXPECT_EQUAL(5u, config_store.getOldestSerialNum());
    config_store.saveConfig(*getConfig(10), Schema(), 2);
    EXPECT_EQUAL(2u, config_store.getOldestSerialNum());
}

TEST("require that existing serial numbers are valid") {
    MemoryConfigStore config_store;
    EXPECT_FALSE(config_store.hasValidSerial(5));
    config_store.saveConfig(*getConfig(10), Schema(), 5);
    EXPECT_TRUE(config_store.hasValidSerial(5));
}

TEST("require that prev valid serial number is the last one before the arg") {
    MemoryConfigStore config_store;
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(10));
    config_store.saveConfig(*getConfig(10), Schema(), 5);
    EXPECT_EQUAL(5u, config_store.getPrevValidSerial(10));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(5));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(4));
    config_store.saveConfig(*getConfig(10), Schema(), 2);
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(1));
    EXPECT_EQUAL(0u, config_store.getPrevValidSerial(2));
    EXPECT_EQUAL(2u, config_store.getPrevValidSerial(4));
    EXPECT_EQUAL(2u, config_store.getPrevValidSerial(5));
    EXPECT_EQUAL(5u, config_store.getPrevValidSerial(10));
}

TEST("require that prune removes old configs") {
    MemoryConfigStore config_store;
    config_store.saveConfig(*getConfig(10), Schema(), 5);
    config_store.saveConfig(*getConfig(10), Schema(), 6);
    EXPECT_TRUE(config_store.hasValidSerial(5));
    config_store.prune(5);
    EXPECT_FALSE(config_store.hasValidSerial(5));
    EXPECT_TRUE(config_store.hasValidSerial(6));
    config_store.prune(10);
    EXPECT_FALSE(config_store.hasValidSerial(6));
}

TEST("require that wipe history clears previous history schema "
     "and adds new, identical entry for current serial num") {
    MemoryConfigStore config_store;
    Schema::SP history(new Schema);
    history->addIndexField(Schema::IndexField("foo", DataType::STRING));
    config_store.saveConfig(*getConfig(10), *history, 5);
    DocumentDBConfig::SP config;
    config_store.loadConfig(*getConfig(14), 5, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    config_store.saveWipeHistoryConfig(6, 0);
    EXPECT_TRUE(config_store.hasValidSerial(6));
    config_store.loadConfig(*getConfig(14), 5, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    config_store.loadConfig(*getConfig(14), 6, config, history);
    ASSERT_TRUE(config.get());
    ASSERT_TRUE(history.get());
    EXPECT_EQUAL(0u, history->getNumIndexFields());
}


TEST("require that wipe history clears only portions of history")
{
    MemoryConfigStore config_store;
    Schema::SP schema(getSchema(0));
    Schema::SP history(new Schema);
    DocumentDBConfig::SP config(getConfig(5, schema));
    config_store.saveConfig(*config, *history, 5);
    Schema::SP oldSchema(schema);
    schema = getSchema(1);
    history = SchemaUtil::makeHistorySchema(*schema, *oldSchema, *history,
                                            100);
    config_store.saveConfig(*config, *history, 10);
    oldSchema = schema;
    schema = getSchema(2);
    history = SchemaUtil::makeHistorySchema(*schema, *oldSchema, *history,
                                            200);
    config_store.saveConfig(*config, *history, 15);
    config_store.saveWipeHistoryConfig(20, 50);
    config_store.saveWipeHistoryConfig(25, 100);
    config_store.saveWipeHistoryConfig(30, 150);
    config_store.saveWipeHistoryConfig(35, 200);
    config_store.saveWipeHistoryConfig(40, 250);
    DocumentDBConfig::SP oldconfig(config);
    config_store.loadConfig(*oldconfig, 20, config, history);
    EXPECT_EQUAL(2u, history->getNumIndexFields());
    oldconfig = config;
    config_store.loadConfig(*oldconfig, 25, config, history);
    EXPECT_EQUAL(2u, history->getNumIndexFields());
    oldconfig = config;
    config_store.loadConfig(*oldconfig, 30, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    oldconfig = config;
    config_store.loadConfig(*oldconfig, 35, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    oldconfig = config;
    config_store.loadConfig(*oldconfig, 40, config, history);
    EXPECT_EQUAL(0u, history->getNumIndexFields());
}

TEST("require that wipe history does nothing if serial num exists") {
    MemoryConfigStore config_store;
    Schema::SP history(new Schema);
    history->addIndexField(Schema::IndexField("foo", DataType::STRING));
    config_store.saveConfig(*getConfig(10), *history, 5);
    DocumentDBConfig::SP config;
    config_store.saveWipeHistoryConfig(5, 0);
    config_store.loadConfig(*getConfig(14), 5, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
}

TEST("require that MemoryConfigStores preserves state of "
     "MemoryConfigStore between instantiations") {
    MemoryConfigStores config_stores;
    const std::string name("foo");
    ConfigStore::UP config_store = config_stores.getConfigStore(name);
    config_store->saveConfig(*getConfig(10), Schema(), 5);
    EXPECT_TRUE(config_store->hasValidSerial(5));
    config_store.reset();
    config_store = config_stores.getConfigStore(name);
    EXPECT_TRUE(config_store->hasValidSerial(5));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
