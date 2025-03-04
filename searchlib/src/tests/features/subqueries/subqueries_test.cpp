// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/subqueries_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

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
    virtual void visitDumpFeature(const std::string &) override {
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
    RankFixture(size_t fooCnt, size_t barCnt,
                std::string featureName = "subqueries(foo)")
        : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
          mdl(), match_data(), rankProgram(), fooHandles(), barHandles()
    {
        fooHandles = addFields(fooCnt, indexEnv.getFieldByName("foo")->id());
        barHandles = addFields(barCnt, indexEnv.getFieldByName("bar")->id());
        rankSetup.setFirstPhaseRank(featureName);
        rankSetup.setIgnoreDefaultRankFeatures(true);
        EXPECT_TRUE(rankSetup.compile());
        match_data = mdl.createMatchData();
        rankProgram = rankSetup.create_first_phase_program();
        rankProgram->setup(*match_data, queryEnv);
    }
    ~RankFixture();
    std::vector<TermFieldHandle> addFields(size_t count, uint32_t fieldId) {
        std::vector<TermFieldHandle> handles;
        for (size_t i = 0; i < count; ++i) {
            handles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.addField(fieldId).setHandle(handles.back());
            queryEnv.getTerms().push_back(term);
        }
        return handles;
    }
    feature_t getSubqueries(uint32_t docId) {
        return Utils::getScoreFeature(*rankProgram, docId);
    }
    void setSubqueries(TermFieldHandle handle, uint32_t docId,
                       uint64_t subqueries) {
        match_data->resolveTermField(handle)->setSubqueries(docId, subqueries);
    }
    void setFooSubqueries(uint32_t i, uint32_t docId, uint64_t subqueries) {
        ASSERT_LT(i, fooHandles.size());
        setSubqueries(fooHandles[i], docId, subqueries);
    }
    void setBarSubqueries(uint32_t i, uint32_t docId, uint64_t subqueries) {
        ASSERT_LT(i, barHandles.size());
        setSubqueries(barHandles[i], docId, subqueries);
    }
};

RankFixture::~RankFixture() = default;

TEST(SubQueriesTest, require_that_blueprint_can_be_created_from_factory) {
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("subqueries");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<SubqueriesBlueprint*>(bp.get()) != 0);
}

TEST(SubQueriesTest, require_that_no_features_are_dumped) {
    SubqueriesBlueprint f1;
    IndexFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST(SubQueriesTest, require_that_setup_can_be_done_on_index_field) {
    SubqueriesBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, {"foo"}));
}

TEST(SubQueriesTest, require_that_setup_can_be_done_on_attribute_field) {
    SubqueriesBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, {"bar"}));
}

TEST(SubQueriesTest, require_that_setup_fails_for_unknown_field) {
    SubqueriesBlueprint f1;
    IndexFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_FALSE(((Blueprint&)f1).setup(f2.indexEnv, {"unknown"}));
}

TEST(SubQueriesTest, require_that_not_searching_a_field_will_give_it_0_subqueries) {
    RankFixture f1(0, 3);
    EXPECT_EQ(0.0, f1.getSubqueries(10));
}

TEST(SubQueriesTest, require_that_subqueries_can_be_obtained) {
    RankFixture f1(1, 0);
    f1.setFooSubqueries(0, 10, 0x1234);
    EXPECT_EQ(static_cast<double>(0x1234), f1.getSubqueries(10));
}

TEST(SubQueriesTest, require_that_msb_subqueries_can_be_obtained) {
    RankFixture f1(1, 0, "subqueries(foo).msb");
    f1.setFooSubqueries(0, 10, 0x123412345678ULL);
    EXPECT_EQ(static_cast<double>(0x1234), f1.getSubqueries(10));
}

TEST(SubQueriesTest, require_that_multiple_subqueries_are_accumulated) {
    RankFixture f1(3, 0);
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 10, 2);
    f1.setFooSubqueries(2, 10, 4);
    EXPECT_EQ(7.0, f1.getSubqueries(10));
}

TEST(SubQueriesTest, require_that_stale_subqueries_are_ignored) {
    RankFixture f1(3, 0);
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 9, 2);
    f1.setFooSubqueries(2, 10, 4);
    EXPECT_EQ(5.0, f1.getSubqueries(10));
}

TEST(SubQueriesTest, require_that_subqueries_from_other_fields_are_ignored) {
    RankFixture f1(2, 2);
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 10, 2);
    f1.setBarSubqueries(0, 10, 4);
    f1.setBarSubqueries(1, 10, 8);
    EXPECT_EQ(3.0, f1.getSubqueries(10));
}

GTEST_MAIN_RUN_ALL_TESTS()
