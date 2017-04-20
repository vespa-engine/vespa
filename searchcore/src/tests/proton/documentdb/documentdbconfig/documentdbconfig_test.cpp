// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/searchcore/proton/server/documentdbconfig.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/config-summarymap.h>

using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search;
using proton::matching::RankingConstants;
using std::make_shared;
using std::shared_ptr;
using vespa::config::search::core::RankingConstantsConfig;

using ConfigSP = shared_ptr<DocumentDBConfig>;

class MyConfigBuilder {
private:
    test::DocumentDBConfigBuilder _builder;

public:
    MyConfigBuilder(int64_t generation, const Schema::SP &schema, const DocumentTypeRepo::SP &repo)
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
        _builder.attributes(make_shared<AttributesConfig>(builder));
        return *this;
    }
    MyConfigBuilder &addSummarymap() {
        SummarymapConfigBuilder builder;
        builder.override.resize(1);
        builder.override.back().field = "my_summary_field";
        _builder.summarymap(make_shared<SummarymapConfig>(builder));
        return *this;
    }
    ConfigSP build() {
        return _builder.build();
    }
};

struct Fixture {
    Schema::SP schema;
    DocumentTypeRepo::SP repo;
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

TEST_MAIN() { TEST_RUN_ALL(); }
