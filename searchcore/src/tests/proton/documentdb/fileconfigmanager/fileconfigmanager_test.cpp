// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-mycfg.h"
#include <vespa/config-attributes.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <filesystem>


using namespace cloud::config::filedistribution;
using namespace config;
using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search::core;
using namespace vespa::config::search;
using namespace std::chrono_literals;
using vespa::config::content::core::BucketspacesConfig;
using search::fef::OnnxModels;
using search::fef::RankingConstants;
using search::fef::RankingExpressions;

using DBCM = DocumentDBConfigHelper;
using DocumenttypesConfigSP = DocumentDBConfig::DocumenttypesConfigSP;
using vespalib::nbostream;
using vespalib::HwInfo;

std::string myId("myconfigid");
std::string base_dir("out");
std::string document_type_name("dummy");
std::string placeholder_document_type_name("test");

DocumentDBConfig::SP
makeBaseConfigSnapshot(FNET_Transport & transport)
{
    ::config::DirSpec spec(TEST_PATH("cfg"));

    DBCM dbcm(spec, placeholder_document_type_name);
    DocumenttypesConfigSP dtcfg(::config::ConfigGetter<DocumenttypesConfig>::getConfig("", spec).release());
    auto b = std::make_shared<BootstrapConfig>(1, dtcfg,
                                               std::make_shared<DocumentTypeRepo>(*dtcfg),
                                               std::make_shared<ProtonConfig>(),
                                               std::make_shared<FiledistributorrpcConfig>(),
                                               std::make_shared<BucketspacesConfig>(),
                                               std::make_shared<TuneFileDocumentDB>(), HwInfo());
    dbcm.forwardConfig(b);
    dbcm.nextGeneration(transport, 0ms);
    DocumentDBConfig::SP snap = dbcm.getConfig();
    snap->setConfigId(myId);
    return snap;
}

std::vector<SerialNum>
get_valid_serials(FileConfigManager& cm)
{
    std::vector<SerialNum> serials;
    auto serial = cm.getPrevValidSerial(1000);
    while (serial > 0) {
        serials.emplace_back(serial);
        serial = cm.getPrevValidSerial(serial);
    };
    return {serials.rbegin(), serials.rend()};
}

std::vector<SerialNum>
make_serials(std::vector<SerialNum> serials)
{
    return serials;
}

DocumentDBConfig::SP
makeEmptyConfigSnapshot()
{
    return test::DocumentDBConfigBuilder(0, std::make_shared<const Schema>(), "client",
                                         placeholder_document_type_name).build();
}

void
assertEqualSnapshot(const DocumentDBConfig &exp, const DocumentDBConfig &act)
{
    EXPECT_TRUE(exp.getRankProfilesConfig() == act.getRankProfilesConfig());
    EXPECT_TRUE(exp.getRankingConstants() == act.getRankingConstants());
    EXPECT_TRUE(exp.getRankingExpressions() == act.getRankingExpressions());
    EXPECT_TRUE(exp.getOnnxModels() == act.getOnnxModels());
    EXPECT_EQ(0u, exp.getRankingConstants().size());
    EXPECT_EQ(0u, exp.getRankingExpressions().size());
    EXPECT_EQ(0u, exp.getOnnxModels().size());
    EXPECT_TRUE(exp.getIndexschemaConfig() == act.getIndexschemaConfig());
    EXPECT_TRUE(exp.getAttributesConfig() == act.getAttributesConfig());
    EXPECT_TRUE(exp.getSummaryConfig() == act.getSummaryConfig());
    EXPECT_TRUE(exp.getJuniperrcConfig() == act.getJuniperrcConfig());
    EXPECT_TRUE(exp.getImportedFieldsConfig() == act.getImportedFieldsConfig());
    EXPECT_EQ(0u, exp.getImportedFieldsConfig().attribute.size());

    int expTypeCount = 0;
    int actTypeCount = 0;
    exp.getDocumentTypeRepoSP()->forEachDocumentType([&expTypeCount](const DocumentType &) noexcept {
        expTypeCount++;
    });
    act.getDocumentTypeRepoSP()->forEachDocumentType([&actTypeCount](const DocumentType &) noexcept {
        actTypeCount++;
    });
    EXPECT_EQ(expTypeCount, actTypeCount);
    EXPECT_TRUE(*exp.getSchemaSP() == *act.getSchemaSP());
    EXPECT_EQ(expTypeCount, actTypeCount);
    EXPECT_EQ(exp.getConfigId(), act.getConfigId());
}

DocumentDBConfig::SP
addConfigsThatAreNotSavedToDisk(const DocumentDBConfig &cfg)
{
    test::DocumentDBConfigBuilder builder(cfg);
    RankingConstants::Vector constants = {{"my_name", "my_type", "my_path"}};
    builder.rankingConstants(std::make_shared<RankingConstants>(constants));

    auto expr_list = std::make_shared<RankingExpressions>();
    expr_list->add("my_expr", "my_file");
    builder.rankingExpressions(expr_list);

    OnnxModels::Vector models;
    models.emplace_back("my_model_name", "my_model_file");
    builder.onnxModels(std::make_shared<OnnxModels>(std::move(models)));

    ImportedFieldsConfigBuilder importedFields;
    importedFields.attribute.resize(1);
    importedFields.attribute.back().name = "my_name";
    builder.importedFields(std::make_shared<ImportedFieldsConfig>(importedFields));
    return builder.build();
}

class FileConfigManagerTest : public ::testing::Test {
protected:
    Transport                          _transport;
    std::unique_ptr<FileConfigManager> _cm;
    FileConfigManagerTest();
    ~FileConfigManagerTest() override;
    void SetUp() override;
    void TearDown() override;
    void make_file_config_manager();
};

FileConfigManagerTest::FileConfigManagerTest()
    : ::testing::Test(),
        _transport(),
        _cm()
{
}

FileConfigManagerTest::~FileConfigManagerTest() = default;

void
FileConfigManagerTest::SetUp()
{
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    make_file_config_manager();
}

void
FileConfigManagerTest::TearDown()
{
    _cm.reset();
    std::filesystem::remove_all(std::filesystem::path(base_dir));
}

void
FileConfigManagerTest::make_file_config_manager()
{
    _cm.reset();
    _cm = std::make_unique<FileConfigManager>(_transport.transport(), base_dir, myId, document_type_name);
}

TEST_F(FileConfigManagerTest, requireThatConfigCanBeSavedAndLoaded)
{
    auto initial_size_on_disk = _cm->get_size_on_disk();
    EXPECT_LT(0, initial_size_on_disk);
    auto f2(makeBaseConfigSnapshot(_transport.transport()));
    auto fullCfg = addConfigsThatAreNotSavedToDisk(*f2);
    _cm->saveConfig(*fullCfg, 20);
    auto size_on_disk = _cm->get_size_on_disk();
    EXPECT_LT(initial_size_on_disk, size_on_disk);
    DocumentDBConfig::SP esnap(makeEmptyConfigSnapshot());
    make_file_config_manager();
    _cm->loadConfig(*esnap, 20, esnap);
    EXPECT_EQ(size_on_disk, _cm->get_size_on_disk());
    assertEqualSnapshot(*f2, *esnap);
}

TEST_F(FileConfigManagerTest, requireThatConfigCanBeSerializedAndDeserialized)
{
    auto initial_size_on_disk = _cm->get_size_on_disk();
    auto f2(makeBaseConfigSnapshot(_transport.transport()));
    _cm->saveConfig(*f2, 30);
    auto size_on_disk = _cm->get_size_on_disk();
    auto delta_size_on_disk = size_on_disk - initial_size_on_disk;
    nbostream stream;
    _cm->serializeConfig(30, stream);
    _cm->deserializeConfig(40, stream);
    EXPECT_EQ(initial_size_on_disk + 2 * delta_size_on_disk, _cm->get_size_on_disk());
    make_file_config_manager();
    EXPECT_EQ(initial_size_on_disk + 2 * delta_size_on_disk, _cm->get_size_on_disk());
    auto fsnap(makeEmptyConfigSnapshot());
    _cm->loadConfig(*fsnap, 40, fsnap);
    assertEqualSnapshot(*f2, *fsnap);
    EXPECT_EQ(document_type_name, fsnap->getDocTypeName());
}

TEST_F(FileConfigManagerTest, requireThatConfigCanBeLoadedWithoutExtraConfigsDataFile)
{
    auto f2(makeBaseConfigSnapshot(_transport.transport()));
    _cm->saveConfig(*f2, 70);
    EXPECT_FALSE(std::filesystem::remove(std::filesystem::path("out/config-70/extraconfigs.dat")));
    auto esnap(makeEmptyConfigSnapshot());
    make_file_config_manager();
    _cm->loadConfig(*esnap, 70, esnap);
}

TEST_F(FileConfigManagerTest, requireThatPruneKeepsLatestOldConfig)
{
    auto initial_size_on_disk = _cm->get_size_on_disk();
    auto f2(makeBaseConfigSnapshot(_transport.transport()));
    _cm->saveConfig(*f2, 30);
    auto delta_size_on_disk = _cm->get_size_on_disk() - initial_size_on_disk;
    _cm->saveConfig(*f2, 40);
    _cm->saveConfig(*f2, 50);
    _cm->saveConfig(*f2, 60);
    EXPECT_EQ(make_serials({30, 40, 50, 60}), get_valid_serials(*_cm));
    EXPECT_EQ(initial_size_on_disk + 4 * delta_size_on_disk, _cm->get_size_on_disk());
    _cm->prune(50);
    EXPECT_EQ(make_serials({50, 60}), get_valid_serials(*_cm));
    EXPECT_EQ(initial_size_on_disk + 2 * delta_size_on_disk, _cm->get_size_on_disk());
    _cm->prune(59);
    EXPECT_EQ(make_serials({50, 60}), get_valid_serials(*_cm));
    EXPECT_EQ(initial_size_on_disk + 2 * delta_size_on_disk, _cm->get_size_on_disk());
    _cm->prune(60);
    EXPECT_EQ(make_serials({60}), get_valid_serials(*_cm));
    EXPECT_EQ(initial_size_on_disk + delta_size_on_disk, _cm->get_size_on_disk());
    _cm->prune(70);
    EXPECT_EQ(make_serials({60}), get_valid_serials(*_cm));
    EXPECT_EQ(initial_size_on_disk + delta_size_on_disk, _cm->get_size_on_disk());
}

TEST_F(FileConfigManagerTest, requireThatVisibilityDelayIsPropagated)
{
    auto f2(makeBaseConfigSnapshot(_transport.transport()));
    _cm->saveConfig(*f2, 80);
    DocumentDBConfig::SP esnap(makeEmptyConfigSnapshot());
    make_file_config_manager();
    ProtonConfigBuilder protonConfigBuilder;
    ProtonConfigBuilder::Documentdb ddb;
    ddb.inputdoctypename = document_type_name;
    ddb.visibilitydelay = 61.0;
    protonConfigBuilder.documentdb.push_back(ddb);
    protonConfigBuilder.maxvisibilitydelay = 100.0;
    _cm->setProtonConfig(std::make_shared<ProtonConfig>(protonConfigBuilder));
    _cm->loadConfig(*esnap, 80, esnap);
    EXPECT_EQ(61s, esnap->getMaintenanceConfigSP()->getVisibilityDelay());
}

GTEST_MAIN_RUN_ALL_TESTS()
