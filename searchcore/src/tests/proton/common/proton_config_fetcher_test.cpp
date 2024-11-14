// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-onnx-models.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-ranking-constants.h>
#include <vespa/config-ranking-expressions.h>
#include <vespa/config-summary.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/bootstrapconfigmanager.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/proton_config_fetcher.h>
#include <vespa/searchcore/proton/server/proton_config_snapshot.h>
#include <vespa/searchcore/proton/server/i_proton_configurer.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fileacquirer/config-filedistributorrpc.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/config.h>
#include <map>
#include <thread>

using namespace config;
using namespace proton;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace cloud::config::filedistribution;
using namespace std::chrono_literals;
using vespa::config::content::core::BucketspacesConfig;
using vespa::config::content::core::BucketspacesConfigBuilder;

using config::ConfigUri;
using document::DocumentTypeRepo;
using search::TuneFileDocumentDB;
using std::map;
using vespalib::VarHolder;
using search::GrowStrategy;
using vespalib::datastore::CompactionStrategy;
using vespalib::HwInfo;

namespace {
constexpr int proton_rpc_port = 9010; // Not used for listening in this test

struct DoctypeFixture {
    using UP = std::unique_ptr<DoctypeFixture>;
    AttributesConfigBuilder attributesBuilder;
    RankProfilesConfigBuilder rankProfilesBuilder;
    RankingConstantsConfigBuilder rankingConstantsBuilder;
    RankingExpressionsConfigBuilder rankingExpressionsBuilder;
    OnnxModelsConfigBuilder onnxModelsBuilder;
    IndexschemaConfigBuilder indexschemaBuilder;
    SummaryConfigBuilder summaryBuilder;
    JuniperrcConfigBuilder juniperrcBuilder;
    ImportedFieldsConfigBuilder importedFieldsBuilder;
};

struct ConfigTestFixture {
    const std::string   configId;
    Transport           transport;
    ProtonConfigBuilder protonBuilder;
    DocumenttypesConfigBuilder documenttypesBuilder;
    FiledistributorrpcConfigBuilder filedistBuilder;
    BucketspacesConfigBuilder bucketspacesBuilder;
    map<std::string, DoctypeFixture::UP> dbConfig;
    ConfigSet set;
    std::shared_ptr<IConfigContext> context;
    int idcounter;

    explicit ConfigTestFixture(const std::string & id)
        : configId(id),
          protonBuilder(),
          documenttypesBuilder(),
          filedistBuilder(),
          bucketspacesBuilder(),
          dbConfig(),
          set(),
          context(std::make_shared<ConfigContext>(set)),
          idcounter(-1)
    {
        set.addBuilder(configId, &protonBuilder);
        set.addBuilder(configId, &documenttypesBuilder);
        set.addBuilder(configId, &filedistBuilder);
        set.addBuilder(configId, &bucketspacesBuilder);
        addDocType("_alwaysthere_");
    }

    ~ConfigTestFixture();

    DoctypeFixture *addDocType(const std::string &name) {
        return addDocType(name, ProtonConfig::Documentdb::Mode::INDEX);
    }

    DoctypeFixture *addDocType(const std::string &name, ProtonConfig::Documentdb::Mode mode) {
        return addDocType(name, mode, false);
    }

    DoctypeFixture *addDocType(const std::string &name, ProtonConfig::Documentdb::Mode mode, bool isGlobal) {
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
        db.global = isGlobal;
        db.mode = mode;
        protonBuilder.documentdb.push_back(db);

        DoctypeFixture::UP fixture = std::make_unique<DoctypeFixture>();
        set.addBuilder(db.configid, &fixture->attributesBuilder);
        set.addBuilder(db.configid, &fixture->rankProfilesBuilder);
        set.addBuilder(db.configid, &fixture->rankingConstantsBuilder);
        set.addBuilder(db.configid, &fixture->rankingExpressionsBuilder);
        set.addBuilder(db.configid, &fixture->onnxModelsBuilder);
        set.addBuilder(db.configid, &fixture->indexschemaBuilder);
        set.addBuilder(db.configid, &fixture->summaryBuilder);
        set.addBuilder(db.configid, &fixture->juniperrcBuilder);
        set.addBuilder(db.configid, &fixture->importedFieldsBuilder);
        return dbConfig.emplace(name, std::move(fixture)).first->second.get();
    }

    void removeDocType(const std::string & name)
    {
        for (auto it(documenttypesBuilder.documenttype.begin()), mt(documenttypesBuilder.documenttype.end());
             it != mt;
             it++) {
            if ((*it).name.compare(name) == 0) {
                documenttypesBuilder.documenttype.erase(it);
                break;
            }
        }

        for (auto it(protonBuilder.documentdb.begin()), mt(protonBuilder.documentdb.end());
             it != mt;
             it++) {
            if ((*it).inputdoctypename.compare(name) == 0) {
                protonBuilder.documentdb.erase(it);
                break;
            }
        }
    }

    bool configEqual(const std::string & name, const DocumentDBConfig & dbc) {
        auto itr = dbConfig.find(name);
        if (itr == dbConfig.end()) {
            return false;
        }
        const auto *fixture = itr->second.get();
        return (fixture->attributesBuilder == dbc.getAttributesConfig() &&
                fixture->rankProfilesBuilder == dbc.getRankProfilesConfig() &&
                fixture->indexschemaBuilder == dbc.getIndexschemaConfig() &&
                fixture->summaryBuilder == dbc.getSummaryConfig() &&
                fixture->juniperrcBuilder == dbc.getJuniperrcConfig());
    }

    bool configEqual(const BootstrapConfig & bootstrapConfig) const {
        return (protonBuilder == bootstrapConfig.getProtonConfig() &&
                documenttypesBuilder == bootstrapConfig.getDocumenttypesConfig());
    }

    BootstrapConfig::SP getBootstrapConfig(int64_t generation, const HwInfo & hwInfo) const {
        return std::make_shared<BootstrapConfig>(generation,
                                                 std::make_shared<DocumenttypesConfig>(documenttypesBuilder),
                                                 std::make_shared<DocumentTypeRepo>(documenttypesBuilder),
                                                 std::make_shared<ProtonConfig>(protonBuilder),
                                                 std::make_shared<FiledistributorrpcConfig>(),
                                                 std::make_shared<BucketspacesConfig>(bucketspacesBuilder),
                                                 std::make_shared<TuneFileDocumentDB>(),
                                                 hwInfo);
    }

    void reload() { context->reload(); }
};

ConfigTestFixture::~ConfigTestFixture() = default;

struct ProtonConfigOwner : public proton::IProtonConfigurer
{
    mutable std::mutex _mutex;
    volatile bool _configured;
    VarHolder<std::shared_ptr<ProtonConfigSnapshot>> _config;

    ProtonConfigOwner() : _configured(false), _config() { }
    ~ProtonConfigOwner() override;
    bool waitUntilConfigured(vespalib::duration timeout) const {
        vespalib::Timer timer;
        while (timer.elapsed() < timeout) {
            if (getConfigured())
                return true;
            std::this_thread::sleep_for(100ms);
        }
        return getConfigured();
    }
    void reconfigure(std::shared_ptr<ProtonConfigSnapshot> cfg) override {
        std::lock_guard<std::mutex> guard(_mutex);
        _config.set(cfg);
        _configured = true;
    }
    bool getConfigured() const {
        std::lock_guard<std::mutex> guard(_mutex);
        return _configured;
    }
    BootstrapConfig::SP getBootstrapConfig() const {
        auto snapshot = _config.get();
        return snapshot->getBootstrapConfig();
    }
    DocumentDBConfig::SP getDocumentDBConfig(const std::string &name) const
    {
        auto snapshot = _config.get();
        auto &dbcs = snapshot->getDocumentDBConfigs();
        auto dbitr = dbcs.find(DocTypeName(name));
        if (dbitr != dbcs.end()) {
            return dbitr->second;
        } else {
            return {};
        }
    }
};

ProtonConfigOwner::~ProtonConfigOwner() = default;

}

TEST(ProtonConfigFetcherTest, require_that_bootstrap_config_manager_creats_correct_key_set)
{
    BootstrapConfigManager f1("foo");
    const ConfigKeySet set(f1.createConfigKeySet());
    ASSERT_EQ(4u, set.size());
    ConfigKey protonKey(ConfigKey::create<ProtonConfig>("foo"));
    ConfigKey dtKey(ConfigKey::create<DocumenttypesConfig>("foo"));
    ConfigKey bsKey(ConfigKey::create<BucketspacesConfig>("foo"));
    ASSERT_TRUE(set.find(protonKey) != set.end());
    ASSERT_TRUE(set.find(dtKey) != set.end());
    ASSERT_TRUE(set.find(bsKey) != set.end());
}

TEST(ProtonConfigFetcherTest, require_that_bootstrap_config_manager_updates_config)
{
    ConfigTestFixture f1("search");
    BootstrapConfigManager f2(f1.configId);
    ConfigRetriever f3(f2.createConfigKeySet(), f1.context);
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(*f2.getConfig()));
    f1.protonBuilder.rpcport = proton_rpc_port;
    ASSERT_FALSE(f1.configEqual(*f2.getConfig()));
    f1.reload();
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(*f2.getConfig()));

    f1.addDocType("foobar");
    ASSERT_FALSE(f1.configEqual(*f2.getConfig()));
    f1.reload();
    f2.update(f3.getBootstrapConfigs());
    ASSERT_TRUE(f1.configEqual(*f2.getConfig()));
}

namespace {

DocumentDBConfig::SP
getDocumentDBConfig(ConfigTestFixture &f, DocumentDBConfigManager &mgr, const HwInfo & hwInfo)
{
    ConfigRetriever retriever(mgr.createConfigKeySet(), f.context);
    mgr.forwardConfig(f.getBootstrapConfig(1, hwInfo));
    mgr.update(f.transport.transport(), retriever.getBootstrapConfigs()); // Cheating, but we only need the configs
    return mgr.getConfig();
}

DocumentDBConfig::SP
getDocumentDBConfig(ConfigTestFixture &f, DocumentDBConfigManager &mgr)
{
    return getDocumentDBConfig(f, mgr, HwInfo());
}

}

TEST(ProtonConfigFetcherTest, require_that_documentdb_config_manager_subscribes_for_config)
{
    ConfigTestFixture f1("search");
    DocumentDBConfigManager f2(f1.configId + "/typea", "typea");
    f1.addDocType("typea");
    const ConfigKeySet keySet(f2.createConfigKeySet());
    ASSERT_EQ(9u, keySet.size());
    ASSERT_TRUE(f1.configEqual("typea", *getDocumentDBConfig(f1, f2)));
}

TEST(ProtonConfigFetcherTest, require_that_documentdb_config_manager_builds_schema_with_imported_attribute_fields_and_that_they_are_filtered_from_resulting_attribute_config)
{
    ConfigTestFixture f1("search");
    DocumentDBConfigManager f2(f1.configId + "/typea", "typea");
    auto *docType = f1.addDocType("typea");
    docType->attributesBuilder.attribute.resize(2);
    docType->attributesBuilder.attribute[0].name = "imported";
    docType->attributesBuilder.attribute[0].imported = true;
    docType->attributesBuilder.attribute[1].name = "regular";
    docType->summaryBuilder.classes.resize(1);
    docType->summaryBuilder.classes[0].id = 1;
    docType->summaryBuilder.classes[0].name = "a";

    for (size_t i(0); i < 3; i++) {
        const auto &schema = getDocumentDBConfig(f1, f2)->getSchemaSP();
        EXPECT_EQ(1u, schema->getNumImportedAttributeFields());
        EXPECT_EQ("imported", schema->getImportedAttributeFields()[0].getName());
        EXPECT_EQ(1u, schema->getNumAttributeFields());
        EXPECT_EQ("regular", schema->getAttributeFields()[0].getName());

        const auto &attrCfg = getDocumentDBConfig(f1, f2)->getAttributesConfig();
        EXPECT_EQ(1u, attrCfg.attribute.size());
        EXPECT_EQ("regular", attrCfg.attribute[0].name);
        // This is required to trigger a change to schema, but not to the attributes config
        docType->summaryBuilder.classes[0].id = i+2;
    }
}

TEST(ProtonConfigFetcherTest, require_that_proton_config_fetcher_follows_changes_to_bootstrap)
{
    ConfigTestFixture f1("search");
    ProtonConfigOwner f2;
    ProtonConfigFetcher f3(f1.transport.transport(), ConfigUri(f1.configId, f1.context), f2, 60s);
    f3.start();
    ASSERT_TRUE(f2._configured);
    ASSERT_TRUE(f1.configEqual(*f2.getBootstrapConfig()));
    f2._configured = false;
    f1.protonBuilder.rpcport = proton_rpc_port;
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(120s));
    ASSERT_TRUE(f1.configEqual(*f2.getBootstrapConfig()));
    f3.close();
}

TEST(ProtonConfigFetcherTest, require_that_proton_config_fetcher_follows_changes_to_doctypes)
{
    ConfigTestFixture f1("search");
    ProtonConfigOwner f2;
    ProtonConfigFetcher f3(f1.transport.transport(), ConfigUri(f1.configId, f1.context), f2, 60s);
    f3.start();

    f2._configured = false;
    f1.addDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60s));
    ASSERT_TRUE(f1.configEqual(*f2.getBootstrapConfig()));

    f2._configured = false;
    f1.removeDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60s));
    ASSERT_TRUE(f1.configEqual(*f2.getBootstrapConfig()));
    f3.close();
}

TEST(ProtonConfigFetcherTest, require_that_proton_config_fetcher_reconfigures_dbowners)
{
    ConfigTestFixture f1("search");
    ProtonConfigOwner f2;
    ProtonConfigFetcher f3(f1.transport.transport(), ConfigUri(f1.configId, f1.context), f2, 60s);
    f3.start();
    ASSERT_FALSE(f2.getDocumentDBConfig("typea"));

    // Add db and verify that config for db is provided
    f2._configured = false;
    f1.addDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60s));
    ASSERT_TRUE(f1.configEqual(*f2.getBootstrapConfig()));
    ASSERT_TRUE(static_cast<bool>(f2.getDocumentDBConfig("typea")));
    ASSERT_TRUE(f1.configEqual("typea", *f2.getDocumentDBConfig("typea")));

    // Remove and verify that config for db is no longer provided
    f2._configured = false;
    f1.removeDocType("typea");
    f1.reload();
    ASSERT_TRUE(f2.waitUntilConfigured(60s));
    ASSERT_FALSE(f2.getDocumentDBConfig("typea"));
    f3.close();
}

TEST(ProtonConfigFetcherTest, require_that_lid_space_compaction_is_disabled_for_globally_distributed_document_type)
{
    ConfigTestFixture f1("search");
    DocumentDBConfigManager f2(f1.configId + "/global", "global");
    f1.addDocType("global", ProtonConfig::Documentdb::Mode::INDEX, true);
    auto config = getDocumentDBConfig(f1, f2);
    EXPECT_TRUE(config->getMaintenanceConfigSP()->getLidSpaceCompactionConfig().isDisabled());
}

namespace {

HwInfo
createHwInfoWithMemory(uint64_t mem) {
    return {HwInfo::Disk(1, false, false), HwInfo::Memory(mem), HwInfo::Cpu(1)};
}

}

TEST(ProtonConfigFetcherTest, require_that_target_numdocs_is_fixed_1k_for_indexed_mode)
{
    ConfigTestFixture f1("search");
    DocumentDBConfigManager f2(f1.configId + "/test", "test");
    f1.addDocType("test", ProtonConfig::Documentdb::Mode::INDEX, true);
    for (uint64_t memory : {1_Gi, 10_Gi}) {
        auto config = getDocumentDBConfig(f1, f2, createHwInfoWithMemory(memory));
        AllocStrategy strategy = config->get_alloc_config().make_alloc_strategy(SubDbType::READY);
        EXPECT_EQ(1024u, strategy.get_grow_strategy().getMinimumCapacity());
    }
}

namespace {

constexpr bool target_numdocs_hw_adjustment_is_enabled() noexcept {
    // Must mirror logic of `use_hw_memory_presized_target_num_docs()` in documentdbconfigmanager.cpp
#ifndef VESPA_USE_SANITIZER
    return true;
#else
    return false;
#endif
}

}

TEST(ProtonConfigFetcherTest, require_that_target_numdocs_follows_memory_for_streaming_mode)
{
    ConfigTestFixture f1("search");
    DocumentDBConfigManager f2(f1.configId + "/test", "test");
    f1.addDocType("test", ProtonConfig::Documentdb::Mode::STREAMING, true);
    {
        auto config = getDocumentDBConfig(f1, f2, createHwInfoWithMemory(1_Gi));
        AllocStrategy strategy = config->get_alloc_config().make_alloc_strategy(SubDbType::READY);
        const auto expected = target_numdocs_hw_adjustment_is_enabled() ? 23342213u : 1024u;
        EXPECT_EQ(expected, strategy.get_grow_strategy().getMinimumCapacity());
    }
    {
        auto config = getDocumentDBConfig(f1, f2, createHwInfoWithMemory(10_Gi));
        AllocStrategy strategy = config->get_alloc_config().make_alloc_strategy(SubDbType::READY);
        const auto expected = target_numdocs_hw_adjustment_is_enabled() ? 233422135u : 1024u;
        EXPECT_EQ(expected, strategy.get_grow_strategy().getMinimumCapacity());
    }
}

TEST(ProtonConfigFetcherTest, require_that_prune_removed_documents_interval_can_be_set_based_on_age)
{
    ConfigTestFixture f1("test");
    DocumentDBConfigManager f2(f1.configId + "/test", "test");
    f1.protonBuilder.pruneremoveddocumentsage = 2000;
    f1.protonBuilder.pruneremoveddocumentsinterval = 0;
    f1.addDocType("test");
    auto config = getDocumentDBConfig(f1, f2);
    EXPECT_EQ(20s, config->getMaintenanceConfigSP()->getPruneRemovedDocumentsConfig().getInterval());
}

TEST(ProtonConfigFetcherTest, require_that_docstore_config_computes_cachesize_automatically_if_unset)
{
    ConfigTestFixture f1("test");
    DocumentDBConfigManager f2(f1.configId + "/test", "test");
    HwInfo hwInfo = createHwInfoWithMemory(1000000);
    f1.addDocType("test");
    f1.protonBuilder.summary.cache.maxbytes = 2000;
    auto config = getDocumentDBConfig(f1, f2, hwInfo);
    EXPECT_EQ(2000ul, config->getStoreConfig().getMaxCacheBytes());

    f1.protonBuilder.summary.cache.maxbytes = -7;
    config = getDocumentDBConfig(f1, f2, hwInfo);
    EXPECT_EQ(70000ul, config->getStoreConfig().getMaxCacheBytes());

    f1.protonBuilder.summary.cache.maxbytes = -700;
    config = getDocumentDBConfig(f1, f2, hwInfo);
    EXPECT_EQ(500000ul, config->getStoreConfig().getMaxCacheBytes());
}

namespace {

GrowStrategy
growStrategy(uint32_t initial) {
    return {initial, 0.1, 1, initial, 0.15};
}

}

TEST(ProtonConfigFetcherTest, require_that_allocation_config_is_propagated)
{
    ConfigTestFixture f1("test");
    DocumentDBConfigManager f2(f1.configId + "/test", "test");
    f1.protonBuilder.distribution.redundancy = 5;
    f1.protonBuilder.distribution.searchablecopies = 2;
    f1.addDocType("test");
    {
        auto& allocation = f1.protonBuilder.documentdb.back().allocation;
        allocation.initialnumdocs = 10000000;
        allocation.growfactor = 0.1;
        allocation.growbias = 1;
        allocation.amortizecount = 10000;
        allocation.multivaluegrowfactor = 0.15;
        allocation.maxDeadBytesRatio = 0.25;
        allocation.maxDeadAddressSpaceRatio = 0.3;
    }
    auto config = getDocumentDBConfig(f1, f2);
    {
        auto& alloc_config = config->get_alloc_config();
        EXPECT_EQ(AllocStrategy(growStrategy(20000000), CompactionStrategy(0.25, 0.3), 10000), alloc_config.make_alloc_strategy(SubDbType::READY));
        EXPECT_EQ(AllocStrategy(growStrategy(100000), CompactionStrategy(0.25, 0.3), 10000), alloc_config.make_alloc_strategy(SubDbType::REMOVED));
        EXPECT_EQ(AllocStrategy(growStrategy(30000000), CompactionStrategy(0.25, 0.3), 10000), alloc_config.make_alloc_strategy(SubDbType::NOTREADY));
    }
}

TEST(ProtonConfigFetcherTest, test_HwInfo_equality)
{
    EXPECT_TRUE(HwInfo::Cpu(1) == HwInfo::Cpu(1));
    EXPECT_FALSE(HwInfo::Cpu(1) == HwInfo::Cpu(2));
    EXPECT_TRUE(HwInfo::Memory(1) == HwInfo::Memory(1));
    EXPECT_FALSE(HwInfo::Memory(1) == HwInfo::Memory(2));
    EXPECT_TRUE(HwInfo::Disk(1, false, false) == HwInfo::Disk(1, false,false));
    EXPECT_FALSE(HwInfo::Disk(1, false, false) == HwInfo::Disk(1, false,true));
    EXPECT_FALSE(HwInfo::Disk(1, false, false) == HwInfo::Disk(1, true,false));
    EXPECT_FALSE(HwInfo::Disk(1, false, false) == HwInfo::Disk(2, false,false));
    EXPECT_TRUE(HwInfo(HwInfo::Disk(1, false, false), 1ul, 1ul) == HwInfo(HwInfo::Disk(1, false,false), 1ul, 1ul));
    EXPECT_FALSE(HwInfo(HwInfo::Disk(1, false, false), 1ul, 1ul) == HwInfo(HwInfo::Disk(1, false,false), 1ul, 2ul));
    EXPECT_FALSE(HwInfo(HwInfo::Disk(1, false, false), 1ul, 1ul) == HwInfo(HwInfo::Disk(1, false,false), 2ul, 1ul));
    EXPECT_FALSE(HwInfo(HwInfo::Disk(1, false, false), 1ul, 1ul) == HwInfo(HwInfo::Disk(2, false,false), 1ul, 1ul));
}
