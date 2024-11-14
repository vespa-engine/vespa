// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/feature.h>

#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/idumpfeaturevisitor.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/fef/utils.h>

#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/rankresult.h>

#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/features/second_phase_feature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/test/plugin/chain.h>
#include <vespa/searchlib/fef/test/plugin/double.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchlib/fef/test/plugin/staticrank.h>
#include <vespa/searchlib/fef/test/plugin/sum.h>
#include <vespa/searchlib/fef/test/plugin/cfgvalue.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <iostream>

using namespace search::fef;
using namespace search::features;
using namespace search::fef::test;
using search::feature_t;
using vespalib::make_string_short::fmt;

using FNB = FeatureNameBuilder;

//-----------------------------------------------------------------------------
// DumpFeatureVisitor
//-----------------------------------------------------------------------------
class DumpFeatureVisitor : public IDumpFeatureVisitor
{
public:
    DumpFeatureVisitor() {}
    virtual void visitDumpFeature(const std::string & name) override {
        std::cout << "dump feature: " << name << std::endl;
    }
};


//-----------------------------------------------------------------------------
// RankEnvironment
//-----------------------------------------------------------------------------
class RankEnvironment
{
private:
    const BlueprintFactory & _factory;
    const IIndexEnvironment & _indexEnv;
    const IQueryEnvironment & _queryEnv;

public:
    RankEnvironment(const BlueprintFactory & bfactory,
                    const IIndexEnvironment & indexEnv, const IQueryEnvironment & queryEnv) :
        _factory(bfactory), _indexEnv(indexEnv), _queryEnv(queryEnv) {}

    const BlueprintFactory & factory() const { return _factory; }
    const IIndexEnvironment & indexEnvironment() const { return _indexEnv; }
    const IQueryEnvironment & queryEnvironment() const { return _queryEnv; }
};


//-----------------------------------------------------------------------------
// RankExecutor
//-----------------------------------------------------------------------------
class RankExecutor
{
private:
    std::string _initRank;
    std::string _finalRank;
    const RankEnvironment & _rankEnv;
    MatchDataLayout _layout;
    std::unique_ptr<RankSetup> _rs;
    MatchData::UP _match_data;
    RankProgram::UP _firstPhaseProgram;
    RankProgram::UP _secondPhaseProgram;

public:
    RankExecutor(const std::string &initRank, const std::string &finalRank, const RankEnvironment &rankEnv);
    ~RankExecutor();
    bool setup();
    RankResult execute(uint32_t docId = 1);
};

RankExecutor::RankExecutor(const std::string &initRank, const std::string &finalRank,
                           const RankEnvironment &rankEnv)
    : _initRank(initRank), _finalRank(finalRank), _rankEnv(rankEnv), _layout(),
      _rs(), _match_data(), _firstPhaseProgram(), _secondPhaseProgram()
{}

RankExecutor::~RankExecutor() {}

bool
RankExecutor::setup()
{
    _rs = std::unique_ptr<RankSetup>(new RankSetup(_rankEnv.factory(), _rankEnv.indexEnvironment()));
    if (_initRank.empty()) {
        return false;
    }
    _rs->setFirstPhaseRank(_initRank);

    if (!_finalRank.empty()) {
        _rs->setSecondPhaseRank(_finalRank);
    }

    if (!_rs->compile()) {
        return false;
    }
    _match_data = _layout.createMatchData();

    _firstPhaseProgram = _rs->create_first_phase_program();
    _firstPhaseProgram->setup(*_match_data, _rankEnv.queryEnvironment());
    if (!_finalRank.empty()) {
        _secondPhaseProgram = _rs->create_second_phase_program();
        _secondPhaseProgram->setup(*_match_data, _rankEnv.queryEnvironment());
    }
    return true;
}

RankResult
RankExecutor::execute(uint32_t docId)
{
    RankResult result;
    result.addScore(_initRank, Utils::getScoreFeature(*_firstPhaseProgram, docId));

    if (_secondPhaseProgram.get() != nullptr) {
        result.addScore(_finalRank, Utils::getScoreFeature(*_secondPhaseProgram, docId));
    }

    return result;
}


//-----------------------------------------------------------------------------
// FeatureDumper
//-----------------------------------------------------------------------------
class FeatureDumper
{
private:
    const RankEnvironment & _rankEnv;
    RankSetup _setup;
    MatchDataLayout _layout;
    MatchData::UP _match_data;
    RankProgram::UP _rankProgram;

public:
    FeatureDumper(const RankEnvironment & rankEnv);
    ~FeatureDumper();
    void addDumpFeature(const std::string &name);
    void configure();
    bool setup();
    RankResult dump();
};

FeatureDumper::FeatureDumper(const RankEnvironment & rankEnv)
    : _rankEnv(rankEnv),
      _setup(_rankEnv.factory(), _rankEnv.indexEnvironment()),
      _layout(),
      _match_data(),
      _rankProgram()
{}
FeatureDumper::~FeatureDumper() {}
void
FeatureDumper::addDumpFeature(const std::string &name)
{
    _setup.addDumpFeature(name);
}

void
FeatureDumper::configure()
{
    _setup.configure();
}

bool
FeatureDumper::setup()
{
    if (!_setup.compile()) {
        return false;
    }

    _match_data = _layout.createMatchData();
    _rankProgram = _setup.create_dump_program();
    _rankProgram->setup(*_match_data, _rankEnv.queryEnvironment());
    return true;
}

RankResult
FeatureDumper::dump()
{
    std::map<std::string, feature_t> features = Utils::getSeedFeatures(*_rankProgram, 1);
    RankResult retval;
    for (auto itr = features.begin(); itr != features.end(); ++itr) {
        retval.addScore(itr->first, itr->second);
    }
    return retval;
}


//-----------------------------------------------------------------------------
// RankSetupTest
//-----------------------------------------------------------------------------
class RankSetupTest : public ::testing::Test
{
protected:
    BlueprintFactory _factory;
    search::AttributeManager _manager;
    IndexEnvironment _indexEnv;
    QueryEnvironment _queryEnv;
    RankEnvironment  _rankEnv;
    DumpFeatureVisitor _visitor;

    bool testExecution(const std::string & initRank, feature_t initScore,
                       const std::string & finalRank = "", feature_t finalScore = 0.0f, uint32_t docId = 1);
    bool testExecution(const RankEnvironment &rankEnv,
                       const std::string & initRank, feature_t initScore,
                       const std::string & finalRank = "", feature_t finalScore = 0.0f, uint32_t docId = 1);
    void testExecution();
    void testFeatureDump();

    void checkFeatures(std::map<std::string, feature_t> &exp, std::map<std::string, feature_t> &actual);
    void testFeatureNormalization();

    RankSetupTest();
    ~RankSetupTest() override;
};

RankSetupTest::RankSetupTest()
    : _factory(),
      _manager(),
      _indexEnv(),
      _queryEnv(),
      _rankEnv(_factory, _indexEnv, _queryEnv),
      _visitor()
{
    // register blueprints
    setup_fef_test_plugin(_factory);
    _factory.addPrototype(Blueprint::SP(new ValueBlueprint()));
    _factory.addPrototype(Blueprint::SP(new RankingExpressionBlueprint()));
    _factory.addPrototype(std::make_shared<SecondPhaseBlueprint>());

    // setup an original attribute manager with two attributes
    search::attribute::Config cfg(search::attribute::BasicType::INT32,
                                        search::attribute::CollectionType::SINGLE);
    search::AttributeVector::SP av1 =
        search::AttributeFactory::createAttribute("staticrank1", cfg);
    search::AttributeVector::SP av2 =
        search::AttributeFactory::createAttribute("staticrank2", cfg);
    av1->addDocs(5);
    av2->addDocs(5);
    for (uint32_t i = 0; i < 5; ++i) {
        (static_cast<search::IntegerAttribute *>(av1.get()))->update(i, i + 100);
        (static_cast<search::IntegerAttribute *>(av2.get()))->update(i, i + 200);
    }
    av1->commit();
    av2->commit();
    _manager.add(av1);
    _manager.add(av2);

    // set the index environment
    _queryEnv.setIndexEnv(&_indexEnv);

    // set the manager
    _queryEnv.overrideAttributeManager(&_manager);
}

RankSetupTest::~RankSetupTest() = default;

TEST_F(RankSetupTest, value_blueprint)
{
    ValueBlueprint prototype;
    prototype.visitDumpFeatures(_indexEnv, _visitor);
    { // basic test
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        bp->setName("value");
        EXPECT_EQ(bp->getName(), "value");
        std::vector<std::string> params;
        params.push_back("5.5");
        params.push_back("10.5");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 0u);
        EXPECT_EQ(deps.output.size(), 2u);
        EXPECT_EQ(deps.output[0], "0");
        EXPECT_EQ(deps.output[1], "1");

        vespalib::Stash stash;
        FeatureExecutor &fe = bp->createExecutor(_queryEnv, stash);
        ValueExecutor * vfe = static_cast<ValueExecutor *>(&fe);
        EXPECT_EQ(vfe->getValues().size(), 2u);
        EXPECT_EQ(vfe->getValues()[0], 5.5f);
        EXPECT_EQ(vfe->getValues()[1], 10.5f);
    }
    { // invalid params
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        EXPECT_TRUE(!bp->setup(_indexEnv, params));
    }
}

TEST_F(RankSetupTest, double_blueprint)
{
    DoubleBlueprint prototype;
    prototype.visitDumpFeatures(_indexEnv, _visitor);
    { // basic test
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("value(5.5).0");
        params.push_back("value(10.5).0");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 2u);
        EXPECT_EQ(deps.input[0], "value(5.5).0");
        EXPECT_EQ(deps.input[1], "value(10.5).0");
        EXPECT_EQ(deps.output.size(), 2u);
        EXPECT_EQ(deps.output[0], "0");
        EXPECT_EQ(deps.output[1], "1");
   }
}

TEST_F(RankSetupTest, sum_blueprint)
{
    SumBlueprint prototype;
    prototype.visitDumpFeatures(_indexEnv, _visitor);
    { // basic test
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("value(5.5, 10.5).0");
        params.push_back("value(5.5, 10.5).1");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 2u);
        EXPECT_EQ(deps.input[0], "value(5.5, 10.5).0");
        EXPECT_EQ(deps.input[1], "value(5.5, 10.5).1");
        EXPECT_EQ(deps.output.size(), 1u);
        EXPECT_EQ(deps.output[0], "out");
    }
}

TEST_F(RankSetupTest, static_rank_blueprint)
{
    StaticRankBlueprint prototype;
    { // basic test
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("sr1");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 0u);
        EXPECT_EQ(deps.output.size(), 1u);
        EXPECT_EQ(deps.output[0], "out");
    }
    { // invalid params
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        EXPECT_TRUE(!bp->setup(_indexEnv, params));
        params.push_back("sr1");
        params.push_back("sr2");
        EXPECT_TRUE(!bp->setup(_indexEnv, params));
    }
}

TEST_F(RankSetupTest, chain_blueprint)
{
    ChainBlueprint prototype;
    { // chaining
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("basic");
        params.push_back("2");
        params.push_back("4");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 1u);
        EXPECT_EQ(deps.input[0], "chain(basic,1,4)");
    }
    { // leaf node
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("basic");
        params.push_back("1");
        params.push_back("4");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 1u);
        EXPECT_EQ(deps.input[0], "value(4)");
    }
    { // cycle
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        params.push_back("cycle");
        params.push_back("1");
        params.push_back("4");
        EXPECT_TRUE(bp->setup(_indexEnv, params));
        EXPECT_EQ(deps.input.size(), 1u);
        EXPECT_EQ(deps.input[0], "chain(cycle,4,4)");
    }
    { // invalid params
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        std::vector<std::string> params;
        EXPECT_TRUE(!bp->setup(_indexEnv, params));
        params.push_back("basic");
        params.push_back("0");
        params.push_back("4");
        EXPECT_TRUE(!bp->setup(_indexEnv, params));
    }
}

TEST_F(RankSetupTest, cfg_value_blueprint)
{
    CfgValueBlueprint     prototype;
    IndexEnvironment      indexEnv;
    indexEnv.getProperties().add("test_cfgvalue(foo).value", "1.0");
    indexEnv.getProperties().add("test_cfgvalue(foo).value", "2.0");
    indexEnv.getProperties().add("test_cfgvalue(foo).value", "3.0");

    { // basic test
        Blueprint::UP bp = prototype.createInstance();
        DummyDependencyHandler deps(*bp);
        bp->setName("test_cfgvalue(foo)");
        std::vector<std::string> params;
        params.push_back("foo");

        EXPECT_TRUE(bp->setup(indexEnv, params));
        EXPECT_EQ(deps.input.size(), 0u);
        EXPECT_EQ(deps.output.size(), 3u);
        EXPECT_EQ(deps.output[0], "0");
        EXPECT_EQ(deps.output[1], "1");
        EXPECT_EQ(deps.output[2], "2");

        vespalib::Stash stash;
        FeatureExecutor &fe = bp->createExecutor(_queryEnv, stash);
        ValueExecutor *vfe = static_cast<ValueExecutor *>(&fe);
        EXPECT_EQ(vfe->getValues().size(), 3u);
        EXPECT_EQ(vfe->getValues()[0], 1.0f);
        EXPECT_EQ(vfe->getValues()[1], 2.0f);
        EXPECT_EQ(vfe->getValues()[2], 3.0f);
    }
}

TEST_F(RankSetupTest, compilation)
{
    { // unknown blueprint
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank("unknown");
        EXPECT_TRUE(!rs.compile());
    }
    { // unknown output for initial rank
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank("value(2).1");
        EXPECT_TRUE(!rs.compile());
    }
    { // unknown output for dependency
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank(FNB().baseName("mysum").parameter("value(2).1").buildName());
        EXPECT_TRUE(!rs.compile());
    }
    { // illegal input parameters
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank("value.0");
        EXPECT_TRUE(!rs.compile());
    }
    { // illegal feature name
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank("value(2).");
        EXPECT_TRUE(!rs.compile());
    }
    { // almost too deep dependency graph
        RankSetup rs(_factory, _indexEnv);
        std::ostringstream oss;
        oss << "chain(basic," << (BlueprintResolver::MAX_DEP_DEPTH - 1) << ",4)"; // gives tree height == MAX_DEP_DEPTH
        rs.setFirstPhaseRank(oss.str());
        EXPECT_TRUE(rs.compile());
    }
    { // too deep dependency graph
        RankSetup rs(_factory, _indexEnv);
        std::ostringstream oss;
        oss << "chain(basic," << BlueprintResolver::MAX_DEP_DEPTH << ",4)"; // gives tree height == MAX_DEP_DEPTH + 1
        rs.setFirstPhaseRank(oss.str());
        EXPECT_TRUE(!rs.compile());
    }
    { // short cycle
        RankSetup rs(_factory, _indexEnv);
        // c(c,4,2) -> c(c,3,2) -> c(c,2,2) -> c(c,1,2) -> c(c,2,2)
        rs.setFirstPhaseRank("chain(cycle,4,2)");
        EXPECT_TRUE(!rs.compile());
    }
    { // cycle with max back-trace
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank(fmt("chain(cycle,%d,2)", BlueprintResolver::MAX_TRACE_SIZE));
        EXPECT_TRUE(!rs.compile());
    }
    { // cycle with max+1 back-trace (skip 2)
        RankSetup rs(_factory, _indexEnv);
        rs.setFirstPhaseRank(fmt("chain(cycle,%d,2)", BlueprintResolver::MAX_TRACE_SIZE + 1));
        EXPECT_TRUE(!rs.compile());
    }
}

TEST_F(RankSetupTest, rank_setup)
{
    using namespace search::fef::indexproperties;
    IndexEnvironment env;
    env.getProperties().add(rank::FirstPhase::NAME, "firstphase");
    env.getProperties().add(rank::SecondPhase::NAME, "secondphase");
    env.getProperties().add(match::Feature::NAME, "match_foo");
    env.getProperties().add(match::Feature::NAME, "match_bar");
    env.getProperties().add(dump::Feature::NAME, "foo");
    env.getProperties().add(dump::Feature::NAME, "bar");
    env.getProperties().add(matching::NumThreadsPerSearch::NAME, "3");
    env.getProperties().add(matching::MinHitsPerThread::NAME, "8");
    env.getProperties().add(matchphase::DegradationAttribute::NAME, "mystaticrankattr");
    env.getProperties().add(matchphase::DegradationAscendingOrder::NAME, "true");
    env.getProperties().add(matchphase::DegradationMaxHits::NAME, "12345");
    env.getProperties().add(matchphase::DegradationMaxFilterCoverage::NAME, "0.19");
    env.getProperties().add(matchphase::DegradationSamplePercentage::NAME, "0.9");
    env.getProperties().add(matchphase::DegradationPostFilterMultiplier::NAME, "0.7");
    env.getProperties().add(matchphase::DiversityAttribute::NAME, "mycategoryattr");
    env.getProperties().add(matchphase::DiversityMinGroups::NAME, "37");
    env.getProperties().add(matchphase::DiversityCutoffFactor::NAME, "7.1");
    env.getProperties().add(matchphase::DiversityCutoffStrategy::NAME, "strict");
    env.getProperties().add(hitcollector::HeapSize::NAME, "50");
    env.getProperties().add(hitcollector::ArraySize::NAME, "60");
    env.getProperties().add(hitcollector::EstimatePoint::NAME, "70");
    env.getProperties().add(hitcollector::EstimateLimit::NAME, "80");
    env.getProperties().add(hitcollector::FirstPhaseRankScoreDropLimit::NAME, "90.5");
    env.getProperties().add(hitcollector::SecondPhaseRankScoreDropLimit::NAME, "91.5");
    env.getProperties().add(mutate::on_match::Attribute::NAME, "a");
    env.getProperties().add(mutate::on_match::Operation::NAME, "+=3");
    env.getProperties().add(mutate::on_first_phase::Attribute::NAME, "b");
    env.getProperties().add(mutate::on_first_phase::Operation::NAME, "=3");
    env.getProperties().add(mutate::on_second_phase::Attribute::NAME, "b");
    env.getProperties().add(mutate::on_second_phase::Operation::NAME, "=7");
    env.getProperties().add(mutate::on_summary::Attribute::NAME, "c");
    env.getProperties().add(mutate::on_summary::Operation::NAME, "-=2");
    env.getProperties().add(matching::GlobalFilterLowerLimit::NAME, "0.3");
    env.getProperties().add(matching::GlobalFilterUpperLimit::NAME, "0.7");
    env.getProperties().add(matching::TargetHitsMaxAdjustmentFactor::NAME, "5.0");
    env.getProperties().add(matching::FuzzyAlgorithm::NAME, "dfa_implicit");
    env.getProperties().add(matching::WeakAndStopWordAdjustLimit::NAME, "0.05");
    env.getProperties().add(matching::WeakAndStopWordDropLimit::NAME, "0.5");

    RankSetup rs(_factory, env);
    EXPECT_FALSE(rs.has_match_features());
    rs.configure();
    EXPECT_EQ(rs.getFirstPhaseRank(), std::string("firstphase"));
    EXPECT_EQ(rs.getSecondPhaseRank(), std::string("secondphase"));
    EXPECT_TRUE(rs.has_match_features());
    ASSERT_TRUE(rs.get_match_features().size() == 2);
    EXPECT_EQ(rs.get_match_features()[0], std::string("match_foo"));
    EXPECT_EQ(rs.get_match_features()[1], std::string("match_bar"));
    ASSERT_TRUE(rs.getDumpFeatures().size() == 2);
    EXPECT_EQ(rs.getDumpFeatures()[0], std::string("foo"));
    EXPECT_EQ(rs.getDumpFeatures()[1], std::string("bar"));
    EXPECT_EQ(rs.getNumThreadsPerSearch(), 3u);
    EXPECT_EQ(rs.getMinHitsPerThread(), 8u);
    EXPECT_EQ(rs.getDegradationAttribute(), "mystaticrankattr");
    EXPECT_EQ(rs.isDegradationOrderAscending(), true);
    EXPECT_EQ(rs.getDegradationMaxHits(), 12345u);
    EXPECT_EQ(rs.getDegradationSamplePercentage(), 0.9);
    EXPECT_EQ(rs.getDegradationMaxFilterCoverage(), 0.19);
    EXPECT_EQ(rs.getDegradationPostFilterMultiplier(), 0.7);
    EXPECT_EQ(rs.getDiversityAttribute(), "mycategoryattr");
    EXPECT_EQ(rs.getDiversityMinGroups(), 37u);
    EXPECT_EQ(rs.getDiversityCutoffFactor(), 7.1);
    EXPECT_EQ(rs.getDiversityCutoffStrategy(), "strict");
    EXPECT_EQ(rs.getHeapSize(), 50u);
    EXPECT_EQ(rs.getArraySize(), 60u);
    EXPECT_EQ(rs.getEstimatePoint(), 70u);
    EXPECT_EQ(rs.getEstimateLimit(), 80u);
    EXPECT_EQ(std::optional<feature_t>(90.5), rs.get_first_phase_rank_score_drop_limit());
    EXPECT_EQ(std::optional<feature_t>(91.5), rs.get_second_phase_rank_score_drop_limit());
    EXPECT_EQ(rs.getMutateOnMatch()._attribute, "a");
    EXPECT_EQ(rs.getMutateOnMatch()._operation, "+=3");
    EXPECT_EQ(rs.getMutateOnFirstPhase()._attribute, "b");
    EXPECT_EQ(rs.getMutateOnFirstPhase()._operation, "=3");
    EXPECT_EQ(rs.getMutateOnSecondPhase()._attribute, "b");
    EXPECT_EQ(rs.getMutateOnSecondPhase()._operation, "=7");
    EXPECT_EQ(rs.getMutateOnSummary()._attribute, "c");
    EXPECT_EQ(rs.getMutateOnSummary()._operation, "-=2");
    EXPECT_EQ(rs.get_global_filter_lower_limit(), 0.3);
    EXPECT_EQ(rs.get_global_filter_upper_limit(), 0.7);
    EXPECT_EQ(rs.get_target_hits_max_adjustment_factor(), 5.0);
    EXPECT_EQ(rs.get_fuzzy_matching_algorithm(), vespalib::FuzzyMatchingAlgorithm::DfaImplicit);
    EXPECT_EQ(rs.get_weakand_stop_word_adjust_limit(), 0.05);
    EXPECT_EQ(rs.get_weakand_stop_word_drop_limit(), 0.5);
}

bool
RankSetupTest::testExecution(const std::string & initRank, feature_t initScore,
                             const std::string & finalRank, feature_t finalScore, uint32_t docId)
{
    return testExecution(_rankEnv, initRank, initScore, finalRank, finalScore, docId);
}

bool
RankSetupTest::testExecution(const RankEnvironment &rankEnv, const std::string & initRank, feature_t initScore,
                             const std::string & finalRank, feature_t finalScore, uint32_t docId)
{
    bool ok = true;
    RankExecutor re(initRank, finalRank, rankEnv);
    ok = ok && re.setup();
    EXPECT_TRUE(ok);
    RankResult exp;
    exp.addScore(initRank, initScore);
    if (finalRank != "") {
        exp.addScore(finalRank, finalScore);
    }
    RankResult rs = re.execute(docId);
    ok = ok && (exp == rs);
    EXPECT_EQ(exp, rs);
    return ok;
}

TEST_F(RankSetupTest, execution)
{
    { // value executor
        std::string v = FNB().baseName("value").parameter("5.5").parameter("10.5").buildName();
        EXPECT_TRUE(testExecution(v + ".0", 5.5f));
        EXPECT_TRUE(testExecution(v + ".0", 5.5f, v + ".1", 10.5f));
        EXPECT_TRUE(testExecution(v, 5.5f));
    }
    { // double executor
        std::string d1 = FNB().baseName("double").parameter("value(2).0").parameter("value(8).0").buildName();
        std::string d2 = FNB().baseName("double").parameter("value(2)").parameter("value(8)").buildName();
        EXPECT_TRUE(testExecution(d1 + ".0", 4.0f));
        EXPECT_TRUE(testExecution(d1 + ".0", 4.0f, d1 + ".1", 16.0f));
        EXPECT_TRUE(testExecution(d2, 4.0f));
    }
    { // sum executor
        std::string s1 = FNB().baseName("mysum").parameter("value(2).0").parameter("value(4).0").output("out").buildName();
        std::string s2 = FNB().baseName("mysum").parameter("value(2)").parameter("value(4)").buildName();
        EXPECT_TRUE(testExecution(s1, 6.0f));
        EXPECT_TRUE(testExecution(s2, 6.0f));
    }
    { // static rank executor
        std::string sr1 = "staticrank(staticrank1)";
        std::string sr2 = "staticrank(staticrank2)";
        for (uint32_t i = 1; i < 5; ++i) {
            EXPECT_TRUE(testExecution(sr1, static_cast<feature_t>(i + 100),
                                     sr2, static_cast<feature_t>(i + 200), i));
        }
    }
    { // test topologic sorting
        std::string v1 = "value(2)";
        std::string d1 = FNB().baseName("double").parameter(v1).buildName();
        std::string d2 = FNB().baseName("double").parameter(d1).buildName();

        {
            std::string s1 = FNB().baseName("mysum").parameter(v1).parameter(d1).parameter(d2).buildName();
            EXPECT_TRUE(testExecution(s1, 14.0f));
        }
        {
            std::string s1 = FNB().baseName("mysum").parameter(d2).parameter(d1).parameter(v1).buildName();
            EXPECT_TRUE(testExecution(s1, 14.0f));
        }
    }
    { // output used by more than one
        std::string v1 = "value(2)";
        std::string d1 = FNB().baseName("double").parameter(v1).buildName();
        std::string d2 = FNB().baseName("double").parameter(v1).buildName();
        std::string s1 = FNB().baseName("mysum").parameter(d1).parameter(d2).buildName();
        EXPECT_TRUE(testExecution(s1, 8.0f));
    }
    { // output not shared between phases
        std::string v1 = "value(2)";
        std::string v2 = "value(8)";
        std::string d1 = FNB().baseName("double").parameter(v1).buildName();
        std::string d2 = FNB().baseName("double").parameter(v2).buildName();
        EXPECT_TRUE(testExecution(d1, 4.0f, d2, 16.0f));
    }
    { // output shared between phases
        std::string v1 = "value(2)";
        std::string v2 = "value(8)";
        std::string v3 = "value(32)";
        std::string d1 = FNB().baseName("double").parameter(v1).buildName();
        std::string d2 = FNB().baseName("double").parameter(v2).buildName();
        std::string d3 = FNB().baseName("double").parameter(v3).buildName();
        std::string s1 = FNB().baseName("mysum").parameter(d1).parameter(d2).buildName();
        std::string s2 = FNB().baseName("mysum").parameter(d2).parameter(d3).buildName();
        EXPECT_TRUE(testExecution(s1, 20.0f, s2, 80.0f));
    }
    { // max dependency depth
        uint32_t maxDepth = BlueprintResolver::MAX_DEP_DEPTH;
        std::ostringstream oss;
        oss << "chain(basic," << (maxDepth - 1) << ",4)"; // gives tree height == MAX_DEP_DEPTH;
        EXPECT_TRUE(testExecution(oss.str(), 4.0f));
    }
    {
        IndexEnvironment indexEnv;
        indexEnv.getProperties().add("test_cfgvalue(foo).value", "1.0");
        indexEnv.getProperties().add("test_cfgvalue(foo).value", "2.0");
        indexEnv.getProperties().add("test_cfgvalue(bar).value", "5.0");

        std::string s = FNB().baseName("mysum")
                        .parameter("test_cfgvalue(foo).0")
                        .parameter("test_cfgvalue(foo).1")
                        .buildName();

        EXPECT_TRUE(testExecution(RankEnvironment(_factory, indexEnv, _queryEnv),
                                 s, 3.0f, "test_cfgvalue(bar).0", 5.0f));
    }
}

TEST_F(RankSetupTest, feature_dump)
{
    {
        FeatureDumper dumper(_rankEnv);
        dumper.addDumpFeature("value(2)");
        dumper.addDumpFeature("value(4)");
        dumper.addDumpFeature("double(value(4))");
        dumper.addDumpFeature("double(value(8))");
        dumper.addDumpFeature("mysum(value(4),value(16))");
        dumper.addDumpFeature("mysum(double(value(8)),double(value(32)))");
        EXPECT_TRUE(dumper.setup());

        RankResult exp;
        exp.addScore("value(2)", 2.0f);
        exp.addScore("value(4)", 4.0f);
        exp.addScore(FNB().baseName("double").parameter("value(4)").buildName(), 8.0f);
        exp.addScore(FNB().baseName("double").parameter("value(8)").buildName(), 16.0f);
        exp.addScore(FNB().baseName("mysum").parameter("value(4)").parameter("value(16)").buildName(), 20.0f);
        exp.addScore(FNB().baseName("mysum").
                     parameter(FNB().baseName("double").parameter("value(8)").buildName()).
                     parameter(FNB().baseName("double").parameter("value(32)").buildName()).
                     buildName(), 80.0f);
        EXPECT_EQ(exp, dumper.dump());
    }
    {
        FeatureDumper dumper(_rankEnv);
        dumper.addDumpFeature("value(50)");
        dumper.addDumpFeature("value(100)");
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore("value(50)", 50.0f);
        exp.addScore("value(100)", 100.0f);
        EXPECT_EQ(exp, dumper.dump());
    }
    {
        FeatureDumper dumper(_rankEnv);
        dumper.addDumpFeature(FNB().baseName("rankingExpression").parameter("if(4<2,3,4)").buildName());
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore(FNB().baseName("rankingExpression").parameter("if(4<2,3,4)").buildName(), 4.0f);
        EXPECT_EQ(exp, dumper.dump());
    }

    {
        FeatureDumper dumper(_rankEnv);
        dumper.addDumpFeature(FNB().baseName("rankingExpression").parameter("if(mysum(value(12),value(10))>2,3,4)").buildName());
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore(FNB().baseName("rankingExpression").parameter("if(mysum(value(12),value(10))>2,3,4)").buildName(), 3.0f);
        EXPECT_EQ(exp, dumper.dump());
    }
    { // dump features indicated by visitation
        IndexEnvironment indexEnv;
        indexEnv.getProperties().add("test_cfgvalue(foo).value", "1.0");
        indexEnv.getProperties().add("test_cfgvalue(bar).value", "5.0");
        indexEnv.getProperties().add("test_cfgvalue.dump", "test_cfgvalue(foo)");
        indexEnv.getProperties().add("test_cfgvalue.dump", "test_cfgvalue(bar)");
        indexEnv.getProperties().add(indexproperties::rank::FirstPhase::NAME, "");
        indexEnv.getProperties().add(indexproperties::rank::SecondPhase::NAME, "");

        RankEnvironment rankEnv(_factory, indexEnv, _queryEnv);
        FeatureDumper dumper(rankEnv);
        dumper.configure();
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore("test_cfgvalue(foo)", 1.0);
        exp.addScore("test_cfgvalue(bar)", 5.0);
        EXPECT_EQ(exp, dumper.dump());
    }
    { // ignore features indicated by visitation
        IndexEnvironment indexEnv;
        indexEnv.getProperties().add("test_cfgvalue(foo).value", "1.0");
        indexEnv.getProperties().add("test_cfgvalue(bar).value", "5.0");
        indexEnv.getProperties().add("test_cfgvalue.dump", "test_cfgvalue(foo)");
        indexEnv.getProperties().add("test_cfgvalue.dump", "test_cfgvalue(bar)");
        indexEnv.getProperties().add(indexproperties::dump::IgnoreDefaultFeatures::NAME, "true");
        indexEnv.getProperties().add(indexproperties::dump::Feature::NAME, "test_cfgvalue(foo)");
        indexEnv.getProperties().add(indexproperties::rank::FirstPhase::NAME, "");
        indexEnv.getProperties().add(indexproperties::rank::SecondPhase::NAME, "");

        RankEnvironment rankEnv(_factory, indexEnv, _queryEnv);
        FeatureDumper dumper(rankEnv);
        dumper.configure();
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore("test_cfgvalue(foo)", 1.0);
        EXPECT_EQ(exp, dumper.dump());
    }
    { // Dump secondPhase feature
        IndexEnvironment indexEnv;
        indexEnv.getProperties().add(indexproperties::rank::FirstPhase::NAME, "value(2)");
        indexEnv.getProperties().add(indexproperties::rank::SecondPhase::NAME, "value(4)");
        RankEnvironment rankEnv(_factory, indexEnv, _queryEnv);
        FeatureDumper dumper(rankEnv);
        dumper.configure();
        dumper.addDumpFeature("secondPhase");
        EXPECT_TRUE(dumper.setup());
        RankResult exp;
        exp.addScore("secondPhase", 4.0);
        EXPECT_EQ(exp, dumper.dump());
    }
}

void
RankSetupTest::checkFeatures(std::map<std::string, feature_t> &exp, std::map<std::string, feature_t> &actual)
{
    using ITR = std::map<std::string, feature_t>::const_iterator;
    ASSERT_EQ(exp.size(), actual.size());
    ITR exp_itr    = exp.begin();
    ITR exp_end    = exp.end();
    ITR actual_itr = actual.begin();
    ITR actual_end = actual.end();
    for (; exp_itr != exp_end && actual_itr != actual_end; ++exp_itr, ++actual_itr) {
        EXPECT_EQ(exp_itr->first, actual_itr->first);
        EXPECT_NEAR(exp_itr->second, actual_itr->second, 0.001);
    }
    EXPECT_EQ(exp_itr == exp_end, actual_itr == actual_end);
}

TEST_F(RankSetupTest, feature_normalization)
{
    BlueprintFactory factory;
    factory.addPrototype(Blueprint::SP(new ValueBlueprint()));
    factory.addPrototype(Blueprint::SP(new SumBlueprint()));

    IndexEnvironment idxEnv;
    RankSetup rankSetup(factory, idxEnv);

    rankSetup.setFirstPhaseRank(" mysum ( value ( 1 ) , value ( 1 ) ) ");
    rankSetup.setSecondPhaseRank(" mysum ( value ( 2 ) , value ( 2 ) ) ");
    rankSetup.add_match_feature(" mysum ( value ( 3 ) , value ( 3 ) ) ");
    rankSetup.add_match_feature(" mysum ( \"value( 3 )\" , \"value( 3 )\" ) ");
    rankSetup.addSummaryFeature(" mysum ( value ( 5 ) , value ( 5 ) ) ");
    rankSetup.addSummaryFeature(" mysum ( \"value( 5 )\" , \"value( 5 )\" ) ");
    rankSetup.addDumpFeature(" mysum ( value ( 10 ) , value ( 10 ) ) ");
    rankSetup.addDumpFeature(" mysum ( \"value( 10 )\" , \"value( 10 )\" ) ");

    ASSERT_TRUE(rankSetup.compile());

    { // RANK context
        MatchDataLayout layout;
        QueryEnvironment queryEnv;
        MatchData::UP match_data = layout.createMatchData();
        RankProgram::UP firstPhaseProgram = rankSetup.create_first_phase_program();
        RankProgram::UP secondPhaseProgram = rankSetup.create_second_phase_program();
        RankProgram::UP match_program = rankSetup.create_match_program();
        RankProgram::UP summaryProgram = rankSetup.create_summary_program();
        firstPhaseProgram->setup(*match_data, queryEnv);
        secondPhaseProgram->setup(*match_data, queryEnv);
        match_program->setup(*match_data, queryEnv);
        summaryProgram->setup(*match_data, queryEnv);

        EXPECT_NEAR(2.0, Utils::getScoreFeature(*firstPhaseProgram, 1), 0.001);
        EXPECT_NEAR(4.0, Utils::getScoreFeature(*secondPhaseProgram, 1), 0.001);

        {
            SCOPED_TRACE("rank seed features");
            std::map<std::string, feature_t> actual = Utils::getSeedFeatures(*summaryProgram, 1);
            std::map<std::string, feature_t> exp;
            exp["mysum(value(5),value(5))"] = 10.0;
            exp["mysum(\"value( 5 )\",\"value( 5 )\")"] = 10.0;
            checkFeatures(exp, actual);
        }
        {
            SCOPED_TRACE("all rank features (1. phase)");
            std::map<std::string, feature_t> actual = Utils::getAllFeatures(*firstPhaseProgram, 1);
            std::map<std::string, feature_t> exp;
            exp["value(1)"] = 1.0;
            exp["value(1).0"] = 1.0;
            exp["mysum(value(1),value(1))"] = 2.0;
            exp["mysum(value(1),value(1)).out"] = 2.0;
            checkFeatures(exp, actual);
        }
        {
            SCOPED_TRACE("all rank features (2. phase)");
            std::map<std::string, feature_t> actual = Utils::getAllFeatures(*secondPhaseProgram, 1);
            std::map<std::string, feature_t> exp;
            exp["value(2)"] = 2.0;
            exp["value(2).0"] = 2.0;
            exp["mysum(value(2),value(2))"] = 4.0;
            exp["mysum(value(2),value(2)).out"] = 4.0;
            checkFeatures(exp, actual);
        }
        {
            SCOPED_TRACE("all match features");
            std::map<std::string, feature_t> actual = Utils::getAllFeatures(*match_program, 1);
            std::map<std::string, feature_t> exp;
            exp["value(3)"] = 3.0;
            exp["value(3).0"] = 3.0;
            exp["mysum(value(3),value(3))"] = 6.0;
            exp["mysum(value(3),value(3)).out"] = 6.0;
            exp["mysum(\"value( 3 )\",\"value( 3 )\")"] = 6.0;
            exp["mysum(\"value( 3 )\",\"value( 3 )\").out"] = 6.0;
            checkFeatures(exp, actual);
        }
        {
            SCOPED_TRACE("all rank features (summary)");
            std::map<std::string, feature_t> actual = Utils::getAllFeatures(*summaryProgram, 1);
            std::map<std::string, feature_t> exp;
            exp["value(5)"] = 5.0;
            exp["value(5).0"] = 5.0;
            exp["mysum(value(5),value(5))"] = 10.0;
            exp["mysum(value(5),value(5)).out"] = 10.0;
            exp["mysum(\"value( 5 )\",\"value( 5 )\")"] = 10.0;
            exp["mysum(\"value( 5 )\",\"value( 5 )\").out"] = 10.0;
            checkFeatures(exp, actual);
        }
    }

    { // DUMP context
        MatchDataLayout layout;
        QueryEnvironment queryEnv;
        MatchData::UP match_data = layout.createMatchData();
        RankProgram::UP rankProgram = rankSetup.create_dump_program();
        rankProgram->setup(*match_data, queryEnv);

        {
            SCOPED_TRACE("dump seed features");
            std::map<std::string, feature_t> actual = Utils::getSeedFeatures(*rankProgram, 1);
            std::map<std::string, feature_t> exp;
            exp["mysum(value(10),value(10))"] = 20.0;
            exp["mysum(\"value( 10 )\",\"value( 10 )\")"] = 20.0;
            checkFeatures(exp, actual);
        }

        {
            SCOPED_TRACE("all dump features");
            std::map<std::string, feature_t> actual = Utils::getAllFeatures(*rankProgram, 1);
            std::map<std::string, feature_t> exp;

            exp["value(10)"] = 10.0;
            exp["value(10).0"] = 10.0;

            exp["mysum(value(10),value(10))"] = 20.0;
            exp["mysum(value(10),value(10)).out"] = 20.0;

            exp["mysum(\"value( 10 )\",\"value( 10 )\")"] = 20.0;
            exp["mysum(\"value( 10 )\",\"value( 10 )\").out"] = 20.0;

            checkFeatures(exp, actual);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
