// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/plugin/sum.h>
#include <vespa/searchlib/fef/rank_program.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;

size_t count_unique_features(const RankProgram &program) {
    std::set<const NumberOrObject *> seen;
    auto unboxed = program.get_all_features(true);
    for (size_t i = 0; i < unboxed.num_features(); ++i) {
        // fprintf(stderr, "seen feature (unboxed): %s\n", unboxed.name_of(i).c_str());
        seen.insert(unboxed.resolve_raw(i));
    }
    auto maybe_boxed = program.get_all_features(false);
    for (size_t i = 0; i < maybe_boxed.num_features(); ++i) {
        // fprintf(stderr, "seen feature (maybe boxed): %s\n", maybe_boxed.name_of(i).c_str());
        seen.insert(maybe_boxed.resolve_raw(i));
    }
    return seen.size();
}

struct ImpureValueExecutor : FeatureExecutor {
    double value;
    ImpureValueExecutor(double value_in) : value(value_in) {}
    bool isPure() override { return false; }
    void execute(uint32_t) override { outputs().set_number(0, value); }
};

struct ImpureValueBlueprint : Blueprint {
    double value;
    ImpureValueBlueprint() : Blueprint("ivalue"), value(31212.0) {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new ImpureValueBlueprint()); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override {
        ASSERT_EQUAL(1u, params.size());
        value = strtod(params[0].c_str(), nullptr);
        describeOutput("out", "the impure value");
        return true;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<ImpureValueExecutor>(value);
    }
};

struct MySetup {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;
    BlueprintResolver::SP resolver;
    Properties overrides;
    RankProgram program;
    MySetup() : factory(), indexEnv(), resolver(new BlueprintResolver(factory, indexEnv)),
                overrides(), program(resolver)
    {
        factory.addPrototype(Blueprint::SP(new ValueBlueprint()));
        factory.addPrototype(Blueprint::SP(new ImpureValueBlueprint()));
        factory.addPrototype(Blueprint::SP(new SumBlueprint()));
    }
    MySetup &add(const vespalib::string &feature) {
        resolver->addSeed(feature);
        return *this;
    }
    MySetup &override(const vespalib::string &feature, double value) {
        overrides.add(feature, vespalib::make_string("%g", value));
        return *this;
    }
    MySetup &compile() {
        ASSERT_TRUE(resolver->compile());
        MatchDataLayout mdl;
        QueryEnvironment queryEnv(&indexEnv);
        program.setup(mdl, queryEnv, overrides);
        return *this;
    }
    MySetup &run() {
        program.run(1);
        return *this;
    }
    double get() {
        auto result = program.get_seeds();
        EXPECT_EQUAL(1u, result.num_features());
        return *result.resolve_number(0);
    }
    double get(const vespalib::string &feature) {
        auto result = program.get_seeds();
        for (size_t i = 0; i < result.num_features(); ++i) {
            if (result.name_of(i) == feature) {
                return *result.resolve_number(i);
            }
        }
        return 31212.0;
    }
    std::map<vespalib::string, double> all() {
        auto result = program.get_seeds();
        std::map<vespalib::string, double> result_map;
        for (size_t i = 0; i < result.num_features(); ++i) {
            result_map[result.name_of(i)] = *result.resolve_number(i);
        }
        return result_map;
    }
};

TEST_F("require that match data docid is set by run", MySetup()) {
    f1.compile();
    EXPECT_NOT_EQUAL(1u, f1.program.match_data().getDocId());
    f1.run();
    EXPECT_EQUAL(1u, f1.program.match_data().getDocId());
}

TEST_F("require that simple program works", MySetup()) {
    EXPECT_EQUAL(15.0, f1.add("mysum(value(10),ivalue(5))").compile().run().get());
    EXPECT_EQUAL(3u, f1.program.num_executors());
    EXPECT_EQUAL(2u, f1.program.program_size());
}

TEST_F("require that const features are calculated during setup", MySetup()) {
    f1.add("mysum(value(10),value(5))").compile();
    EXPECT_EQUAL(15.0, f1.get());
    EXPECT_EQUAL(3u, f1.program.num_executors());
    EXPECT_EQUAL(0u, f1.program.program_size());
}

TEST_F("require that non-const features are calculated during run", MySetup()) {
    f1.add("mysum(ivalue(10),ivalue(5))").compile();
    EXPECT_EQUAL(0.0, f1.get());
    f1.run();
    EXPECT_EQUAL(15.0, f1.get());
    EXPECT_EQUAL(3u, f1.program.num_executors());
    EXPECT_EQUAL(3u, f1.program.program_size());
}

TEST_F("require that a single program can calculate multiple output features", MySetup()) {
    f1.add("value(1)").add("ivalue(2)").add("ivalue(3)");
    f1.add("mysum(value(1),value(2),ivalue(3))");
    f1.compile().run();
    EXPECT_EQUAL(5u, f1.program.num_executors());
    EXPECT_EQUAL(3u, f1.program.program_size());
    EXPECT_EQUAL(5u, count_unique_features(f1.program));
    auto result = f1.all();
    EXPECT_EQUAL(4u, result.size());
    EXPECT_EQUAL(1.0, result["value(1)"]);
    EXPECT_EQUAL(2.0, result["ivalue(2)"]);
    EXPECT_EQUAL(3.0, result["ivalue(3)"]);
    EXPECT_EQUAL(6.0, result["mysum(value(1),value(2),ivalue(3))"]);
}

TEST_F("require that a single executor can produce multiple features", MySetup()) {
    f1.add("mysum(value(1,2,3).0,value(1,2,3).1,value(1,2,3).2)");
    EXPECT_EQUAL(6.0, f1.compile().run().get());
    EXPECT_EQUAL(2u, f1.program.num_executors());
    EXPECT_EQUAL(0u, f1.program.program_size());
    EXPECT_EQUAL(4u, count_unique_features(f1.program));
}

TEST_F("require that feature values can be overridden", MySetup()) {
    f1.add("value(1)").add("ivalue(2)").add("ivalue(3)");
    f1.add("mysum(value(1),value(2),ivalue(3))");
    f1.override("value(2)", 20.0).override("ivalue(3)", 30.0);
    f1.compile().run();
    EXPECT_EQUAL(5u, f1.program.num_executors());
    EXPECT_EQUAL(3u, f1.program.program_size());
    EXPECT_EQUAL(5u, count_unique_features(f1.program));
    auto result = f1.all();
    EXPECT_EQUAL(4u, result.size());
    EXPECT_EQUAL(1.0, result["value(1)"]);
    EXPECT_EQUAL(2.0, result["ivalue(2)"]);
    EXPECT_EQUAL(30.0, result["ivalue(3)"]);
    EXPECT_EQUAL(51.0, result["mysum(value(1),value(2),ivalue(3))"]);    
}

TEST_MAIN() { TEST_RUN_ALL(); }
