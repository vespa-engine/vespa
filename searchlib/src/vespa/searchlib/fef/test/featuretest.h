// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexenvironment.h"
#include "indexenvironmentbuilder.h"
#include "matchdatabuilder.h"
#include "queryenvironment.h"
#include "queryenvironmentbuilder.h"
#include "rankresult.h"
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/blueprintresolver.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/eval/eval/value.h>

namespace search::fef::test {

/**
 * This class wraps everything necessary to simulate a feature execution environment.
 */
class FeatureTest {
public:
    /**
     * Constructs a new feature test.
     *
     * @param factory   The blueprint factory that holds all registered features.
     * @param indexEnv  The index environment to use.
     * @param queryEnv  The query environment to use.
     * @param layout    The match data layout to use.
     * @param feature   The feature strings to run.
     * @param overrides The set of feature overrides.
     */
    FeatureTest(BlueprintFactory &factory,
                const IndexEnvironment &indexEnv,
                QueryEnvironment &queryEnv,
                MatchDataLayout &layout,
                const std::vector<vespalib::string> &features,
                const Properties &overrides);
    ~FeatureTest();

    /**
     * Constructs a new feature test.
     *
     * @param factory   The blueprint factory that holds all registered features.
     * @param indexEnv  The index environment to use.
     * @param queryEnv  The query environment to use.
     * @param layout    The match data layout to use.
     * @param feature   The feature string to run.
     * @param overrides The set of feature overrides.
     */
    FeatureTest(BlueprintFactory &factory,
                const IndexEnvironment &indexEnv,
                QueryEnvironment &queryEnv,
                MatchDataLayout &layout,
                const vespalib::string &feature,
                const Properties &overrides);
    /**
     * Necessary method to setup the internal feature execution manager. A test will typically assert on the return of
     * this method, since no test can run if setup failed.
     *
     * @return Whether or not setup was ok.
     */
    bool setup();

    /**
     * Creates and returns a match data builder object. This will clear whatever content is currently contained in this
     * runner. The builder offers a simple API to build a match data object.
     *
     * @return A builder object.
     */
    MatchDataBuilder::UP createMatchDataBuilder();

    /**
     * Executes the content of this runner, comparing the result to the given result set.
     *
     * @param expected The expected output.
     * @param docId    The document id to set on the match data object before running executors.
     * @return Whether or not the output matched the expected.
     */
    bool execute(const RankResult &expected, uint32_t docId = 1);

    /**
     * Convenience method to assert the final output of a feature string.
     *
     * @param expected The expected output.
     * @param epsilon  The allowed slack for comparing rank results.
     * @param docId    The document id to set on the match data object before running executors.
     * @return Whether or not the output matched the expected.
     */
    bool execute(feature_t expected, double epsilon = 0, uint32_t docId = 1);

    /**
     * Executes the content of this runner only and stores the result in the given rank result.
     *
     * @param result The rank result to store the rank scores.
     * @param docId  The document id to set on the match data object before running executors.
     * @return Whether the executors were executed.
     */
    bool executeOnly(RankResult & result, uint32_t docId = 1);

    /**
     * Resolve the only object feature that is present in the match data of the underlying
     * rank program.
     */
    vespalib::eval::Value::CREF resolveObjectFeature(uint32_t docid = 1);

private:
    BlueprintFactory                       &_factory;
    const IndexEnvironment                 &_indexEnv;
    QueryEnvironment                       &_queryEnv;
    std::vector<vespalib::string>           _features;
    MatchDataLayout                        &_layout;
    const Properties                       &_overrides;
    BlueprintResolver::SP                   _resolver;
    MatchData::UP                           _match_data;
    RankProgram::UP                         _rankProgram;
    bool                                    _doneSetup;

    void clear();
};

}
