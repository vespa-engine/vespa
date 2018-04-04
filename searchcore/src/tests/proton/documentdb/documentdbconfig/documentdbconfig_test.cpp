// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/searchcore/proton/server/documentdbconfig.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/config-summarymap.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>

using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search;
using proton::matching::RankingConstants;
using std::make_shared;
using std::shared_ptr;
using document::config_builder::DocumenttypesConfigBuilderHelper;
using document::config_builder::Struct;

using ConfigSP = shared_ptr<DocumentDBConfig>;

namespace {

const int32_t doc_type_id = 787121340;
const vespalib::string type_name = "test";
const vespalib::string header_name = type_name + ".header";
const vespalib::string body_name = type_name + ".body";

std::shared_ptr<const DocumentTypeRepo>
makeDocTypeRepo(bool hasField)
{
    DocumenttypesConfigBuilderHelper builder;
    Struct body(body_name);
    if (hasField) {
        body.addField("my_attribute", DataType::T_INT);
    }
    builder.document(doc_type_id, type_name, Struct(header_name), body);
    return make_shared<DocumentTypeRepo>(builder.config());
}

}

class MyConfigBuilder {
private:
    test::DocumentDBConfigBuilder _builder;

public:
    MyConfigBuilder(int64_t generation, const Schema::SP &schema, const std::shared_ptr<const DocumentTypeRepo> &repo)
        : _builder(generation, schema, "client", "test")
    {
        _builder.repo(repo);
    }
    MyConfigBuilder &addRankProfile() {
        RankProfilesConfigBuilder builder;
        builder.rankprofile.resize(1);
        RankProfilesConfigBuilder::Rankprofile &profile = builder.rankprofile.back();
        profile.name = "my_profile";
        _builder.rankProfiles(make_shared<RankProfilesConfig>(builder));
        return *this;
    }
    MyConfigBuilder &addRankingConstant() {
        RankingConstants::Vector constants = {{"my_name", "my_type", "my_path"}};
        _builder.rankingConstants(make_shared<RankingConstants>(constants));
        return *this;
    }
    MyConfigBuilder &addImportedField() {
        ImportedFieldsConfigBuilder builder;
        builder.attribute.resize(1);
        ImportedFieldsConfigBuilder::Attribute &attribute = builder.attribute.back();
        attribute.name = "my_name";
        attribute.referencefield = "my_ref";
        attribute.targetfield = "my_target";
        _builder.importedFields(make_shared<ImportedFieldsConfig>(builder));
        return *this;
    }
    MyConfigBuilder &addAttribute() {
        AttributesConfigBuilder builder;
        builder.attribute.resize(1);
        AttributesConfigBuilder::Attribute &attribute = builder.attribute.back();
        attribute.name = "my_attribute";
        attribute.datatype = AttributesConfigBuilder::Attribute::Datatype::INT32;
        _builder.attributes(make_shared<AttributesConfig>(builder));
        return *this;
    }
    MyConfigBuilder &addSummarymap() {
        SummarymapConfigBuilder builder;
        builder.override.resize(1);
        builder.override.back().field = "my_attribute";
        builder.override.back().command = "attribute";
        _builder.summarymap(make_shared<SummarymapConfig>(builder));
        return *this;
    }
    ConfigSP build() {
        return _builder.build();
    }
};

struct Fixture {
    Schema::SP schema;
    std::shared_ptr<const DocumentTypeRepo> repo;
    ConfigSP basicCfg;
    ConfigSP fullCfg;
    ConfigSP replayCfg;
    ConfigSP nullCfg;
    Fixture()
        : schema(make_shared<Schema>()),
          repo(make_shared<DocumentTypeRepo>()),
          basicCfg(),
          fullCfg(),
          replayCfg(),
          nullCfg()
    {
        basicCfg = MyConfigBuilder(4, schema, repo).addAttribute().build();
        fullCfg = MyConfigBuilder(4, schema, repo).addAttribute().
                                                   addRankProfile().
                                                   addRankingConstant().
                                                   addImportedField().
                                                   addSummarymap().
                                                   build();
        replayCfg = DocumentDBConfig::makeReplayConfig(fullCfg);
    }
};

TEST_F("require that makeReplayConfig() drops unneeded configs", Fixture)
{
    using DDBC = DocumentDBConfig;
    EXPECT_FALSE(*f.basicCfg == *f.fullCfg);
    EXPECT_TRUE(*f.basicCfg == *f.replayCfg);
    EXPECT_TRUE(f.basicCfg->getOriginalConfig().get() == nullptr);
    EXPECT_TRUE(f.fullCfg->getOriginalConfig().get() == nullptr);
    EXPECT_TRUE(f.replayCfg->getOriginalConfig().get() == f.fullCfg.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(f.basicCfg).get() == f.basicCfg.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(f.fullCfg).get() == f.fullCfg.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(f.replayCfg).get() == f.fullCfg.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(f.nullCfg).get() == nullptr);
}

struct DelayAttributeAspectFixture {
    Schema::SP schema;
    ConfigSP attrCfg;
    ConfigSP noAttrCfg;
    DelayAttributeAspectFixture(bool hasDocField)
        : schema(make_shared<Schema>()),
          attrCfg(),
          noAttrCfg()
    {
        attrCfg = MyConfigBuilder(4, schema, makeDocTypeRepo(true)).addAttribute().
                                                   addRankProfile().
                                                   addRankingConstant().
                                                   addImportedField().
                                                   addSummarymap().
                                                   build();
        noAttrCfg = MyConfigBuilder(4, schema, makeDocTypeRepo(hasDocField)).addRankProfile().
                         addRankingConstant().
                         addImportedField().
                         build();
    }

    void assertDelayedConfig(const DocumentDBConfig &testCfg) {
        EXPECT_FALSE(noAttrCfg->getAttributesConfig() == testCfg.getAttributesConfig());
        EXPECT_FALSE(noAttrCfg->getSummarymapConfig() == testCfg.getSummarymapConfig());
        EXPECT_TRUE(attrCfg->getAttributesConfig() == testCfg.getAttributesConfig());
        EXPECT_TRUE(attrCfg->getSummarymapConfig() == testCfg.getSummarymapConfig());
        EXPECT_TRUE(testCfg.getDelayedAttributeAspects());
    }
    void assertNotDelayedConfig(const DocumentDBConfig &testCfg) {
        EXPECT_TRUE(noAttrCfg->getAttributesConfig() == testCfg.getAttributesConfig());
        EXPECT_TRUE(noAttrCfg->getSummarymapConfig() == testCfg.getSummarymapConfig());
        EXPECT_FALSE(attrCfg->getAttributesConfig() == testCfg.getAttributesConfig());
        EXPECT_FALSE(attrCfg->getSummarymapConfig() == testCfg.getSummarymapConfig());
        EXPECT_FALSE(testCfg.getDelayedAttributeAspects());
    }
};

TEST_F("require that makeDelayedAttributeAspectConfig works, field remains when attribute removed", DelayAttributeAspectFixture(true))
{
    auto delayedRemove = DocumentDBConfig::makeDelayedAttributeAspectConfig(f.noAttrCfg, *f.attrCfg);
    TEST_DO(f.assertDelayedConfig(*delayedRemove));
}

TEST_F("require that makeDelayedAttributeAspectConfig works, field removed with attribute", DelayAttributeAspectFixture(false))
{
    auto removed = DocumentDBConfig::makeDelayedAttributeAspectConfig(f.noAttrCfg, *f.attrCfg);
    TEST_DO(f.assertNotDelayedConfig(*removed));
}

TEST_MAIN() { TEST_RUN_ALL(); }
