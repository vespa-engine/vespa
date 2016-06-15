// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("documentdbconfig_test");

#include <vespa/searchcore/proton/server/documentdbconfig.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;
using namespace vespa::config::search;
using std::shared_ptr;
using std::make_shared;

typedef shared_ptr<DocumentDBConfig> DDBCSP;

namespace
{

DDBCSP
getConfig(int64_t generation, const Schema::SP &schema,
          shared_ptr<DocumentTypeRepo> repo,
          const RankProfilesConfig &rankProfiles)
{
    return make_shared<DocumentDBConfig>(
            generation,
            make_shared<RankProfilesConfig>(rankProfiles),
            make_shared<IndexschemaConfig>(),
            make_shared<AttributesConfig>(),
            make_shared<SummaryConfig>(),
            make_shared<SummarymapConfig>(),
            make_shared<summary::JuniperrcConfig>(),
            make_shared<DocumenttypesConfig>(),
            repo,
            make_shared<TuneFileDocumentDB>(),
            schema,
            make_shared<DocumentDBMaintenanceConfig>(),
            "client", "test");
}

}

TEST("Test that makeReplayConfig drops unneeded configs")
{
    RankProfilesConfigBuilder rp;
    using DDBC = DocumentDBConfig;
    shared_ptr<DocumentTypeRepo> repo(make_shared<DocumentTypeRepo>());
    Schema::SP schema(make_shared<Schema>());
    DDBCSP cfg0 = getConfig(4, schema, repo, rp);
    rp.rankprofile.resize(1);
    RankProfilesConfigBuilder::Rankprofile &rpr = rp.rankprofile.back();
    rpr.name = "dummy";
    DDBCSP cfg1 = getConfig(4, schema, repo, rp);
    EXPECT_FALSE(*cfg0 == *cfg1);
    DDBCSP cfg2 = DocumentDBConfig::makeReplayConfig(cfg1);
    EXPECT_TRUE(*cfg0 == *cfg2);
    EXPECT_TRUE(cfg0->getOriginalConfig().get() == nullptr);
    EXPECT_TRUE(cfg1->getOriginalConfig().get() == nullptr);
    EXPECT_TRUE(cfg2->getOriginalConfig().get() == cfg1.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(cfg0).get() == cfg0.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(cfg1).get() == cfg1.get());
    EXPECT_TRUE(DDBC::preferOriginalConfig(cfg2).get() == cfg1.get());
    DDBCSP cfg3;
    EXPECT_TRUE(DDBC::preferOriginalConfig(cfg3).get() == nullptr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
