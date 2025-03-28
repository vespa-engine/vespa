// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/raw_score_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

const std::string featureName("rawScore(foo)");

struct BlueprintFactoryFixture {
    BlueprintFactory factory;
    BlueprintFactoryFixture() : factory()
    {
        setup_search_features(factory);
    }
};

struct IndexFixture {
    IndexEnvironment indexEnv;
    IndexFixture() : indexEnv()
    {
        IndexEnvironmentBuilder builder(indexEnv);
        builder.addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    void visitDumpFeature(const std::string &) override {
        FAIL() << "no features should be dumped";
    }
    FeatureDumpFixture() : IDumpFeatureVisitor() {}
    ~FeatureDumpFixture() override;
};

FeatureDumpFixture::~FeatureDumpFixture() = default;

struct RankFixture : BlueprintFactoryFixture, IndexFixture {
    QueryEnvironment         queryEnv;
    RankSetup                rankSetup;
    MatchDataLayout          mdl;
    MatchData::UP            match_data;
    RankProgram::UP          rankProgram;
    std::vector<TermFieldHandle> fooHandles;
    std::vector<TermFieldHandle> barHandles;
    RankFixture(size_t fooCnt, size_t barCnt)
        : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
          mdl(), match_data(), rankProgram(), fooHandles(), barHandles()
    {
        for (size_t i = 0; i < fooCnt; ++i) {
            uint32_t fieldId = indexEnv.getFieldByName("foo")->id();
            fooHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.addField(fieldId).setHandle(fooHandles.back());
            queryEnv.getTerms().push_back(term);
        }
        for (size_t i = 0; i < barCnt; ++i) { 
            uint32_t fieldId = indexEnv.getFieldByName("bar")->id();
            barHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.addField(fieldId).setHandle(barHandles.back());
            queryEnv.getTerms().push_back(term);
        }
        rankSetup.setFirstPhaseRank(featureName);
        rankSetup.setIgnoreDefaultRankFeatures(true);
        EXPECT_TRUE(rankSetup.compile());
        match_data = mdl.createMatchData();
        rankProgram = rankSetup.create_first_phase_program();
        rankProgram->setup(*match_data, queryEnv);
    }
    ~RankFixture();
    feature_t getScore(uint32_t docId) {
        return Utils::getScoreFeature(*rankProgram, docId);
    }
    void setScore(TermFieldHandle handle, uint32_t docId, feature_t score) {
        match_data->resolveTermField(handle)->setRawScore(docId, score);
    }
    void setFooScore(uint32_t i, uint32_t docId, feature_t score) {
        ASSERT_LT(i, fooHandles.size());
        setScore(fooHandles[i], docId, score);
    }
    void setBarScore(uint32_t i, uint32_t docId, feature_t score) {
        ASSERT_LT(i, barHandles.size());
        setScore(barHandles[i], docId, score);
    }
};

RankFixture::~RankFixture() = default;

TEST(RawScoreTest, require_that_blueprint_can_be_created_from_factory) {
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("rawScore");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<RawScoreBlueprint*>(bp.get()) != 0);
}

TEST(RawScoreTest, require_that_no_features_are_dumped) {
    RawScoreBlueprint f1;
    IndexFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST(RawScoreTest, require_that_setup_can_be_done_on_index_field) {
    RawScoreBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "foo")));
}

TEST(RawScoreTest, require_that_setup_can_be_done_on_attribute_field) {
    RawScoreBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "bar")));
}

TEST(RawScoreTest, require_that_setup_fails_for_unknown_field) {
    RawScoreBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "unknown")));
}

TEST(RawScoreTest, require_that_not_searching_a_field_will_give_it_0_raw_score) {
    RankFixture f1(0, 3);
    EXPECT_EQ(0.0, f1.getScore(10));
}

TEST(RawScoreTest, require_that_raw_score_can_be_obtained) {
    RankFixture f1(1, 0);
    f1.setFooScore(0, 10, 5.0);
    EXPECT_EQ(5.0, f1.getScore(10));
}

TEST(RawScoreTest, require_that_multiple_raw_scores_are_accumulated) {
    RankFixture f1(3, 0);
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 10, 2.0);
    f1.setFooScore(2, 10, 3.0);
    EXPECT_EQ(6.0, f1.getScore(10));
}

TEST(RawScoreTest, require_that_stale_raw_scores_are_ignored) {
    RankFixture f1(3, 0);
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 9, 2.0);
    f1.setFooScore(2, 10, 3.0);
    EXPECT_EQ(4.0, f1.getScore(10));
}

TEST(RawScoreTest, require_that_raw_scores_from_other_fields_are_ignored) {
    RankFixture f1(2, 2);
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 10, 2.0);
    f1.setBarScore(0, 10, 5.0);
    f1.setBarScore(1, 10, 6.0);
    EXPECT_EQ(3.0, f1.getScore(10));
}

GTEST_MAIN_RUN_ALL_TESTS()
