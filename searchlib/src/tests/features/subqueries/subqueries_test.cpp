// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/subqueries_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
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
    virtual void visitDumpFeature(const vespalib::string &) override {
        TEST_ERROR("no features should be dumped");
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
        ASSERT_TRUE(rankSetup.compile());
        match_data = mdl.createMatchData();
        rankProgram = rankSetup.create_first_phase_program();
        rankProgram->setup(*match_data, queryEnv);
    }
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
        ASSERT_LESS(i, fooHandles.size());
        setSubqueries(fooHandles[i], docId, subqueries);
    }
    void setBarSubqueries(uint32_t i, uint32_t docId, uint64_t subqueries) {
        ASSERT_LESS(i, barHandles.size());
        setSubqueries(barHandles[i], docId, subqueries);
    }
};

TEST_F("require that blueprint can be created from factory",
       BlueprintFactoryFixture) {
    Blueprint::SP bp = f.factory.createBlueprint("subqueries");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<SubqueriesBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that no features are dumped",
         SubqueriesBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST_FF("require that setup can be done on index field",
        SubqueriesBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, {"foo"}));
}

TEST_FF("require that setup can be done on attribute field",
        SubqueriesBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, {"bar"}));
}

TEST_FF("require that setup fails for unknown field",
        SubqueriesBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_FALSE(((Blueprint&)f1).setup(f2.indexEnv, {"unknown"}));
}

TEST_F("require that not searching a field will give it 0 subqueries",
       RankFixture(0, 3)) {
    EXPECT_EQUAL(0, f1.getSubqueries(10));
}

TEST_F("require that subqueries can be obtained", RankFixture(1, 0)) {
    f1.setFooSubqueries(0, 10, 0x1234);
    EXPECT_EQUAL(0x1234, f1.getSubqueries(10));
}

TEST_F("require that msb subqueries can be obtained",
       RankFixture(1, 0, "subqueries(foo).msb")) {
    f1.setFooSubqueries(0, 10, 0x123412345678ULL);
    EXPECT_EQUAL(0x1234, f1.getSubqueries(10));
}

TEST_F("require that multiple subqueries are accumulated", RankFixture(3, 0)) {
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 10, 2);
    f1.setFooSubqueries(2, 10, 4);
    EXPECT_EQUAL(7, f1.getSubqueries(10));
}

TEST_F("require that stale subqueries are ignored", RankFixture(3, 0)) {
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 9, 2);
    f1.setFooSubqueries(2, 10, 4);
    EXPECT_EQUAL(5, f1.getSubqueries(10));
}

TEST_F("require that subqueries from other fields are ignored",
       RankFixture(2, 2)) {
    f1.setFooSubqueries(0, 10, 1);
    f1.setFooSubqueries(1, 10, 2);
    f1.setBarSubqueries(0, 10, 4);
    f1.setBarSubqueries(1, 10, 8);
    EXPECT_EQUAL(3, f1.getSubqueries(10));
}

TEST_MAIN() { TEST_RUN_ALL(); }
