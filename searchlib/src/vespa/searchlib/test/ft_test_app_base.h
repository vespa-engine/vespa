// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/fieldmatch/params.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#ifdef ENABLE_GTEST_MIGRATION
#include <vespa/vespalib/gtest/gtest.h>
#else
#include <vespa/vespalib/testkit/test_macros.h>
#endif

#ifdef ENABLE_GTEST_MIGRATION
#define FtTestAppBase FtTestAppBaseForGTest
#endif

/*
 * Base class for test application used by feature unit tests.
 */
struct FtTestAppBase {
    using string = vespalib::string;
    static void FT_SETUP_FAIL(const search::fef::Blueprint &prototype, const StringList &params);
    static void FT_SETUP_FAIL(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env,
                              const StringList &params);
    static void FT_SETUP_OK(const search::fef::Blueprint &prototype, const StringList &params,
                            const StringList &expectedIn, const StringList &expectedOut);
    static void FT_SETUP_OK(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env,
                            const StringList &params, const StringList &expectedIn, const StringList &expectedOut);

    static void FT_DUMP_EMPTY(search::fef::BlueprintFactory &factory, const vespalib::string &baseName);
    static void FT_DUMP_EMPTY(search::fef::BlueprintFactory &factory, const vespalib::string &baseName,
                              search::fef::test::IndexEnvironment &env);
    static void FT_DUMP(search::fef::BlueprintFactory &factory, const vespalib::string &baseName,
                        const StringList &expected);
    static void FT_DUMP(search::fef::BlueprintFactory &factory, const vespalib::string &baseName,
                        search::fef::test::IndexEnvironment &env,
                        const StringList &expected);

    static void FT_EQUAL(const std::vector<string> &expected, const std::vector<string> &actual,
                         const vespalib::string & prefix = "");

    static void FT_LOG(const search::fef::Blueprint &prototype, const search::fef::test::IndexEnvironment &env, const StringList &params);
    static void FT_LOG(const vespalib::string &prefix, const std::vector<vespalib::string> &arr);


    static void FT_SETUP(FtFeatureTest & test, const vespalib::string & query, const StringMap & index, uint32_t docId);
    static void FT_SETUP(FtFeatureTest & test, const FtQuery & query, const StringVectorMap & index, uint32_t docId);

    static void FT_SETUP(FtFeatureTest &test, const FtQuery &query, const FtIndex &index, uint32_t docId);

    static void setupQueryEnv(FtQueryEnvironment & queryEnv, const FtQuery & query);
    static void setupFieldMatch(FtFeatureTest & test, const vespalib::string & indexName,
                                const vespalib::string & query, const vespalib::string & field,
                                const search::features::fieldmatch::Params * params,
                                uint32_t totalTermWeight, feature_t totalSignificance,
                                uint32_t docId);

    static search::fef::test::RankResult toRankResult(const vespalib::string & baseName,
                                                      const vespalib::string & result,
                                                      const vespalib::string & separator = " ");

    template <typename T>
    static bool assertCreateInstance(const T & prototype, const vespalib::string & baseName) {
        search::fef::Blueprint::UP bp = prototype.createInstance();
#ifdef ENABLE_GTEST_MIGRATION
        bool failed = false;
        EXPECT_TRUE(dynamic_cast<T*>(bp.get()) != NULL) << (failed = true, "");
        if (failed) {
            return false;
        }
        EXPECT_EQ(bp->getBaseName(), baseName) << (failed = true, "");
        if (failed) {
            return false;
        }
#else
        if (!EXPECT_TRUE(dynamic_cast<T*>(bp.get()) != NULL)) return false;
        if (!EXPECT_EQUAL(bp->getBaseName(), baseName)) return false;
#endif
        return true;
    }
};
