// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/native_dot_product_feature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/util/stringfmt.h>

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
    virtual void visitDumpFeature(const vespalib::string &) override {
        TEST_ERROR("no features should be dumped");
    }
    FeatureDumpFixture() : IDumpFeatureVisitor() {}
};

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
                const vespalib::string &featureName = fooFeatureName)
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
        ASSERT_TRUE(rankSetup.compile());
        match_data = mdl.createMatchData();
        rankProgram = rankSetup.create_first_phase_program();
        rankProgram->setup(*match_data, queryEnv);
    }
    feature_t getScore(uint32_t docId) {
        return Utils::getScoreFeature(*rankProgram, docId);
    }
    void setFooWeight(uint32_t i, uint32_t docId, int32_t index_weight) {
        ASSERT_LESS(i, fooHandles.size());
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(index_weight);
        match_data->resolveTermField(fooHandles[i])->reset(docId);
        match_data->resolveTermField(fooHandles[i])->appendPosition(pos);
    }
    void setBarWeight(uint32_t i, uint32_t docId, int32_t index_weight) {
        ASSERT_LESS(i, barHandles.size());
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(index_weight);
        match_data->resolveTermField(barHandles[i])->reset(docId);
        match_data->resolveTermField(barHandles[i])->appendPosition(pos);
    }
};

TEST_F("require that blueprint can be created from factory", BlueprintFactoryFixture) {
    Blueprint::SP bp = f.factory.createBlueprint("nativeDotProduct");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<NativeDotProductBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that no features are dumped", NativeDotProductBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST_FF("require that setup can be done on index field", NativeDotProductBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
}

TEST_FF("require that setup can be done on attribute field", NativeDotProductBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "bar")));
}

TEST_FF("require that setup fails for unknown field", NativeDotProductBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(unknown)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "unknown")));
}

TEST_FF("require that setup can be done without field", NativeDotProductBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>()));
}

TEST_F("require that not searching a field will give it 0.0 dot product", RankFixture(vec(), vec(1, 2, 3))) {
    EXPECT_EQUAL(0.0, f1.getScore(10));
}

TEST_F("require that dot product works for single match", RankFixture(vec(5), vec())) {
    f1.setFooWeight(0, 10, 7);
    EXPECT_EQUAL(35, f1.getScore(10));
}

TEST_F("require that dot product works for multiple matches", RankFixture(vec(1, 3, 5), vec())) {
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setFooWeight(2, 10, 6);
    EXPECT_EQUAL(44, f1.getScore(10));
}

TEST_F("require that stale data is ignored", RankFixture(vec(1, 3, 5), vec())) {
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 9, 4);
    f1.setFooWeight(2, 10, 6);
    EXPECT_EQUAL(32, f1.getScore(10));
}

TEST_F("require that data from other fields is ignored", RankFixture(vec(1, 3), vec(5, 7))) {
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setBarWeight(0, 10, 6);
    f1.setBarWeight(1, 10, 8);
    EXPECT_EQUAL(14, f1.getScore(10));
}

TEST_F("require that not specifying field includes all term/field combinations", RankFixture(vec(1, 3), vec(5, 7), anyFeatureName)) {
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, 4);
    f1.setBarWeight(0, 10, 6);
    f1.setBarWeight(1, 10, 8);
    EXPECT_EQUAL(100, f1.getScore(10));
}

TEST_F("require that negative weights in the index works", RankFixture(vec(1, 3), vec())) {
    f1.setFooWeight(0, 10, 2);
    f1.setFooWeight(1, 10, -4);
    EXPECT_EQUAL(-10, f1.getScore(10));
}

TEST_MAIN() { TEST_RUN_ALL(); }
