// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/flow_completeness_feature.h>
#include <vespa/searchlib/features/jarowinklerdistancefeature.h>
#include <vespa/searchlib/features/proximityfeature.h>
#include <vespa/searchlib/features/querycompletenessfeature.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/features/reverseproximityfeature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/termeditdistancefeature.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using CollectionType = FieldInfo::CollectionType;

//---------------------------------------------------------------------------------------------------------------------
// Test
//---------------------------------------------------------------------------------------------------------------------
class Test : public FtTestApp {
public:
    Test();
    ~Test() override;
    int Main() override;
    void testJaroWinklerDistance();
    void testProximity();
    void testFlowCompleteness();
    void testQueryCompleteness();
    void testReverseProximity();
    void testTermEditDistance();

private:
    void assertJaroWinklerDistance(const vespalib::string &query, const vespalib::string &field, feature_t expected);
    void assertQueryCompleteness(FtFeatureTest & ft, uint32_t firstOcc, uint32_t hits, uint32_t miss);
    void assertTermEditDistance(const vespalib::string &query, const vespalib::string &field,
                                uint32_t expectedDel, uint32_t expectedIns, uint32_t expectedSub);

private:
    search::fef::BlueprintFactory _factory;
};

TEST_APPHOOK(Test);

Test::Test() = default;
Test::~Test() = default;

int
Test::Main()
{
    TEST_INIT("beta_features_test");

    // Configure factory with all known blueprints.
    setup_fef_test_plugin(_factory);
    setup_search_features(_factory);

    // Test all features.
    testJaroWinklerDistance(); TEST_FLUSH();
    testProximity();           TEST_FLUSH();
    testFlowCompleteness();    TEST_FLUSH();
    testQueryCompleteness();   TEST_FLUSH();
    testReverseProximity();    TEST_FLUSH();
    testTermEditDistance();    TEST_FLUSH();

    TEST_DONE();
    return 0;
}

void
Test::testJaroWinklerDistance()
{
    {
        // Test blueprint.
        JaroWinklerDistanceBlueprint pt;
        {
            EXPECT_TRUE(assertCreateInstance(pt, "jaroWinklerDistance"));

            StringList params, in, out;
            FT_SETUP_FAIL(pt, params);
            FT_SETUP_FAIL(pt, params.add("foo"));
            FT_SETUP_FAIL(pt, params.add("0"));
            params.clear();

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "afoo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wfoo");
            FT_SETUP_FAIL(pt, ie, params);
            FT_SETUP_OK  (pt, ie, params.add("foo"), in.add("fieldLength(foo)"), out.add("out"));
            FT_SETUP_FAIL(pt, ie, params.add("afoo"));
            FT_SETUP_FAIL(pt, ie, params.add("wfoo"));
            FT_SETUP_FAIL(pt, ie, params.add("1"));
        }
        {
            FT_DUMP_EMPTY(_factory, "jaroWinklerDistance");

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "abar");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wbar");
            FT_DUMP_EMPTY(_factory, "jaroWinklerDistance", ie); // must be a single value index field

            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
            StringList dump;
            FT_DUMP(_factory, "jaroWinklerDistance", ie, dump/*.add("jaroWinklerDistance(bar).out")*/);
        }
    }
    {
        // These measures are taken from table 6 in the paper "Overview of Record Linkage and Current Research Directions"
        // by William E. Winkler. It is available at: http://www.census.gov/srd/papers/pdf/rrs2006-02.pdf
        //
        // Note that the strings used as query and field here are transformed into query and field terms, and therefore
        // they all need to be unique. The second occurence of a character in the below names are therefore
        // capitalized. A comment is given whenever our result is different from what is presented in the paper (only 2
        // of 17 is actually different).
        assertJaroWinklerDistance("shackleford", "shackelford", 1 - 0.982f);
        assertJaroWinklerDistance("dunNigham",   "cunnigham",   1 - 0.852f); // 3x'n' in query, removed one
        assertJaroWinklerDistance("nichlesoN",   "nichulsoN",   1 - 0.956f);
        assertJaroWinklerDistance("jones",       "johnsoN",     1 - 0.832f);
        assertJaroWinklerDistance("masSey",      "masSie",      1 - 0.933f);
        assertJaroWinklerDistance("abroms",      "abrAms",      1 - 0.922f);
        assertJaroWinklerDistance("hardin",      "martinez",    1 - 0.722f); // no measure was given
        assertJaroWinklerDistance("itman",       "smith",       1 - 0.622f); // no measure was given
        assertJaroWinklerDistance("jeraldinE",   "geraldinE",   1 - 0.926f);
        assertJaroWinklerDistance("marhtA",      "marthA",      1 - 0.961f);
        assertJaroWinklerDistance("micheLlE",    "michael",     1 - 0.921f);
        assertJaroWinklerDistance("julies",      "juliUs",      1 - 0.933f);
        assertJaroWinklerDistance("tanyA",       "tonyA",       1 - 0.880f);
        assertJaroWinklerDistance("dwayne",      "duane",       1 - 0.765f); // was 0.840 in paper
        assertJaroWinklerDistance("sean",        "suSan",       1 - 0.672f); // was 0.805 in paper
        assertJaroWinklerDistance("jon",         "john",        1 - 0.933f);
        assertJaroWinklerDistance("jon",         "jan",         1 - 0.800f); // no measure was given
    }
}

void
Test::assertJaroWinklerDistance(const vespalib::string &query, const vespalib::string &field, feature_t expected)
{
    FtFeatureTest ft(_factory, "jaroWinklerDistance(foo)");
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    FT_SETUP(ft, query, StringMap().add("foo", field), 1);

    RankResult res;
    ASSERT_TRUE(ft.execute(res.setEpsilon(0.001).addScore("jaroWinklerDistance(foo).out", expected)));
}

void
Test::testProximity()
{

    { // Test blueprint.
        ProximityBlueprint prototype;
        {
            EXPECT_TRUE(assertCreateInstance(prototype, "proximity"));

            StringList params, in, out;
            FT_SETUP_FAIL(prototype, params);
            FT_SETUP_FAIL(prototype, params.add("foo"));
            FT_SETUP_FAIL(prototype, params.add("0"));
            FT_SETUP_FAIL(prototype, params.add("1"));
            FT_SETUP_FAIL(prototype, params.add("2"));
            params.clear();

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            FT_SETUP_FAIL(prototype, ie, params.add("foo"));
            FT_SETUP_FAIL(prototype, ie, params.add("0"));
            FT_SETUP_OK  (prototype, ie, params.add("1"), in, out.add("out").add("posA").add("posB"));
            FT_SETUP_FAIL(prototype, ie, params.add("2"));
        }

        {
            FT_DUMP_EMPTY(_factory, "proximity");

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            FT_DUMP_EMPTY(_factory, "proximity", ie); // must be an index field

            StringList dump;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
#ifdef VISIT_BETA_FEATURES
            for (uint32_t a = 0; a < 5; ++a) {
                for (uint32_t b = a + 1; b < 6; ++b) {
                    vespalib::string bn = vespalib::make_string("proximity(bar,%u,%u)", a, b);
                    dump.add(bn + ".out");
                    dump.add(bn + ".posA");
                    dump.add(bn + ".posB");
                }
            }
#endif
            FT_DUMP(_factory, "proximity", ie, dump);
        }
    }
    {
        // Test executor.
        FtFeatureTest ft(_factory, "proximity(foo,0,1)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ASSERT_TRUE(ft.setup());

        search::fef::test::RankResult exp;
        exp.addScore("proximity(foo,0,1).out",  util::FEATURE_MAX).
            addScore("proximity(foo,0,1).posA", util::FEATURE_MAX).
            addScore("proximity(foo,0,1).posB", util::FEATURE_MIN);
        ASSERT_TRUE(ft.execute(exp, 1));
    }
    {
        FtFeatureTest ft(_factory, "proximity(foo,0,1)");
        ASSERT_TRUE(!ft.setup());

        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());

        search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        ASSERT_TRUE(mdb->setFieldLength("foo", 50));
        ASSERT_TRUE(mdb->addOccurence("foo", 0, 30));
        search::fef::test::RankResult exp;
        exp.addScore("proximity(foo,0,1).out",  util::FEATURE_MAX).
            addScore("proximity(foo,0,1).posA", util::FEATURE_MAX).
            addScore("proximity(foo,0,1).posB", util::FEATURE_MIN);
        ASSERT_TRUE(mdb->apply(1));
        ASSERT_TRUE(ft.execute(exp, 1));

        ASSERT_TRUE(mdb->addOccurence("foo", 1, 20));
        ASSERT_TRUE(mdb->apply(2));
        ASSERT_TRUE(ft.execute(exp, 2));

        ASSERT_TRUE(mdb->addOccurence("foo", 0, 10));
        ASSERT_TRUE(mdb->apply(3));
        exp .clear()
            .addScore("proximity(foo,0,1).out",  10.0f)
            .addScore("proximity(foo,0,1).posA", 10.0f)
            .addScore("proximity(foo,0,1).posB", 20.0f);
        ASSERT_TRUE(ft.execute(exp, 3));
    }
    {
        for (int a = 0; a < 10; ++a) {
            for (int b = 0; b < 10; ++b) {
                FtFeatureTest ft(_factory, "proximity(foo,0,1)");
                ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
                ft.getQueryEnv().getBuilder().addAllFields();
                ft.getQueryEnv().getBuilder().addAllFields();
                ASSERT_TRUE(ft.setup());

                search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
                ASSERT_TRUE(mdb->setFieldLength("foo", 10));
                ASSERT_TRUE(mdb->addOccurence("foo", 0, a));
                ASSERT_TRUE(mdb->addOccurence("foo", 1, b));
                ASSERT_TRUE(mdb->apply(1));

                search::fef::test::RankResult exp;
                exp .addScore("proximity(foo,0,1).out",  a < b ? b - a : util::FEATURE_MAX)
                    .addScore("proximity(foo,0,1).posA", a < b ? a     : util::FEATURE_MAX)
                    .addScore("proximity(foo,0,1).posB", a < b ? b     : util::FEATURE_MIN);
                TEST_STATE(vespalib::make_string("a=%u, b=%u", a, b).c_str());
                { // reset lazy evaluation
                    RankResult dummy;
                    ft.executeOnly(dummy, 0);
                }
                EXPECT_TRUE(ft.execute(exp));
            }
        }
    }
}

void
Test::testQueryCompleteness()
{
    { // Test blueprint.
        QueryCompletenessBlueprint prototype;

        EXPECT_TRUE(assertCreateInstance(prototype, "queryCompleteness"));

        StringList params, in, out;
        FT_SETUP_FAIL(prototype, params);
        FT_SETUP_FAIL(prototype, params.add("foo"));
        FT_SETUP_FAIL(prototype, params.add("0"));
        params.clear();

        FtIndexEnvironment ie;
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        FT_SETUP_OK  (prototype, ie, params.add("foo"), in, out.add("hit").add("miss"));
        FT_SETUP_OK  (prototype, ie, params.add("0"),   in, out);
        FT_SETUP_OK  (prototype, ie, params.add("1"),   in, out);
        FT_SETUP_FAIL(prototype, ie, params.add("2"));

        FT_DUMP_EMPTY(_factory, "queryCompleteness");
        FT_DUMP_EMPTY(_factory, "queryCompleteness", ie);
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "queryCompleteness(foo)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        // add 5 term nodes
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());
        // from 0 to 5 hits (5 to 0 misses)
        for (uint32_t i = 0; i < 6; ++i) {
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            mdb->setFieldLength("foo", 10);
            for (uint32_t j = 0; j < i; ++j) {
                mdb->addOccurence("foo", j, j);
            }
            ASSERT_TRUE(mdb->apply(1));
            RankResult exp;
            exp.addScore("queryCompleteness(foo).hit", (feature_t)(i));
            exp.addScore("queryCompleteness(foo).miss", (feature_t)(5 - i));
            { // reset lazy evaluation
                RankResult dummy;
                ft.executeOnly(dummy, 0);
            }
            EXPECT_TRUE(ft.execute(exp));
        }
    }
    { // Test executor.
        FtFeatureTest ft(_factory, "queryCompleteness(foo,5,10)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());

        // before window
        assertQueryCompleteness(ft, 4, 0, 1);
        // inside window
        assertQueryCompleteness(ft, 5, 1, 0);
        // inside window
        assertQueryCompleteness(ft, 9, 1, 0);
        // after window
        assertQueryCompleteness(ft, 10, 0, 1);
    }
}

void
Test::assertQueryCompleteness(FtFeatureTest & ft, uint32_t firstOcc, uint32_t hits, uint32_t miss)
{
    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
    mdb->setFieldLength("foo", 20);
    mdb->addOccurence("foo", 0, firstOcc);
    ASSERT_TRUE(mdb->apply(1));
    RankResult exp;
    exp.addScore("queryCompleteness(foo,5,10).hit", hits);
    exp.addScore("queryCompleteness(foo,5,10).miss", miss);
    { // reset lazy evaluation
        RankResult dummy;
        ft.executeOnly(dummy, 0);
    }
    EXPECT_TRUE(ft.execute(exp));
}

// BFI implementation: brute force and ignorance
int cntFlow(int m1, int m2, int m3, int m4)
{
    int flow = 0;

    for (int p1p = 0; p1p < 4; p1p++) {
        if (((1 << p1p) & m1) == 0) continue;
        for (int p2p = 0; p2p < 4; p2p++) {
            if (((1 << p2p) & m2) == 0) continue;
            int f2 = 1;
            if (p2p != p1p) ++f2;
            for (int p3p = 0; p3p < 4; p3p++) {
                if (((1 << p3p) & m3) == 0) continue;
                int f3 = f2;
                if (p3p != p1p && p3p != p2p) ++f3;
                for (int p4p = 0; p4p < 4; p4p++) {
                    if (((1 << p4p) & m4) == 0) continue;
                    int f4 = f3;
                    if (p4p != p1p && p4p != p2p && p4p != p3p) ++f4;
                    if (flow < f4) flow = f4;
                }
            }
        }
    }
    return flow;
}

void
Test::testFlowCompleteness()
{
    { // Test blueprint.
        TEST_STATE("test flow completeness blueprint");
        FlowCompletenessBlueprint prototype;

        EXPECT_TRUE(assertCreateInstance(prototype, "flowCompleteness"));

        StringList params, in, out;
        TEST_DO(FT_SETUP_FAIL(prototype, params));
        TEST_DO(FT_SETUP_FAIL(prototype, params.add("foo")));
        TEST_DO(FT_SETUP_FAIL(prototype, params.add("0")));

        FtIndexEnvironment ie;
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");

        params.clear();
        params.add("foo");

        out.add("completeness").add("fieldCompleteness")
            .add("queryCompleteness").add("elementWeight")
            .add("weight").add("flow");

        StringList expDump;
        for (size_t i = 0; i < out.size(); ++i) {
            vespalib::string fn = "flowCompleteness(foo).";
            fn.append(out[i]);
            expDump.push_back(fn);
        }

        TEST_DO(FT_SETUP_OK(prototype, ie, params, in, out));
        TEST_DO(FT_SETUP_FAIL(prototype, ie, params.add("2")));
        TEST_DO(FT_DUMP_EMPTY(_factory, "flowCompleteness"));
#ifdef notyet
        TEST_DO(FT_DUMP(_factory, "flowCompleteness", ie, expDump));
#endif
    }

    { // Test executor.
        TEST_STATE("test flow completeness executor");

        FtFeatureTest ft(_factory, "flowCompleteness(foo)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        // add 5 term nodes
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());
        // from 0 to 5 hits (5 to 0 misses)
        for (uint32_t i = 0; i < 6; ++i) {
            MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            mdb->setFieldLength("foo", 10);
            for (uint32_t j = 0; j < i; ++j) {
                mdb->addOccurence("foo", j, j);
            }
            ASSERT_TRUE(mdb->apply(1));
            RankResult exp;
            exp.setEpsilon(0.000001);
            exp.addScore("flowCompleteness(foo)", i * 0.15);
            exp.addScore("flowCompleteness(foo).completeness", i * 0.15); // == 0.1*0.5 + 0.2*(1-0.5)
            exp.addScore("flowCompleteness(foo).fieldCompleteness", i * 0.1);
            exp.addScore("flowCompleteness(foo).queryCompleteness", i * 0.2);
            exp.addScore("flowCompleteness(foo).elementWeight", i > 0 ? 1 : 0);
            exp.addScore("flowCompleteness(foo).weight", 100.0);
            exp.addScore("flowCompleteness(foo).flow", i);
            TEST_STATE("run execute");
            { // reset lazy evaluation
                RankResult dummy;
                ft.executeOnly(dummy, 0);
            }
            EXPECT_TRUE(ft.execute(exp));
        }
    }


    { // Test executor, pass 2
        TEST_STATE("test flow completeness executor (pass 2)");

        FtFeatureTest ft(_factory, "flowCompleteness(foo)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        // add 4 term nodes
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());

        // each term will have 1 to 3 positions it matches,
        // with various points of overlap

        for (uint32_t t0m = 1; t0m < 15 ; ++t0m) {

            for (uint32_t t1m = 1; t1m < 15 ; ++t1m) {

                for (uint32_t t2m = 1; t2m < 15 ; ++t2m) {

                    for (uint32_t t3m = 1; t3m < 15 ; ++t3m) {

                        int flow = cntFlow(t0m, t1m, t2m, t3m);

                        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
                        mdb->setFieldLength("foo", 4);
                        for (int pos = 0; pos < 4; ++pos) {
                            if (((1 << pos) & t0m) != 0) mdb->addOccurence("foo", 0, pos);
                            if (((1 << pos) & t1m) != 0) mdb->addOccurence("foo", 1, pos);
                            if (((1 << pos) & t2m) != 0) mdb->addOccurence("foo", 2, pos);
                            if (((1 << pos) & t3m) != 0) mdb->addOccurence("foo", 3, pos);
                        }

                        ASSERT_TRUE(mdb->apply(1));
                        RankResult exp;
                        exp.setEpsilon(0.0001);
                        exp.addScore("flowCompleteness(foo)", flow * 0.25);
                        exp.addScore("flowCompleteness(foo).completeness", flow * 0.25);
                        exp.addScore("flowCompleteness(foo).fieldCompleteness", flow * 0.25);
                        exp.addScore("flowCompleteness(foo).queryCompleteness", flow * 0.25);
                        exp.addScore("flowCompleteness(foo).elementWeight", 1);
                        exp.addScore("flowCompleteness(foo).weight", 100.0);
                        exp.addScore("flowCompleteness(foo).flow", flow);
                        TEST_STATE(vespalib::make_string("execute t0m=%u t1m=%u t2m=%u t3m=%u flow=%u",
                                        t0m, t1m, t2m, t3m, flow).c_str());
                        { // reset lazy evaluation
                            RankResult dummy;
                            ft.executeOnly(dummy, 0);
                        }
                        ASSERT_TRUE(ft.execute(exp));
                    }
                }
            }
        }
    }
}


void
Test::testReverseProximity()
{
    { // Test blueprint.
        ReverseProximityBlueprint prototype;
        {
            EXPECT_TRUE(assertCreateInstance(prototype, "reverseProximity"));

            StringList params, in, out;
            FT_SETUP_FAIL(prototype, params);
            FT_SETUP_FAIL(prototype, params.add("foo"));
            FT_SETUP_FAIL(prototype, params.add("0"));
            FT_SETUP_FAIL(prototype, params.add("1"));
            FT_SETUP_FAIL(prototype, params.add("2"));
            params.clear();

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            FT_SETUP_FAIL(prototype, ie, params.add("foo"));
            FT_SETUP_FAIL(prototype, ie, params.add("0"));
            FT_SETUP_OK  (prototype, ie, params.add("1"), in, out.add("out").add("posA").add("posB"));
            FT_SETUP_FAIL(prototype, ie, params.add("2"));
        }

        {
            FT_DUMP_EMPTY(_factory, "reverseProximity");
            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            FT_DUMP_EMPTY(_factory, "reverseProximity", ie); // must be an index field

            StringList dump;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
#ifdef VISIT_BETA_FEATURES
            for (uint32_t a = 0; a < 5; ++a) {
                for (uint32_t b = a + 1; b < 6; ++b) {
                    vespalib::string bn = vespalib::make_string("reverseProximity(bar,%u,%u)", a, b);
                    dump.add(bn + ".out");
                    dump.add(bn + ".posA");
                    dump.add(bn + ".posB");
                }
            }
#endif
            FT_DUMP(_factory, "reverseProximity", ie, dump);
        }
    }


    { // Test executor.
        FtFeatureTest ft(_factory, "reverseProximity(foo,0,1)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ASSERT_TRUE(ft.setup());
        search::fef::test::RankResult exp;
        exp.addScore("reverseProximity(foo,0,1).out",  util::FEATURE_MAX).
            addScore("reverseProximity(foo,0,1).posA", util::FEATURE_MIN).
            addScore("reverseProximity(foo,0,1).posB", util::FEATURE_MAX);
        ASSERT_TRUE(ft.execute(exp, 1));
    }
    {
        FtFeatureTest ft(_factory, "reverseProximity(foo,0,1)");         ASSERT_TRUE(!ft.setup());
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();                     ASSERT_TRUE(ft.setup());

        search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        ASSERT_TRUE(mdb->setFieldLength("foo", 50));
        ASSERT_TRUE(mdb->addOccurence("foo", 0, 20));
        search::fef::test::RankResult exp;
        exp .addScore("reverseProximity(foo,0,1).out",  util::FEATURE_MAX)
            .addScore("reverseProximity(foo,0,1).posA", util::FEATURE_MIN)
            .addScore("reverseProximity(foo,0,1).posB", util::FEATURE_MAX);
        ASSERT_TRUE(mdb->apply(1));
        ASSERT_TRUE(ft.execute(exp, 1));

        ASSERT_TRUE(mdb->addOccurence("foo", 1, 30));
        ASSERT_TRUE(mdb->apply(2));
        ASSERT_TRUE(ft.execute(exp, 2));

        ASSERT_TRUE(mdb->addOccurence("foo", 1, 10));
        ASSERT_TRUE(mdb->apply(3));
        exp .clear()
            .addScore("reverseProximity(foo,0,1).out",  10.0f)
            .addScore("reverseProximity(foo,0,1).posA", 20.0f)
            .addScore("reverseProximity(foo,0,1).posB", 10.0f);
        ASSERT_TRUE(ft.execute(exp, 3));
    }
    {
        for (int a = 0; a < 10; ++a) {
            for (int b = 0; b < 10; ++b) {
                FtFeatureTest ft(_factory, "reverseProximity(foo,0,1)");
                ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
                ft.getQueryEnv().getBuilder().addAllFields();
                ft.getQueryEnv().getBuilder().addAllFields();
                ASSERT_TRUE(ft.setup());

                search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
                ASSERT_TRUE(mdb->setFieldLength("foo", 10));
                ASSERT_TRUE(mdb->addOccurence("foo", 0, a));
                ASSERT_TRUE(mdb->addOccurence("foo", 1, b));
                ASSERT_TRUE(mdb->apply(1));

                search::fef::test::RankResult exp;
                exp .addScore("reverseProximity(foo,0,1).out",  a >= b ? a - b : util::FEATURE_MAX)
                    .addScore("reverseProximity(foo,0,1).posA", a >= b ? a     : util::FEATURE_MIN)
                    .addScore("reverseProximity(foo,0,1).posB", a >= b ? b     : util::FEATURE_MAX);
                ASSERT_TRUE(ft.execute(exp));
            }
        }
    }
}

void
Test::testTermEditDistance()
{
    { // Test blueprint.
        TermEditDistanceBlueprint prototype;
        {
            EXPECT_TRUE(assertCreateInstance(prototype, "termEditDistance"));

            StringList params, in, out;
            FT_SETUP_FAIL(prototype, params);
            FT_SETUP_FAIL(prototype, params.add("foo"));
            FT_SETUP_FAIL(prototype, params.add("0"));

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "afoo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wfoo");
            FT_SETUP_FAIL(prototype, ie, params.clear());
            FT_SETUP_OK  (prototype, ie, params.add("foo"), in.add("fieldLength(foo)"), out.add("out").add("del").add("ins").add("sub"));
            FT_SETUP_FAIL(prototype, ie, params.add("afoo"));
            FT_SETUP_FAIL(prototype, ie, params.add("wfoo"));
            FT_SETUP_FAIL(prototype, ie, params.add("0"));
        }

        {
            FT_DUMP_EMPTY(_factory, "termEditDistance");
            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "abar");
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wbar");
            FT_DUMP_EMPTY(_factory, "termEditDistance", ie); // must be a single-value index field

            StringList dump;
#ifdef VISIT_BETA_FEATURES
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
            vespalib::string bn = "termEditDistance(bar)";
            dump.add(bn + ".out");
            dump.add(bn + ".del");
            dump.add(bn + ".ins");
            dump.add(bn + ".sub");
#endif
            FT_DUMP(_factory, "termEditDistance", ie, dump);
        }
    }

    { // Test executor.
        assertTermEditDistance("abcde", "abcde", 0, 0, 0);
        assertTermEditDistance("abcde", "abcd.", 0, 0, 1);
        assertTermEditDistance("abcde", ".bcd.", 0, 0, 2);
        assertTermEditDistance("abcde", ".bc..", 0, 0, 3);
        assertTermEditDistance("abcde", "..c..", 0, 0, 4);
        assertTermEditDistance("abcd" , "..c..", 0, 1, 3);
        assertTermEditDistance("abc",   "..c..", 0, 2, 2);
        assertTermEditDistance("ab",    "..b..", 0, 3, 1);
        assertTermEditDistance("a",     "..a..", 0, 4, 0);
    }
}

void
Test::assertTermEditDistance(const vespalib::string &query, const vespalib::string &field,
                             uint32_t expectedDel, uint32_t expectedIns, uint32_t expectedSub)
{
    // Setup feature test.
    vespalib::string feature = "termEditDistance(foo)";
    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    StringMap foo;
    foo.add("foo", field);
    FT_SETUP(ft, query, foo, 1);

    // Execute and compare results.
    search::fef::test::RankResult exp;
    exp .addScore(feature + ".out", (feature_t)(expectedDel*1 + expectedIns*1 + expectedSub*1))
        .addScore(feature + ".del", (feature_t)expectedDel)
        .addScore(feature + ".ins", (feature_t)expectedIns)
        .addScore(feature + ".sub", (feature_t)expectedSub);
    ASSERT_TRUE(ft.execute(exp));
}
