// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <map>
#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/bootstrapconfigmanager.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/protonconfigurer.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/linkedptr.h>
#include <vespa/vespalib/util/varholder.h>

using namespace config;
using namespace proton;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace cloud::config::filedistribution;

using config::ConfigUri;
using document::DocumentTypeRepo;
using document::DocumenttypesConfig;
using document::DocumenttypesConfigBuilder;
using search::TuneFileDocumentDB;
using std::map;
using vespalib::LinkedPtr;
using vespalib::VarHolder;

struct DoctypeFixture {
    typedef vespalib::LinkedPtr<DoctypeFixture> LP;
    AttributesConfigBuilder attributesBuilder;
    RankProfilesConfigBuilder rankProfilesBuilder;
    RankingConstantsConfigBuilder rankingConstantsBuilder;
    IndexschemaConfigBuilder indexschemaBuilder;
    SummaryConfigBuilder summaryBuilder;
    SummarymapConfigBuilder summarymapBuilder;
    JuniperrcConfigBuilder juniperrcBuilder;
    ImportedFieldsConfigBuilder importedFieldsBuilder;
};

struct ConfigTestFixture {
    const std::string configId;
    ProtonConfigBuilder protonBuilder;
    DocumenttypesConfigBuilder documenttypesBuilder;
    FiledistributorrpcConfigBuilder filedistBuilder;
    map<std::string, DoctypeFixture::LP> dbConfig;
    ConfigSet set;
    IConfigContext::SP context;
    int idcounter;

    ConfigTestFixture(const std::string & id)
        : configId(id),
          protonBuilder(),
          documenttypesBuilder(),
          filedistBuilder(),
          dbConfig(),
          set(),
          context(new ConfigContext(set)),
          idcounter(-1)
    {
        set.addBuilder(configId, &protonBuilder);
        set.addBuilder(configId, &documenttypesBuilder);
        set.addBuilder(configId, &filedistBuilder);
        addDocType("_alwaysthere_");
    }

    DoctypeFixture::LP addDocType(const std::string & name)
    {
        DocumenttypesConfigBuilder::Documenttype dt;
        dt.bodystruct = -1270491200;
        dt.headerstruct = 306916075;
        dt.id = idcounter--;
        dt.name = name;
        dt.version = 0;
        documenttypesBuilder.documenttype.push_back(dt);

        ProtonConfigBuilder::Documentdb db;
        db.inputdoctypename = name;
        db.configid = configId + "/" + name;
        protonBuilder.documentdb.push_back(db);

        DoctypeFixture::LP fixture(new DoctypeFixture());
        set.addBuilder(db.configid, &fixture->attributesBuilder);
        set.addBuilder(db.configid, &fixture->rankProfilesBuilder);
        set.addBuilder(db.configid, &fixture->rankingConstantsBuilder);
        set.addBuilder(db.configid, &fixture->indexschemaBuilder);
        set.addBuilder(db.configid, &fixture->summaryBuilder);
        set.addBuilder(db.configid, &fixture->summarymapBuilder);
        set.addBuilder(db.configid, &fixture->juniperrcBuilder);
        set.addBuilder(db.configid, &fixture->importedFieldsBuilder);
        dbConfig[name] = fixture;
        return fixture;
    }

    void removeDocType(const std::string & name)
    {
        for (DocumenttypesConfigBuilder::DocumenttypeVector::iterator it(documenttypesBuilder.documenttype.begin()),
                                                                      mt(documenttypesBuilder.documenttype.end());
             it != mt;
             it++) {
            if ((*it).name.compare(name) == 0) {
                documenttypesBuilder.documenttype.erase(it);
                break;
            }
        }

        for (ProtonConfigBuilder::DocumentdbVector::iterator it(protonBuilder.documentdb.begin()),
                                                             mt(protonBuilder.documentdb.end());
             it != mt;
             it++) {
            if ((*it).inputdoctypename.compare(name) == 0) {
                protonBuilder.documentdb.erase(it);
                break;
            }
        }
    }

    bool configEqual(const std::string & name, DocumentDBConfig::SP dbc) {
        DoctypeFixture::LP fixture(dbConfig[name]);
        return (fixture->attributesBuilder == dbc->getAttributesConfig() &&
                fixture->rankProfilesBuilder == dbc->getRankProfilesConfig() &&
                fixture->indexschemaBuilder == dbc->getIndexschemaConfig() &&
                fixture->summaryBuilder == dbc->getSummaryConfig() &&
                fixture->summarymapBuilder == dbc->getSummarymapConfig() &&
                fixture->juniperrcBuilder == dbc->getJuniperrcConfig());
    }

    bool configEqual(BootstrapConfig::SP bootstrapConfig) {
        return (protonBuilder == bootstrapConfig->getProtonConfig() &&
                documenttypesBuilder == bootstrapConfig->getDocumenttypesConfig());
    }

    BootstrapConfig::SP getBootstrapConfig(int64_t generation) const {
        return BootstrapConfig::SP(new BootstrapConfig(generation,
                                                       BootstrapConfig::DocumenttypesConfigSP(new DocumenttypesConfig(documenttypesBuilder)),
                                                       DocumentTypeRepo::SP(new DocumentTypeRepo(documenttypesBuilder)),
                                                       BootstrapConfig::ProtonConfigSP(new ProtonConfig(protonBuilder)),
                                                       std::make_shared<FiledistributorrpcConfig>(),
                                                       std::make_shared<TuneFileDocumentDB>()));
    }

    void reload() { context->reload(); }
};

template <typename ConfigType, typename ConfigOwner>
struct OwnerFixture : public ConfigOwner
{
    volatile bool configured;
    VarHolder<ConfigType> config;

    OwnerFixture() : configured(false), config() { }
    bool waitUntilConfigured(int timeout) {
        FastOS_Time timer;
        timer.SetNow();
        while (timer.MilliSecsToNow() < timeout) {
            if (configured)
                break;
            FastOS_Thread::Sleep(100);
        }
        return configured;
    }
    void reconfigure(const ConfigType & cfg) {
        assert(cfg->valid());
        config.set(cfg);
        configured = true;
    }
};

typedef OwnerFixture<BootstrapConfig::SP, IBootstrapOwner> BootstrapOwner;
typedef OwnerFixture<DocumentDBConfig::SP, IDocumentDBConfigOwner> DBOwner;

TEST_F("require that bootstrap config manager creats correct key set", BootstrapConfigManager("foo")) {
    const ConfigKeySet set(f1.createConfigKeySet());
    ASSERT_EQUAL(3u, set.size());
    ConfigKey protonKey(ConfigKey::create<ProtonConfig>("foo"));
    ConfigKey dtKey(ConfigKey::create<DocumenttypesConfig>("foo"));
    ASSERT_TRUE(set.find(protonKey) != set.end());
    ASSERT_TRUE(set.find(dtKey) != set.end());
}

TEST_FFF("require_that_bootstrap_config_manager_updates_config", ConfigTestFixture("search"),
                                                                 BootstrapConfigManager(f1.configId),
                                                                 ConfigRetriever(f2.createConfigKeySet(), f1.context)) {
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(f2.getConfig()));
    f1.protonBuilder.rpcport = 9010;
    ASSERT_FALSE(f1.configEqual(f2.getConfig()));
    f1.reload();
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(f2.getConfig()));

    f1.addDocType("foobar");
    ASSERT_FALSE(f1.configEqual(f2.getConfig()));
    f1.reload();
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(f2.getConfig()));
}

TEST_FF("require_that_documentdb_config_manager_subscribes_for_config",
        ConfigTestFixture("search"),
        DocumentDBConfigManager(f1.configId + "/typea", "typea")) {
    f1.addDocType("typea");
    const ConfigKeySet keySet(f2.createConfigKeySet());
    ASSERT_EQUAL(8u, keySet.size());
    ConfigRetriever retriever(keySet, f1.context);
    f2.forwardConfig(f1.getBootstrapConfig(1));
    f2.update(retriever.getBootstrapConfigs()); // Cheating, but we only need the configs
    ASSERT_TRUE(f1.configEqual("typea", f2.getConfig()));
}

TEST_FF("require that documentdb config manager builds schema with imported attribute fields",
        ConfigTestFixture("search"),
        DocumentDBConfigManager(f1.configId + "/typea", "typea"))
{
    auto docType = f1.addDocType("typea");
    docType->importedFieldsBuilder.attribute.resize(1);
    docType->importedFieldsBuilder.attribute[0].name = "imported";

    ConfigRetriever retriever(f2.createConfigKeySet(), f1.context);
    f2.forwardConfig(f1.getBootstrapConfig(1));
    f2.update(retriever.getBootstrapConfigs()); // Cheating, but we only need the configs
    const auto &schema = f2.getConfig()->getSchemaSP();
    EXPECT_EQUAL(1u, schema->getNumImportedAttributeFields());
    EXPECT_EQUAL("imported", schema->getImportedAttributeFields()[0].getName());
}

TEST_FFF("require_that_protonconfigurer_follows_changes_to_bootstrap",
         ConfigTestFixture("search"),
         BootstrapOwner(),
         ProtonConfigurer(ConfigUri(f1.configId, f1.context), &f2, 60000)) {
    f3.start();
    ASSERT_TRUE(f2.configured);
    ASSERT_TRUE(f1.configEqual(f2.config.get()));
    f2.configured = false;
    f1.protonBuilder.rpcport = 9010;
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(120000));
    ASSERT_TRUE(f1.configEqual(f2.config.get()));
    f3.close();
}

TEST_FFF("require_that_protonconfigurer_follows_changes_to_doctypes",
         ConfigTestFixture("search"),
         BootstrapOwner(),
         ProtonConfigurer(ConfigUri(f1.configId, f1.context), &f2, 60000)) {
    f3.start();

    f2.configured = false;
    f1.addDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60000));
    ASSERT_TRUE(f1.configEqual(f2.config.get()));

    f2.configured = false;
    f1.removeDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60000));
    ASSERT_TRUE(f1.configEqual(f2.config.get()));
    f3.close();
}

TEST_FFF("require_that_protonconfigurer_reconfigures_dbowners",
         ConfigTestFixture("search"),
         BootstrapOwner(),
         ProtonConfigurer(ConfigUri(f1.configId, f1.context), &f2, 60000)) {
    f3.start();

    DBOwner dbA;
    f3.registerDocumentDB(DocTypeName("typea"), &dbA);

    // Add db and verify that we get an initial callback
    f2.configured = false;
    f1.addDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60000));
    ASSERT_TRUE(f1.configEqual(f2.config.get()));
    ASSERT_TRUE(dbA.waitUntilConfigured(60000));
    ASSERT_TRUE(f1.configEqual("typea", dbA.config.get()));

    // Remove and verify that we don't get any callback
    dbA.configured = false;
    f1.removeDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60000));
    ASSERT_FALSE(dbA.waitUntilConfigured(1000));
    f3.close();
}

TEST_MAIN() { TEST_RUN_ALL(); }
