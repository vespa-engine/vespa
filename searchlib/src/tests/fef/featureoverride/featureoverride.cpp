// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("featureoverride_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/fef.h>

#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/plugin/double.h>
#include <vespa/searchlib/fef/test/plugin/sum.h>
#include <vespa/searchlib/features/valuefeature.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using search::feature_t;

typedef Blueprint::SP       BPSP;

struct Fixture
{
    MatchDataLayout mdl;
    vespalib::Stash stash;
    std::vector<FeatureExecutor *> executors;
    MatchData::UP md;
    Fixture() : mdl(), stash(), executors(), md() {}
    Fixture &add(FeatureExecutor *executor, size_t outCnt) {
        executor->bind_outputs(stash.create_array<NumberOrObject>(outCnt));
        executors.push_back(executor);
        return *this;
    }
    Fixture &run() {
        md = mdl.createMatchData();
        for (const auto &executor : executors) {
            executor->bind_match_data(*md);
            executor->lazy_execute(1);
        }
        return *this;
    }
    FeatureExecutor &createValueExecutor() {
        std::vector<feature_t> values;
        values.push_back(1.0);
        values.push_back(2.0);
        values.push_back(3.0);
        return stash.create<ValueExecutor>(values);
    }
};

TEST_F("test decorator - single override", Fixture)
{
    FeatureExecutor *fe = &f.createValueExecutor();
    vespalib::Stash &stash = f.stash;
    fe = &stash.create<FeatureOverrider>(*fe, 1, 50.0);
    f.add(fe, 3).run();
    EXPECT_EQUAL(fe->outputs().size(), 3u);

    EXPECT_EQUAL(fe->outputs().get_number(0), 1.0);
    EXPECT_EQUAL(fe->outputs().get_number(1), 50.0);
    EXPECT_EQUAL(fe->outputs().get_number(2), 3.0);
}

TEST_F("test decorator - multiple overrides", Fixture)
{
    FeatureExecutor *fe = &f.createValueExecutor();
    vespalib::Stash &stash = f.stash;
    fe = &stash.create<FeatureOverrider>(*fe, 0, 50.0);
    fe = &stash.create<FeatureOverrider>(*fe, 2, 100.0);
    f.add(fe, 3).run();
    EXPECT_EQUAL(fe->outputs().size(), 3u);

    EXPECT_EQUAL(fe->outputs().get_number(0), 50.0);
    EXPECT_EQUAL(fe->outputs().get_number(1), 2.0);
    EXPECT_EQUAL(fe->outputs().get_number(2), 100.0);
}

TEST_F("test decorator - non-existing override", Fixture)
{
    FeatureExecutor *fe = &f.createValueExecutor();
    vespalib::Stash &stash = f.stash;
    fe = &stash.create<FeatureOverrider>(*fe, 1000, 50.0);
    f.add(fe, 3).run();
    EXPECT_EQUAL(fe->outputs().size(), 3u);

    EXPECT_EQUAL(fe->outputs().get_number(0), 1.0);
    EXPECT_EQUAL(fe->outputs().get_number(1), 2.0);
    EXPECT_EQUAL(fe->outputs().get_number(2), 3.0);
}

TEST_F("test decorator - transitive override", Fixture)
{
    FeatureExecutor *fe = &f.createValueExecutor();
    vespalib::Stash &stash = f.stash;
    fe = &stash.create<FeatureOverrider>(*fe, 1, 50.0);
    f.add(fe, 3);
    EXPECT_EQUAL(fe->outputs().size(), 3u);

    FeatureExecutor *fe2 = &stash.create<DoubleExecutor>(3);
    fe2 = &stash.create<FeatureOverrider>(*fe2, 2, 10.0);
    auto inputs = stash.create_array<LazyValue>(3, nullptr);
    inputs[0] = LazyValue(fe->outputs().get_raw(0), fe);
    inputs[1] = LazyValue(fe->outputs().get_raw(1), fe);
    inputs[2] = LazyValue(fe->outputs().get_raw(2), fe);
    fe2->bind_inputs(inputs);
    f.add(fe2, 3).run();
    EXPECT_EQUAL(fe2->outputs().size(), 3u);

    EXPECT_EQUAL(fe->outputs().get_number(0), 1.0);
    EXPECT_EQUAL(fe->outputs().get_number(1), 50.0);
    EXPECT_EQUAL(fe->outputs().get_number(2), 3.0);
    EXPECT_EQUAL(fe2->outputs().get_number(0), 2.0);
    EXPECT_EQUAL(fe2->outputs().get_number(1), 100.0);
    EXPECT_EQUAL(fe2->outputs().get_number(2), 10.0);
}

TEST("test overrides")
{
    BlueprintFactory bf;
    bf.addPrototype(BPSP(new ValueBlueprint()));
    bf.addPrototype(BPSP(new DoubleBlueprint()));
    bf.addPrototype(BPSP(new SumBlueprint()));

    IndexEnvironment idxEnv;
    RankSetup        rs(bf, idxEnv);

    rs.addDumpFeature("value(1,2,3)");
    rs.addDumpFeature("double(value(1))");
    rs.addDumpFeature("double(value(2))");
    rs.addDumpFeature("double(value(3))");
    rs.addDumpFeature("mysum(value(2),value(2))");
    rs.addDumpFeature("mysum(value(1),value(2),value(3))");
    EXPECT_TRUE(rs.compile());

    RankProgram::UP rankProgram = rs.create_dump_program();

    MatchDataLayout         mdl;
    QueryEnvironment        queryEnv;
    Properties              overrides;

    overrides.add("value(2)",       "20.0");
    overrides.add("value(1,2,3).1",  "4.0");
    overrides.add("value(1,2,3).2",  "6.0");
    overrides.add("bogus(feature)", "10.0");

    MatchData::UP match_data = mdl.createMatchData();
    rankProgram->setup(*match_data, queryEnv, overrides);

    std::map<vespalib::string, feature_t> res = Utils::getAllFeatures(*rankProgram, 2);

    EXPECT_EQUAL(res.size(), 20u);
    EXPECT_APPROX(res["value(1)"],                               1.0, 1e-6);
    EXPECT_APPROX(res["value(1).0"],                             1.0, 1e-6);
    EXPECT_APPROX(res["value(2)"],                              20.0, 1e-6);
    EXPECT_APPROX(res["value(2).0"],                            20.0, 1e-6);
    EXPECT_APPROX(res["value(3)"],                               3.0, 1e-6);
    EXPECT_APPROX(res["value(3).0"],                             3.0, 1e-6);
    EXPECT_APPROX(res["value(1,2,3)"],                           1.0, 1e-6);
    EXPECT_APPROX(res["value(1,2,3).0"],                         1.0, 1e-6);
    EXPECT_APPROX(res["value(1,2,3).1"],                         4.0, 1e-6);
    EXPECT_APPROX(res["value(1,2,3).2"],                         6.0, 1e-6);
    EXPECT_APPROX(res["mysum(value(2),value(2))"],              40.0, 1e-6);
    EXPECT_APPROX(res["mysum(value(2),value(2)).out"],          40.0, 1e-6);
    EXPECT_APPROX(res["mysum(value(1),value(2),value(3))"],     24.0, 1e-6);
    EXPECT_APPROX(res["mysum(value(1),value(2),value(3)).out"], 24.0, 1e-6);
    EXPECT_APPROX(res["double(value(1))"],                       2.0, 1e-6);
    EXPECT_APPROX(res["double(value(1)).0"],                     2.0, 1e-6);
    EXPECT_APPROX(res["double(value(2))"],                      40.0, 1e-6);
    EXPECT_APPROX(res["double(value(2)).0"],                    40.0, 1e-6);
    EXPECT_APPROX(res["double(value(3))"],                       6.0, 1e-6);
    EXPECT_APPROX(res["double(value(3)).0"],                     6.0, 1e-6);
}

TEST_MAIN() { TEST_RUN_ALL(); }
