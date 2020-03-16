// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/closenessfeature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;
using DataType = FieldInfo::DataType;

const vespalib::string labelFeatureName("closeness(label,nns)");
const vespalib::string fieldFeatureName("closeness(bar)");

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
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, DataType::INT64, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, DataType::TENSOR, "bar");
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    virtual void visitDumpFeature(const vespalib::string &) override {
        TEST_ERROR("no features should be dumped");
    }
    FeatureDumpFixture() : IDumpFeatureVisitor() {}
};

struct Labels {
    virtual void inject(Properties &p) const = 0;
    virtual ~Labels() {}
};
struct NoLabel : public Labels {
    virtual void inject(Properties &) const override {}    
};
struct SingleLabel : public Labels {
    vespalib::string label;
    uint32_t uid;
    SingleLabel(const vespalib::string &l, uint32_t x) : label(l), uid(x) {}
    virtual void inject(Properties &p) const override {
        vespalib::asciistream key;
        key << "vespa.label." << label << ".id";
        vespalib::asciistream value;
        value << uid;
        p.add(key.str(), value.str());
    }
};

struct RankFixture : BlueprintFactoryFixture, IndexFixture {
    QueryEnvironment         queryEnv;
    RankSetup                rankSetup;
    MatchDataLayout          mdl;
    MatchData::UP            match_data;
    RankProgram::UP          rankProgram;
    std::vector<TermFieldHandle> fooHandles;
    std::vector<TermFieldHandle> barHandles;
    RankFixture(size_t fooCnt, size_t barCnt, const Labels &labels, const vespalib::string &featureName)
        : queryEnv(&indexEnv), rankSetup(factory, indexEnv),
          mdl(), match_data(), rankProgram(), fooHandles(), barHandles()
    {
        for (size_t i = 0; i < fooCnt; ++i) {
            uint32_t fieldId = indexEnv.getFieldByName("foo")->id();
            fooHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.setUniqueId(i + 1);
            term.addField(fieldId).setHandle(fooHandles.back());
            queryEnv.getTerms().push_back(term);
        }
        for (size_t i = 0; i < barCnt; ++i) { 
            uint32_t fieldId = indexEnv.getFieldByName("bar")->id();
            barHandles.push_back(mdl.allocTermField(fieldId));
            SimpleTermData term;
            term.setUniqueId(fooCnt + i + 1);
            term.addField(fieldId).setHandle(barHandles.back());
            queryEnv.getTerms().push_back(term);
        }
        labels.inject(queryEnv.getProperties());
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
    Blueprint::SP bp = f.factory.createBlueprint("closeness");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<ClosenessBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that no features are dumped", ClosenessBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST_FF("require that setup can be done on random label", ClosenessBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(label,random_label)", f1.getBaseName().c_str()));
    EXPECT_TRUE(static_cast<Blueprint&>(f1).setup(f2.indexEnv, std::vector<vespalib::string>{"label", "random_label"}));
}

TEST_FF("require that no label gives 0 closeness", NoLabel(), RankFixture(2, 2, f1, labelFeatureName)) {
    EXPECT_EQUAL(0.0, f2.getScore(10));
}

TEST_FF("require that unrelated label gives 0 closeness", SingleLabel("unrelated", 1), RankFixture(2, 2, f1, labelFeatureName)) {
    EXPECT_EQUAL(0.0, f2.getScore(10));
}

TEST_FF("require that labeled item raw score can be obtained", SingleLabel("nns", 1), RankFixture(2, 2, f1, labelFeatureName)) {
    f2.setFooScore(0, 10, 5.0);
    EXPECT_EQUAL(1/(1+5.0), f2.getScore(10));
}

TEST_FF("require that field raw score can be obtained", NoLabel(), RankFixture(2, 2, f1, fieldFeatureName)) {
    f2.setBarScore(0, 10, 5.0);
    EXPECT_EQUAL(1/(1+5.0), f2.getScore(10));
}

TEST_FF("require that other raw scores are ignored", SingleLabel("nns", 2), RankFixture(2, 2, f1, labelFeatureName)) {
    f2.setFooScore(0, 10, 1.0);
    f2.setFooScore(1, 10, 2.0);
    f2.setBarScore(0, 10, 5.0);
    f2.setBarScore(1, 10, 6.0);
    EXPECT_EQUAL(1/(1+2.0), f2.getScore(10));
}

TEST_FF("require that the correct raw score is used", NoLabel(), RankFixture(2, 2, f1, fieldFeatureName)) {
    f2.setFooScore(0, 10, 3.0);
    f2.setFooScore(1, 10, 4.0);
    f2.setBarScore(0, 10, 8.0);
    f2.setBarScore(1, 10, 7.0);
    EXPECT_EQUAL(1/(1+7.0), f2.getScore(10));
}

TEST_FF("require that stale data is ignored", SingleLabel("nns", 2), RankFixture(2, 2, f1, labelFeatureName)) {
    f2.setFooScore(0, 10, 1.0);
    f2.setFooScore(1, 5, 2.0);
    EXPECT_EQUAL(0, f2.getScore(10));
}

TEST_MAIN() { TEST_RUN_ALL(); }
