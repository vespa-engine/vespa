// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/fieldmatch/params.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/vespalib/gtest/gtest.h>

/*
 * Base class for test application used by feature unit tests.
 */
struct FtTestAppBase {
    using string = std::string;
    static void FT_SETUP_FAIL(const search::fef::Blueprint &prototype, const StringList &params);
    static void FT_SETUP_FAIL(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env,
                              const StringList &params);
    static void FT_SETUP_OK(const search::fef::Blueprint &prototype, const StringList &params,
                            const StringList &expectedIn, const StringList &expectedOut);
    static void FT_SETUP_OK(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env,
                            const StringList &params, const StringList &expectedIn, const StringList &expectedOut);

    static void FT_DUMP_EMPTY(search::fef::BlueprintFactory &factory, const std::string &baseName);
    static void FT_DUMP_EMPTY(search::fef::BlueprintFactory &factory, const std::string &baseName,
                              search::fef::test::IndexEnvironment &env);
    static void FT_DUMP(search::fef::BlueprintFactory &factory, const std::string &baseName,
                        const StringList &expected);
    static void FT_DUMP(search::fef::BlueprintFactory &factory, const std::string &baseName,
                        search::fef::test::IndexEnvironment &env,
                        const StringList &expected);

    static void FT_EQUAL(const std::vector<string> &expected, const std::vector<string> &actual,
                         const std::string & prefix = "");

    static void FT_LOG(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env, const StringList &params);
    static void FT_LOG(const std::string &prefix, const std::vector<std::string> &arr);


    static void FT_SETUP(FtFeatureTest & test, const std::string & query, const StringMap & index, uint32_t docId);
    static void FT_SETUP(FtFeatureTest & test, const FtQuery & query, const StringVectorMap & index, uint32_t docId);

    static void FT_SETUP(FtFeatureTest &test, const FtQuery &query, const FtIndex &index, uint32_t docId);

    static void setupQueryEnv(FtQueryEnvironment & queryEnv, const FtQuery & query);
    static void setupFieldMatch(FtFeatureTest & test, const std::string & indexName,
                                const std::string & query, const std::string & field,
                                const search::features::fieldmatch::Params * params,
                                uint32_t totalTermWeight, feature_t totalSignificance,
                                uint32_t docId);

    static search::fef::test::RankResult toRankResult(const std::string & baseName,
                                                      const std::string & result,
                                                      const std::string & separator = " ");

    template <typename T>
    static bool assertCreateInstance(const T & prototype, const std::string & baseName) {
        search::fef::Blueprint::UP bp = prototype.createInstance();
        bool failed = false;
        EXPECT_TRUE(dynamic_cast<T*>(bp.get()) != nullptr) << (failed = true, "");
        if (failed) {
            return false;
        }
        EXPECT_EQ(bp->getBaseName(), baseName) << (failed = true, "");
        if (failed) {
            return false;
        }
        return true;
    }
};
