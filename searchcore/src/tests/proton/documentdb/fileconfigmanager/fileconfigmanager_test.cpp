// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-mycfg.h"
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/searchcore/proton/common/schemautil.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace cloud::config::filedistribution;
using namespace config;
using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search::core;
using namespace vespa::config::search;
using proton::matching::RankingConstants;

typedef DocumentDBConfigHelper DBCM;
typedef DocumentDBConfig::DocumenttypesConfigSP DocumenttypesConfigSP;
using vespalib::nbostream;

vespalib::string myId("myconfigid");

namespace
{

DocumentDBConfig::SP
getConfig(int64_t generation, const Schema::SP &schema)
{
    return test::DocumentDBConfigBuilder(generation, schema, "client", "test").build();
}

Schema::SP
getSchema(int step)
{
    Schema::SP schema(new Schema);
    schema->addIndexField(Schema::IndexField("foo1", schema::DataType::STRING));
    if (step < 2) {
        schema->addIndexField(Schema::IndexField("foo2", schema::DataType::STRING));
    }
    if (step < 1) {
        schema->addIndexField(Schema::IndexField("foo3", schema::DataType::STRING));
    }
    return schema;
}

        }

DocumentDBConfig::SP
makeBaseConfigSnapshot()
{
    config::DirSpec spec(TEST_PATH("cfg"));
    ConfigKeySet extraKeySet;
    extraKeySet.add<MycfgConfig>("");
    DBCM dbcm(spec, "test", extraKeySet);
    DocumenttypesConfigSP dtcfg(config::ConfigGetter<DocumenttypesConfig>::getConfig("", spec).release());
    BootstrapConfig::SP b(new BootstrapConfig(1,
                                              dtcfg,
                                              DocumentTypeRepo::SP(new DocumentTypeRepo(*dtcfg)),
                                              std::make_shared<ProtonConfig>(),
                                              std::make_shared<FiledistributorrpcConfig>(),
                                              std::make_shared<TuneFileDocumentDB>()));
    dbcm.forwardConfig(b);
    dbcm.nextGeneration(0);
    DocumentDBConfig::SP snap = dbcm.getConfig();
    snap->setConfigId(myId);
    ASSERT_TRUE(snap.get() != NULL);
    return snap;
}

Schema
makeHistorySchema()
{
    Schema hs;
    hs.addIndexField(Schema::IndexField("history", schema::DataType::STRING));
    return hs;
}

void
saveBaseConfigSnapshot(const DocumentDBConfig &snap, const Schema &history, SerialNum num)
{
    FileConfigManager cm("out", myId, snap.getDocTypeName());
    cm.saveConfig(snap, history, num);
}


DocumentDBConfig::SP
makeEmptyConfigSnapshot(void)
{
    return test::DocumentDBConfigBuilder(0, std::make_shared<Schema>(), "client", "test").build();
}

void incInt(int *i, const DocumentType&) { ++*i; }

void
assertEqualExtraConfigs(const DocumentDBConfig &expSnap, const DocumentDBConfig &actSnap)
{
    const ConfigSnapshot &exp = expSnap.getExtraConfigs();
    const ConfigSnapshot &act = actSnap.getExtraConfigs();
    EXPECT_EQUAL(1u, exp.size());
    EXPECT_EQUAL(1u, act.size());
    std::unique_ptr<MycfgConfig> expCfg = exp.getConfig<MycfgConfig>("");
    std::unique_ptr<MycfgConfig> actCfg = act.getConfig<MycfgConfig>("");
    EXPECT_EQUAL("foo", expCfg->myField);
    EXPECT_EQUAL("foo", actCfg->myField);
}

void
assertEqualSnapshot(const DocumentDBConfig &exp, const DocumentDBConfig &act)
{
    EXPECT_TRUE(exp.getRankProfilesConfig() == act.getRankProfilesConfig());
    EXPECT_TRUE(exp.getRankingConstants() == act.getRankingConstants());
    EXPECT_EQUAL(0u, exp.getRankingConstants().size());
    EXPECT_TRUE(exp.getIndexschemaConfig() == act.getIndexschemaConfig());
    EXPECT_TRUE(exp.getAttributesConfig() == act.getAttributesConfig());
    EXPECT_TRUE(exp.getSummaryConfig() == act.getSummaryConfig());
    EXPECT_TRUE(exp.getSummarymapConfig() == act.getSummarymapConfig());
    EXPECT_TRUE(exp.getJuniperrcConfig() == act.getJuniperrcConfig());
    EXPECT_TRUE(exp.getImportedFieldsConfig() == act.getImportedFieldsConfig());
    EXPECT_EQUAL(0u, exp.getImportedFieldsConfig().attribute.size());

    int expTypeCount = 0;
    int actTypeCount = 0;
    exp.getDocumentTypeRepoSP()->forEachDocumentType(
            *vespalib::makeClosure(incInt, &expTypeCount));
    act.getDocumentTypeRepoSP()->forEachDocumentType(
            *vespalib::makeClosure(incInt, &actTypeCount));
    EXPECT_EQUAL(expTypeCount, actTypeCount);
    EXPECT_TRUE(*exp.getSchemaSP() == *act.getSchemaSP());
    EXPECT_EQUAL(expTypeCount, actTypeCount);
    EXPECT_EQUAL(exp.getConfigId(), act.getConfigId());
    assertEqualExtraConfigs(exp, act);
}

DocumentDBConfig::SP
addConfigsThatAreNotSavedToDisk(const DocumentDBConfig &cfg)
{
    test::DocumentDBConfigBuilder builder(cfg);
    RankingConstants::Vector constants = {{"my_name", "my_type", "my_path"}};
    builder.rankingConstants(std::make_shared<RankingConstants>(constants));

    ImportedFieldsConfigBuilder importedFields;
    importedFields.attribute.resize(1);
    importedFields.attribute.back().name = "my_name";
    builder.importedFields(std::make_shared<ImportedFieldsConfig>(importedFields));
    return builder.build();
}

TEST_FF("requireThatConfigCanBeSavedAndLoaded", DocumentDBConfig::SP(makeBaseConfigSnapshot()),
                                                Schema(makeHistorySchema()))
{

    DocumentDBConfig::SP fullCfg = addConfigsThatAreNotSavedToDisk(*f1);
    saveBaseConfigSnapshot(*fullCfg, f2, 20);
    DocumentDBConfig::SP esnap(makeEmptyConfigSnapshot());
    Schema::SP ehs;
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.loadConfig(*esnap, 20, esnap, ehs);
    }
    assertEqualSnapshot(*f1, *esnap);
    EXPECT_TRUE(f2 == *ehs);
}

TEST_FF("requireThatConfigCanBeSerializedAndDeserialized", DocumentDBConfig::SP(makeBaseConfigSnapshot()),
                                                           Schema(makeHistorySchema()))
{
    saveBaseConfigSnapshot(*f1, f2, 30);
    nbostream stream;
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.serializeConfig(30, stream);
    }
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.deserializeConfig(40, stream);
    }
    DocumentDBConfig::SP fsnap(makeEmptyConfigSnapshot());
    Schema::SP fhs;
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.loadConfig(*fsnap, 40, fsnap, fhs);
    }
    assertEqualSnapshot(*f1, *fsnap);
    EXPECT_TRUE(f2 == *fhs);
    EXPECT_EQUAL("dummy", fsnap->getDocTypeName());
}

TEST_FF("requireThatWipeHistoryCanBeSaved", DocumentDBConfig::SP(makeBaseConfigSnapshot()),
                                            Schema(makeHistorySchema()))
{
    saveBaseConfigSnapshot(*f1, f2, 50);
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.saveWipeHistoryConfig(60, 0);
    }
    DocumentDBConfig::SP gsnap(makeEmptyConfigSnapshot());
    Schema::SP ghs;
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.loadConfig(*gsnap, 60, gsnap, ghs);
    }
    assertEqualSnapshot(*f1, *gsnap);
    EXPECT_TRUE(f2 != *ghs);
    EXPECT_TRUE(!f2.empty());
    EXPECT_TRUE(ghs->empty());
}


TEST("require that wipe history clears only portions of history")
{
    FileConfigManager cm("out2", myId, "dummy");
    Schema::SP schema(getSchema(0));
    Schema::SP history(new Schema);
    DocumentDBConfig::SP config(getConfig(5, schema));
    cm.saveConfig(*config, *history, 5);
    Schema::SP oldSchema(schema);
    schema = getSchema(1);
    config = getConfig(6, schema);
    history = SchemaUtil::makeHistorySchema(*schema, *oldSchema, *history,
                                            100);
    cm.saveConfig(*config, *history, 10);
    oldSchema = schema;
    schema = getSchema(2);
    config = getConfig(7, schema);
    history = SchemaUtil::makeHistorySchema(*schema, *oldSchema, *history,
                                            200);
    cm.saveConfig(*config, *history, 15);
    cm.saveWipeHistoryConfig(20, 50);
    cm.saveWipeHistoryConfig(25, 100);
    cm.saveWipeHistoryConfig(30, 150);
    cm.saveWipeHistoryConfig(35, 200);
    cm.saveWipeHistoryConfig(40, 250);
    DocumentDBConfig::SP oldconfig(config);
    cm.loadConfig(*oldconfig, 20, config, history);
    EXPECT_EQUAL(2u, history->getNumIndexFields());
    oldconfig = config;
    cm.loadConfig(*oldconfig, 25, config, history);
    EXPECT_EQUAL(2u, history->getNumIndexFields());
    oldconfig = config;
    cm.loadConfig(*oldconfig, 30, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    oldconfig = config;
    cm.loadConfig(*oldconfig, 35, config, history);
    EXPECT_EQUAL(1u, history->getNumIndexFields());
    oldconfig = config;
    cm.loadConfig(*oldconfig, 40, config, history);
    EXPECT_EQUAL(0u, history->getNumIndexFields());
}

TEST_FF("requireThatConfigCanBeLoadedWithoutExtraConfigsDataFile", DocumentDBConfig::SP(makeBaseConfigSnapshot()),
                                                                   Schema(makeHistorySchema()))
{
    saveBaseConfigSnapshot(*f1, f2, 70);
    EXPECT_TRUE(vespalib::unlink("out/config-70/extraconfigs.dat"));
    DocumentDBConfig::SP esnap(makeEmptyConfigSnapshot());
    Schema::SP ehs;
    {
        FileConfigManager cm("out", myId, "dummy");
        cm.loadConfig(*esnap, 70, esnap, ehs);
    }
    EXPECT_EQUAL(0u, esnap->getExtraConfigs().size());
}


TEST_FF("requireThatVisibilityDelayIsPropagated",
        DocumentDBConfig::SP(makeBaseConfigSnapshot()),
        Schema(makeHistorySchema()))
{
    saveBaseConfigSnapshot(*f1, f2, 80);
    DocumentDBConfig::SP esnap(makeEmptyConfigSnapshot());
    Schema::SP ehs;
    {
        ProtonConfigBuilder protonConfigBuilder;
        ProtonConfigBuilder::Documentdb ddb;
        ddb.inputdoctypename = "dummy";
        ddb.visibilitydelay = 61.0;
        protonConfigBuilder.documentdb.push_back(ddb);
        protonConfigBuilder.maxvisibilitydelay = 100.0;
        FileConfigManager cm("out", myId, "dummy");
        using ProtonConfigSP = BootstrapConfig::ProtonConfigSP;
        cm.setProtonConfig(
                ProtonConfigSP(new ProtonConfig(protonConfigBuilder)));
        cm.loadConfig(*esnap, 70, esnap, ehs);
    }
    EXPECT_EQUAL(0u, esnap->getExtraConfigs().size());
    EXPECT_EQUAL(61.0, esnap->getMaintenanceConfigSP()->getVisibilityDelay().sec());
}



TEST_MAIN() { TEST_RUN_ALL(); }

