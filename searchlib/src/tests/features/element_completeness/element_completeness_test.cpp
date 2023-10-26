// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/features/element_completeness_feature.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using CollectionType = FieldInfo::CollectionType;

std::vector<vespalib::string> featureNamesFoo() {
    std::vector<vespalib::string> f;
    f.push_back("elementCompleteness(foo).completeness");
    f.push_back("elementCompleteness(foo).fieldCompleteness");
    f.push_back("elementCompleteness(foo).queryCompleteness");
    f.push_back("elementCompleteness(foo).elementWeight");
    return f;
}

const size_t TOTAL = 0;
const size_t FIELD = 1;
const size_t QUERY = 2;
const size_t WEIGHT = 3;

FtIndex indexFoo() {
    FtIndex idx;
    idx.field("foo");
    return idx;
}

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
        builder.addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        builder.addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
    }
};

struct FeatureDumpFixture : public IDumpFeatureVisitor {
    std::vector<vespalib::string> expect;
    size_t dumped;
    virtual void visitDumpFeature(const vespalib::string &name) override {
        EXPECT_LESS(dumped, expect.size());
        EXPECT_EQUAL(expect[dumped++], name);
    }
    FeatureDumpFixture() : IDumpFeatureVisitor(), expect(featureNamesFoo()), dumped(0) {}
};

struct RankFixture : BlueprintFactoryFixture {
    Properties idxProps;
    RankFixture() : BlueprintFactoryFixture(), idxProps() {}
    void test(const vespalib::string &queryStr, const FtIndex &index,
              feature_t field, feature_t query, int32_t weight = 1, feature_t factor = 0.5,
              bool useStaleMatchData = false)
    {
        std::vector<vespalib::string> names = featureNamesFoo();
        ASSERT_TRUE(names.size() == 4u);
        RankResult expect;
        expect.addScore(names[TOTAL], field*factor + query*(1-factor))
            .addScore(names[FIELD], field).addScore(names[QUERY], query)
            .addScore(names[WEIGHT], (double)weight);
        FtFeatureTest ft(factory, names);
        ft.getIndexEnv().getProperties().import(idxProps);
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "baz");
        FtTestApp::FT_SETUP(ft, FtUtil::toQuery(queryStr), index, 1);
        RankResult actual;
        EXPECT_TRUE(ft.executeOnly(actual, useStaleMatchData ? 2 : 1));
        for (size_t i = 0; i < names.size(); ++i) {
            TEST_STATE(names[i].c_str());
            EXPECT_EQUAL(expect.getScore(names[i]), actual.getScore(names[i]));
        }
    }
};

TEST_F("require that blueprint can be created from factory", BlueprintFactoryFixture) {
    Blueprint::SP bp = f.factory.createBlueprint("elementCompleteness");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<ElementCompletenessBlueprint*>(bp.get()) != 0);
}

TEST_FFF("require that appropriate features are dumped", ElementCompletenessBlueprint, IndexFixture, FeatureDumpFixture) {
    f1.visitDumpFeatures(f2.indexEnv, f3);
    EXPECT_EQUAL(f3.expect.size(), f3.dumped);
}

TEST_FF("require that setup can be done on index field", ElementCompletenessBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
}

TEST_FF("require that setup can not be done on attribute field", ElementCompletenessBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(bar)", f1.getBaseName().c_str()));
    EXPECT_TRUE(!((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "bar")));
}

TEST_FF("require that default config parameters are correct", ElementCompletenessBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
    EXPECT_EQUAL(0u,  f1.getParams().fieldId);
    EXPECT_EQUAL(0.5, f1.getParams().fieldCompletenessImportance);
}

TEST_FF("require that blueprint can be configured", ElementCompletenessBlueprint, IndexFixture) {
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(foo)", f1.getBaseName().c_str()));
    f2.indexEnv.getProperties().add("elementCompleteness(foo).fieldCompletenessImportance", "0.75");
    EXPECT_TRUE(((Blueprint&)f1).setup(f2.indexEnv, std::vector<vespalib::string>(1, "foo")));
    EXPECT_EQUAL(0.75, f1.getParams().fieldCompletenessImportance);
}

TEST_F("require that no match gives zero outputs", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("y"), 0.0, 0.0, 0));
}

TEST_F("require that perfect match gives max outputs", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("x"), 1.0, 1.0));
}

TEST_F("require that matching half the field gives appropriate outputs", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("x y"), 0.5, 1.0));
    TEST_DO(f.test("x y", indexFoo().element("x y a b"), 0.5, 1.0));
}

TEST_F("require that matching half the query gives appropriate outputs", RankFixture) {
    TEST_DO(f.test("x y", indexFoo().element("x"), 1.0, 0.5));
    TEST_DO(f.test("x y a b", indexFoo().element("x y"), 1.0, 0.5));
}

TEST_F("require that query completeness is affected by query term weight", RankFixture) {
    TEST_DO(f.test("x!300 y!100", indexFoo().element("y"), 1.0, 0.25));
    TEST_DO(f.test("x!300 y!100", indexFoo().element("x"), 1.0, 0.75));
}

TEST_F("require that field completeness is not affected by duplicate field tokens", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("x y y y"), 0.25, 1.00));
    TEST_DO(f.test("x", indexFoo().element("x x y y"), 0.25, 1.00));
    TEST_DO(f.test("x", indexFoo().element("x x x y"), 0.25, 1.00));
    TEST_DO(f.test("x", indexFoo().element("x x x x"), 0.25, 1.00));
}

TEST_F("require that field completeness is affected by duplicate query terms", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("x x x x"), 0.25, 1.00));
    TEST_DO(f.test("x x", indexFoo().element("x x x x"), 0.50, 1.00));
    TEST_DO(f.test("x x x", indexFoo().element("x x x x"), 0.75, 1.00));
    TEST_DO(f.test("x x x x", indexFoo().element("x x x x"), 1.00, 1.00));
}

TEST_F("require that a single field token can match multiple query terms", RankFixture) {
    TEST_DO(f.test("x", indexFoo().element("x"), 1.00, 1.00));
    TEST_DO(f.test("x x", indexFoo().element("x"), 1.00, 1.00));
    TEST_DO(f.test("x x x", indexFoo().element("x"), 1.00, 1.00));
    TEST_DO(f.test("x x x x", indexFoo().element("x"), 1.00, 1.00));
}

TEST_F("require that field completeness importance can be adjusted", RankFixture) {
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.1");
    TEST_DO(f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.1));
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.4");
    TEST_DO(f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.4));
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.7");
    TEST_DO(f.test("x y", indexFoo().element("x"), 1.0, 0.5, 1, 0.7));
}

TEST_F("require that order is not relevant", RankFixture) {
    TEST_DO(f.test("x y a b", indexFoo().element("n x n y"), 0.5, 0.5));
    TEST_DO(f.test("a b x y", indexFoo().element("y x n n"), 0.5, 0.5));
    TEST_DO(f.test("a y x b", indexFoo().element("x n y n"), 0.5, 0.5));
}

TEST_F("require that element is selected based on completeness times element weight", RankFixture) {
    f.idxProps.clear().add("elementCompleteness(foo).fieldCompletenessImportance", "0.0");
    TEST_DO(f.test("x y a b", indexFoo().element("x", 39).element("y", 39).element("a b", 19).element("x y a b", 10), 1.0, 1.0, 10, 0.0));
    TEST_DO(f.test("x y a b", indexFoo().element("x", 39).element("y", 39).element("a b", 21).element("x y a b", 10), 1.0, 0.5, 21, 0.0));
    TEST_DO(f.test("x y a b", indexFoo().element("x", 39).element("y", 45).element("a b", 21).element("x y a b", 10), 1.0, 0.25, 45, 0.0));
}

TEST_F("require that stale match data is ignored", RankFixture) {
    TEST_DO(f.test("x y a b", indexFoo().element("x y"), 0.0, 0.0, 0, 0.5, true));
}

TEST_MAIN() { TEST_RUN_ALL(); }
