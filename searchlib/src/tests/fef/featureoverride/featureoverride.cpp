// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/fef/fef.h>

#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/plugin/double.h>
#include <vespa/searchlib/fef/test/plugin/sum.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/fef/test/test_features.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/issue.h>

using namespace search::features;
using namespace search::fef::test;
using namespace search::fef;
using search::feature_t;
using vespalib::Issue;
using vespalib::eval::TensorSpec;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::make_string_short::fmt;


using BPSP = Blueprint::SP;

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
    fe = &stash.create<FeatureOverrider>(*fe, 1, 50.0, nullptr);
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
    fe = &stash.create<FeatureOverrider>(*fe, 0, 50.0, nullptr);
    fe = &stash.create<FeatureOverrider>(*fe, 2, 100.0, nullptr);
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
    fe = &stash.create<FeatureOverrider>(*fe, 1000, 50.0, nullptr);
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
    fe = &stash.create<FeatureOverrider>(*fe, 1, 50.0, nullptr);
    f.add(fe, 3);
    EXPECT_EQUAL(fe->outputs().size(), 3u);

    FeatureExecutor *fe2 = &stash.create<DoubleExecutor>(3);
    fe2 = &stash.create<FeatureOverrider>(*fe2, 2, 10.0, nullptr);
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

//-----------------------------------------------------------------------------

struct SimpleRankFixture {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;
    BlueprintResolver::SP resolver;
    Properties overrides;
    MatchData::UP match_data;
    RankProgram program;
    static vespalib::string expr_feature(const vespalib::string &name) {
        return fmt("rankingExpression(%s)", name.c_str());
    }
    SimpleRankFixture()
      : factory(), indexEnv(), resolver(new BlueprintResolver(factory, indexEnv)),
        overrides(), match_data(), program(resolver)
    {
        factory.addPrototype(std::make_shared<DocidBlueprint>());
        factory.addPrototype(std::make_shared<RankingExpressionBlueprint>());
    }
    ~SimpleRankFixture();
    void add_expr(const vespalib::string &name, const vespalib::string &expr) {
        vespalib::string feature_name = expr_feature(name);
        vespalib::string expr_name = feature_name + ".rankingScript";
        indexEnv.getProperties().add(expr_name, expr);
    }
    void add_override(const vespalib::string &name, const TensorSpec &spec) {
        vespalib::nbostream data;
        auto tensor = vespalib::eval::value_from_spec(spec, FastValueBuilderFactory::get());
        vespalib::eval::encode_value(*tensor, data);
        overrides.add(name, vespalib::stringref(data.peek(), data.size()));
    }
    void add_override(const vespalib::string &name, const vespalib::string &str) {
        overrides.add(name, str);
    }
    bool try_compile(const vespalib::string &seed) {
        resolver->addSeed(seed);
        if (!resolver->compile()) {
            return false;
        }
        MatchDataLayout mdl;
        QueryEnvironment queryEnv(&indexEnv);
        match_data = mdl.createMatchData();
        program.setup(*match_data, queryEnv, overrides);
        return true;
    }
    void compile(const vespalib::string &seed) {
        ASSERT_TRUE(try_compile(seed));
    }
    TensorSpec get(uint32_t docid) {
        auto result = program.get_seeds(false);
        ASSERT_EQUAL(1u, result.num_features());
        return TensorSpec::from_value(result.resolve(0).as_object(docid));
    }
};
SimpleRankFixture::~SimpleRankFixture() = default;

TensorSpec from_expr(const vespalib::string &expr) {
    auto result = TensorSpec::from_expr(expr);
    ASSERT_TRUE(result.type() != "error");
    return result;
}

struct MyIssues : Issue::Handler {
    std::vector<vespalib::string> list;
    Issue::Binding capture;
    MyIssues() : list(), capture(Issue::listen(*this)) {}
    ~MyIssues() override;
    void handle(const Issue &issue) override { list.push_back(issue.message()); }
};

MyIssues::~MyIssues() = default;

//-----------------------------------------------------------------------------

TEST_F("require expression without override works", SimpleRankFixture) {
    auto expect = from_expr("tensor<float>(x[3]):[1,2,3]");
    f1.add_expr("foo", "tensor<float>(x[3]):[1,2,3]");
    f1.compile(f1.expr_feature("foo"));
    EXPECT_EQUAL(f1.get(1), expect);
}

TEST_F("require that const binary override works", SimpleRankFixture) {
    auto expect = from_expr("tensor<float>(x[3]):[5,6,7]");
    f1.add_expr("foo", "tensor<float>(x[3]):[1,2,3]");
    f1.add_override(f1.expr_feature("foo"), expect);
    f1.compile(f1.expr_feature("foo"));
    EXPECT_EQUAL(f1.get(1), expect);
}

TEST_F("require that non-const binary override works", SimpleRankFixture) {
    auto expect = from_expr("tensor<float>(x[3]):[5,6,7]");
    f1.add_expr("foo", "tensor<float>(x[3]):[docid,2,3]");
    f1.add_override(f1.expr_feature("foo"), expect);
    f1.compile(f1.expr_feature("foo"));
    EXPECT_EQUAL(f1.get(1), expect);
}

TEST_F("require that wrong type binary override is ignored", SimpleRankFixture) {
    MyIssues issues;
    auto expect = from_expr("tensor<float>(x[3]):[1,2,3]");
    auto other = from_expr("tensor(x[3]):[5,6,7]");
    f1.add_expr("foo", "tensor<float>(x[3]):[1,2,3]");
    f1.add_override(f1.expr_feature("foo"), other);
    f1.compile(f1.expr_feature("foo"));
    ASSERT_EQUAL(issues.list.size(), 1u);
    EXPECT_LESS(issues.list[0].find("has invalid type"), issues.list[0].size());
    fprintf(stderr, "issue: %s\n", issues.list[0].c_str());
}

TEST_F("require that bad format binary override is ignored", SimpleRankFixture) {
    MyIssues issues;
    auto expect = from_expr("tensor<float>(x[3]):[1,2,3]");
    f1.add_expr("foo", "tensor<float>(x[3]):[1,2,3]");
    f1.add_override(f1.expr_feature("foo"), vespalib::string("bad format"));
    f1.compile(f1.expr_feature("foo"));
    ASSERT_EQUAL(issues.list.size(), 1u);
    EXPECT_LESS(issues.list[0].find("has invalid format"), issues.list[0].size());
    fprintf(stderr, "issue: %s\n", issues.list[0].c_str());
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
