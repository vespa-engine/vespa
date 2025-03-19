// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/native_dot_product_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

const std::string fooFeatureName("nativeDotProduct(foo)");
const std::string anyFeatureName("nativeDotProduct");

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
        builder.addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
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

std::vector<uint32_t> vec() {
    std::vector<uint32_t> ret;
    return ret;
}

std::vector<uint32_t> vec(uint32_t w1) {
    std::vector<uint32_t> ret;
    ret.push_back(w1);
    return ret;
}

std::vector<uint32_t> vec(uint32_t w1, uint32_t w2) {
    std::vector<uint32_t> ret;
    ret.push_back(w1);
    ret.push_back(w2);
    return ret;
}

std::vector<uint32_t> vec(uint32_t w1, uint32_t w2, uint32_t w3) {
    std::vector<uint32_t> ret;
    ret.push_back(w1);
    ret.push_back(w2);
    ret.push_back(w3);
    return ret;
}

struct RankFixture : BlueprintFactoryFixture, IndexFixture {
    QueryEnvironment         queryEnv;
    RankSetup                rankSetup;
    MatchDataLayout          mdl;
    MatchData::UP            match_data;
    RankProgram::UP          rankProgram;
    std::vector<TermFieldHandle> fooHandles;
    std::vector<TermFieldHandle> barHandles;
    RankFixture(const std::vector<uint32_t> &fooWeights,
                const std::vector<uint32_t> &barWeights,
                const std::string &featureName = fooFeatureName)
        : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
          mdl(), match_data(), rankProgram(), fooHandles(), barHandles()
    {
        for (size_t i = 0; i < fooWeights.size(); ++i) {
            uint32_t fieldId = indexEnv.getFieldByName("foo")->id();
            fooHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.addField(fieldId).setHandle(fooHandles.back());
            term.setWeight(search::query::Weight(fooWeights[i]));
            queryEnv.getTerms().push_back(term);
        }
        for (size_t i = 0; i < barWeights.size(); ++i) { 
            uint32_t fieldId = indexEnv.getFieldByName("bar")->id();
            barHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.addField(fieldId).setHandle(barHandles.back());
            term.setWeight(search::query::Weight(barWeights[i]));
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
    void setFooWeight(uint32_t i, uint32_t docId, int32_t index_weight) {
        ASSERT_LT(i, fooHandles.size());
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(index_weight);
        match_data->resolveTermField(fooHandles[i])->reset(docId);
        match_data->resolveTermField(fooHandles[i])->appendPosition(pos);
    }
    void setBarWeight(uint32_t i, uint32_t docId, int32_t index_weight) {
        ASSERT_LT(i, barHandles.size());
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(index_weight);
        match_data->resolveTermField(barHandles[i])->reset(docId);
        match_data->resolveTermField(barHandles[i])->appendPosition(pos);
    }
};

RankFixture::~RankFixture() = default;

TEST(NativeDotProductTest, require_that_blueprint_can_be_created_from_factory) {
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("nativeDotProduct");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<NativeDotProductBlueprint*>(bp.get()) != 0);
}

TEST(NativeDotProductTest, require_that_no_features_are_dumped) {
    NativeDotProductBlueprint f1;
    IndexFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST(NativeDotProductTest, require_that_setup_can_be_done_on_index_field) {
    NativeDotProductBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "foo")));
}

TEST(NativeDotProductTest, require_that_setup_can_be_done_on_attribute_field) {
    NativeDotProductBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "bar")));
}

TEST(NativeDotProductTest, require_that_setup_fails_for_unknown_field) {
    NativeDotProductBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>(1, "unknown")));
}

TEST(NativeDotProductTest, require_that_setup_can_be_done_without_field) {
    NativeDotProductBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<std::string>()));
}

TEST(NativeDotProductTest, require_that_not_searching_a_field_will_give_it_0_dot_product) {
    RankFixture f1(vec(), vec(1, 2, 3));
    EXPECT_EQ(0.0, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_dot_product_works_for_single_match) {
    RankFixture f1(vec(5), vec());
    f1.setFooWeight(0, 10, 7);
    EXPECT_EQ(35, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_dot_product_works_for_multiple_matches) {
    RankFixture f1(vec(1, 3, 5), vec());
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setFooWeight(2, 10, 6);
    EXPECT_EQ(44, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_stale_data_is_ignored) {
    RankFixture f1(vec(1, 3, 5), vec());
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 9, 4);
    f1.setFooWeight(2, 10, 6);
    EXPECT_EQ(32, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_data_from_other_fields_is_ignored) {
    RankFixture f1(vec(1, 3), vec(5, 7));
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setBarWeight(0, 10, 6);
    f1.setBarWeight(1, 10, 8);
    EXPECT_EQ(14, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_not_specifying_field_includes_all_term_field_combinations) {
    RankFixture f1(vec(1, 3), vec(5, 7), anyFeatureName);
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setBarWeight(0, 10, 6);
    f1.setBarWeight(1, 10, 8);
    EXPECT_EQ(100, f1.getScore(10));
}

TEST(NativeDotProductTest, require_that_negative_weights_in_the_index_works) {
    RankFixture f1(vec(1, 3), vec());
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, -4);
    EXPECT_EQ(-10, f1.getScore(10));
}

GTEST_MAIN_RUN_ALL_TESTS()
