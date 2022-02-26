// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/raw_score_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
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
        ASSERT_TRUE(rankSetup.compile());
        match_data = mdl.createMatchData();
        rankProgram = rankSetup.create_first_phase_program();
        rankProgram->setup(*match_data, queryEnv);
    }
    feature_t getScore(uint32_t docId) {
        return Utils::getScoreFeature(*rankProgram, docId);
    }
    void setScore(TermFieldHandle handle, uint32_t docId, feature_t score) {
        match_data->resolveTermField(handle)->setRawScore(docId, score);
    }
    void setFooScore(uint32_t i, uint32_t docId, feature_t score) {
        ASSERT_LESS(i, fooHandles.size());
        setScore(fooHandles[i], docId, score);
    }
    void setBarScore(uint32_t i, uint32_t docId, feature_t score) {
        ASSERT_LESS(i, barHandles.size());
        setScore(barHandles[i], docId, score);
    }
};

TEST_F("require that blueprint can be created from factory", BlueprintFactoryFixture) {
    Blueprint::SP bp = f.factory.createBlueprint("rawScore");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<RawScoreBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that no features are dumped", RawScoreBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST_FF("require that setup can be done on index field", RawScoreBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
}

TEST_FF("require that setup can be done on attribute field", RawScoreBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "bar")));
}

TEST_FF("require that setup fails for unknown field", RawScoreBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "unknown")));
}

TEST_F("require that not searching a filed will give it 0.0 raw score", RankFixture(0, 3)) {
    EXPECT_EQUAL(0.0, f1.getScore(10));
}

TEST_F("require that raw score can be obtained", RankFixture(1, 0)) {
    f1.setFooScore(0, 10, 5.0);
    EXPECT_EQUAL(5.0, f1.getScore(10));
}

TEST_F("require that multiple raw scores are accumulated", RankFixture(3, 0)) {
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 10, 2.0);
    f1.setFooScore(2, 10, 3.0);
    EXPECT_EQUAL(6.0, f1.getScore(10));
}

TEST_F("require that stale raw scores are ignored", RankFixture(3, 0)) {
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 9, 2.0);
    f1.setFooScore(2, 10, 3.0);
    EXPECT_EQUAL(4.0, f1.getScore(10));
}

TEST_F("require that raw scores from other fields are ignored", RankFixture(2, 2)) {
    f1.setFooScore(0, 10, 1.0);
    f1.setFooScore(1, 10, 2.0);
    f1.setBarScore(0, 10, 5.0);
    f1.setBarScore(1, 10, 6.0);
    EXPECT_EQUAL(3.0, f1.getScore(10));
}

TEST_MAIN() { TEST_RUN_ALL(); }
