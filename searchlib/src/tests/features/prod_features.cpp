// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("prod_features_test");

#include "prod_features.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/features/agefeature.h>
#include <vespa/searchlib/features/array_parser.hpp>
#include <vespa/searchlib/features/attributefeature.h>
#include <vespa/searchlib/features/attributematchfeature.h>
#include <vespa/searchlib/features/closenessfeature.h>
#include <vespa/searchlib/features/distancefeature.h>
#include <vespa/searchlib/features/dotproductfeature.h>
#include <vespa/searchlib/features/fieldlengthfeature.h>
#include <vespa/searchlib/features/fieldmatchfeature.h>
#include <vespa/searchlib/features/fieldtermmatchfeature.h>
#include <vespa/searchlib/features/firstphasefeature.h>
#include <vespa/searchlib/features/foreachfeature.h>
#include <vespa/searchlib/features/freshnessfeature.h>
#include <vespa/searchlib/features/matchcountfeature.h>
#include <vespa/searchlib/features/matchesfeature.h>
#include <vespa/searchlib/features/matchfeature.h>
#include <vespa/searchlib/features/nowfeature.h>
#include <vespa/searchlib/features/queryfeature.h>
#include <vespa/searchlib/features/querytermcountfeature.h>
#include <vespa/searchlib/features/randomfeature.h>
#include <vespa/searchlib/features/random_normal_feature.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/termfeature.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/features/weighted_set_parser.hpp>
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/queryproperties.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/util/rand48.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/string_hash.h>
#include <cmath>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;

using search::AttributeVector;
using search::AttributeFactory;
using search::IntegerAttribute;
using search::FloatingPointAttribute;
using search::StringAttribute;
using search::WeightedSetStringExtAttribute;
using search::attribute::WeightedEnumContent;

using AttributePtr = AttributeVector::SP;
using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using CollectionType = FieldInfo::CollectionType;

const double EPS = 10e-6;


TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT("prod_features_test");

    // Configure factory with all known blueprints.
    setup_fef_test_plugin(_factory);
    setup_search_features(_factory);

    // Test all features.
    TEST_DO(testFramework());           TEST_FLUSH();
    TEST_DO(testFtLib());               TEST_FLUSH();
    TEST_DO(testAge());                 TEST_FLUSH();
    TEST_DO(testAttribute());           TEST_FLUSH();
    TEST_DO(testAttributeMatch());      TEST_FLUSH();
    TEST_DO(testCloseness());           TEST_FLUSH();
    TEST_DO(testMatchCount());          TEST_FLUSH();
    TEST_DO(testDistance());            TEST_FLUSH();
    TEST_DO(testDistanceToPath());      TEST_FLUSH();
    TEST_DO(testDotProduct());          TEST_FLUSH();
    TEST_DO(testFieldLength());         TEST_FLUSH();
    TEST_DO(testFieldMatch());          TEST_FLUSH();
    TEST_DO(testFieldTermMatch());      TEST_FLUSH();
    TEST_DO(testFirstPhase());          TEST_FLUSH();
    TEST_DO(testForeach());             TEST_FLUSH();
    TEST_DO(testFreshness());           TEST_FLUSH();
    TEST_DO(testMatch());               TEST_FLUSH();
    TEST_DO(testMatches());             TEST_FLUSH();
    TEST_DO(testNow());                 TEST_FLUSH();
    TEST_DO(testQuery());               TEST_FLUSH();
    TEST_DO(testQueryTermCount());      TEST_FLUSH();
    TEST_DO(testRandom());              TEST_FLUSH();
    TEST_DO(testRandomNormal());        TEST_FLUSH();
    TEST_DO(testRankingExpression());   TEST_FLUSH();
    TEST_DO(testTerm());                TEST_FLUSH();
    TEST_DO(testTermDistance());        TEST_FLUSH();
    TEST_DO(testUtils());               TEST_FLUSH();

    TEST_DONE();
    return 0;
}


void
Test::testFtLib()
{
    { // toQuery
        FtQuery q = FtUtil::toQuery("a b!50 0.5:c!200%0.5  d%0.3   e!300 0.3:f ");
        ASSERT_TRUE(q.size() == 6);
        EXPECT_EQUAL(q[0].term, vespalib::string("a"));
        EXPECT_EQUAL(q[0].termWeight.percent(), 100);
        EXPECT_APPROX(q[0].connexity, 0.1f, EPS);
        EXPECT_APPROX(q[0].significance, 0.1f, EPS);
        EXPECT_EQUAL(q[1].term, vespalib::string("b"));
        EXPECT_EQUAL(q[1].termWeight.percent(), 50);
        EXPECT_APPROX(q[1].connexity, 0.1f, EPS);
        EXPECT_APPROX(q[1].significance, 0.1f, EPS);
        EXPECT_EQUAL(q[2].term, vespalib::string("c"));
        EXPECT_EQUAL(q[2].termWeight.percent(), 200);
        EXPECT_APPROX(q[2].connexity, 0.5f, EPS);
        EXPECT_APPROX(q[2].significance, 0.5f, EPS);
        EXPECT_EQUAL(q[3].term, vespalib::string("d"));
        EXPECT_EQUAL(q[3].termWeight.percent(), 100);
        EXPECT_APPROX(q[3].connexity, 0.1f, EPS);
        EXPECT_APPROX(q[3].significance, 0.3f, EPS);
        EXPECT_EQUAL(q[4].term, vespalib::string("e"));
        EXPECT_EQUAL(q[4].termWeight.percent(), 300);
        EXPECT_APPROX(q[4].connexity, 0.1f, EPS);
        EXPECT_APPROX(q[4].significance, 0.1f, EPS);
        EXPECT_EQUAL(q[5].term, vespalib::string("f"));
        EXPECT_EQUAL(q[5].termWeight.percent(), 100);
        EXPECT_APPROX(q[5].connexity, 0.3f, EPS);
        EXPECT_APPROX(q[5].significance, 0.1f, EPS);
    }
    { // toRankResult
        RankResult rr = toRankResult("foo", "a:0.5 b:-0.5  c:2   d:3 ");
        std::vector<vespalib::string> keys = rr.getKeys();
        ASSERT_TRUE(keys.size() == 4);
        EXPECT_EQUAL(keys[0], vespalib::string("foo.a"));
        EXPECT_EQUAL(keys[1], vespalib::string("foo.b"));
        EXPECT_EQUAL(keys[2], vespalib::string("foo.c"));
        EXPECT_EQUAL(keys[3], vespalib::string("foo.d"));
        EXPECT_APPROX(rr.getScore("foo.a"), 0.5f, EPS);
        EXPECT_APPROX(rr.getScore("foo.b"), -0.5f, EPS);
        EXPECT_APPROX(rr.getScore("foo.c"), 2.0f, EPS);
        EXPECT_APPROX(rr.getScore("foo.d"), 3.0f, EPS);
    }
}


void
Test::testAge()
{
    { // Test blueprint
        FtIndexEnvironment idx_env;
        idx_env.getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "datetime")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "datetime2");

        AgeBlueprint pt;
        EXPECT_TRUE(assertCreateInstance(pt, "age"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, idx_env, params);
        FT_SETUP_OK(pt, idx_env, params.add("datetime"), in.add("now"), out.add("out"));
        FT_SETUP_FAIL(pt, idx_env, params.add("datetime2"));

        FT_DUMP_EMPTY(_factory, "age");
    }

    { // Test executor
        assertAge(0,   "doctime", 60, 120);
        assertAge(60,  "doctime", 180, 120);
        assertAge(15000000000, "doctime", 20000000000, 5000000000);
    }
}

void
Test::assertAge(feature_t expAge, const vespalib::string & attr, uint64_t now, uint64_t docTime)
{
    vespalib::string feature = "age(" + attr + ")";
    FtFeatureTest ft(_factory, feature);
    setupForAgeTest(ft, docTime);
    ft.getQueryEnv().getProperties().add(queryproperties::now::SystemTime::NAME,
                                         vespalib::make_string("%" PRIu64, now));
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(RankResult().addScore(feature, expAge)));
}

void
Test::setupForAgeTest(FtFeatureTest & ft, uint64_t docTime)
{
    AttributePtr doctime = AttributeFactory::createAttribute("doctime", AVC(AVBT::INT64,  AVCT::SINGLE));
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "doctime");
    doctime->addReservedDoc();
    doctime->addDocs(1);
    ft.getIndexEnv().getAttributeMap().add(doctime);
    (static_cast<IntegerAttribute *>(doctime.get()))->update(1, docTime);
    doctime->commit();
}

void
Test::testAttribute()
{
    AttributeBlueprint prototype;
    {
        FtIndexEnvironment idx_env;
        idx_env.getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");

        EXPECT_TRUE(assertCreateInstance(prototype, "attribute"));

        StringList params, in, out;
        FT_SETUP_FAIL(prototype, idx_env, params);            // expects 1 - 2 params

        FT_SETUP_OK(prototype, idx_env, params.add("bar"), in,
                    out.add("value").add("weight").add("contains").add("count"));
        FT_SETUP_OK(prototype, idx_env, params.add("0"), in, out);

        FT_DUMP_EMPTY(_factory, "attribute");
    }
    { // single attributes
        RankResult exp;
        exp.addScore("attribute(sint)", 10).
            addScore("attribute(sint,0)", 10).
            addScore("attribute(sfloat)", 60.5f).
            addScore("attribute(sstr)", (feature_t)vespalib::hash_code("foo")).
            addScore("attribute(sint).count", 1).
            addScore("attribute(sfloat).count", 1).
            addScore("attribute(sstr).count", 1).
            addScore("attribute(udefint)", search::attribute::getUndefined<feature_t>()).
            addScore("attribute(udeffloat)", search::attribute::getUndefined<feature_t>()).
            addScore("attribute(udefstr)", (feature_t)vespalib::hash_code(""));

        FtFeatureTest ft(_factory, exp.getKeys());
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint").
            addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sfloat").
            addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sstr").
            addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udefint").
            addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udeffloat").
            addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udefstr");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(exp));
    }
    { // array attributes
        RankResult exp;
        exp.addScore("attribute(aint)", 0).
            addScore("attribute(aint,0)", 20).
            addScore("attribute(aint,1)", 30).
            addScore("attribute(aint,2)", 0).
            addScore("attribute(afloat,0)", 70.5f).
            addScore("attribute(afloat,1)", 80.5f).
            addScore("attribute(astr,0)", (feature_t)vespalib::hash_code("bar")).
            addScore("attribute(astr,1)", (feature_t)vespalib::hash_code("baz")).
            addScore("attribute(aint).count", 2).
            addScore("attribute(aint,0).count", 0).
            addScore("attribute(afloat).count", 2).
            addScore("attribute(afloat,0).count", 0).
            addScore("attribute(astr).count", 2).
            addScore("attribute(astr,0).count", 0);

        FtFeatureTest ft(_factory, exp.getKeys());
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint").
            addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "afloat").
            addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "astr");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(exp));
    }
    { // weighted set attributes
        RankResult exp;
        exp.addScore("attribute(wsint).value", 0).
            addScore("attribute(wsint).weight", 0).
            addScore("attribute(wsint).contains", 0).
            addScore("attribute(wsint,100).value", 0).
            addScore("attribute(wsint,100).weight", 0).
            addScore("attribute(wsint,100).contains", 0).
            addScore("attribute(wsint,40).value", 40).
            addScore("attribute(wsint,40).weight", 10).
            addScore("attribute(wsint,40).contains", 1).
            addScore("attribute(wsint,50).value", 50).
            addScore("attribute(wsint,50).weight", 20).
            addScore("attribute(wsint,50).contains", 1).
            addScore("attribute(wsfloat).value", 0).
            addScore("attribute(wsfloat).weight", 0).
            addScore("attribute(wsfloat).contains", 0).
            addScore("attribute(wsfloat,1000.5).value", 0).
            addScore("attribute(wsfloat,1000.5).weight", 0).
            addScore("attribute(wsfloat,1000.5).contains", 0).
            addScore("attribute(wsfloat,90.5).value", 90.5f).
            addScore("attribute(wsfloat,90.5).weight", -30).
            addScore("attribute(wsfloat,90.5).contains", 1).
            addScore("attribute(wsfloat,100.5).value", 100.5f).
            addScore("attribute(wsfloat,100.5).weight", -40).
            addScore("attribute(wsfloat,100.5).contains", 1).
            addScore("attribute(wsstr).value", 0).
            addScore("attribute(wsstr).weight", 0).
            addScore("attribute(wsstr).contains", 0).
            addScore("attribute(wsstr,foo).value", 0).
            addScore("attribute(wsstr,foo).weight", 0).
            addScore("attribute(wsstr,foo).contains", 0).
            addScore("attribute(wsstr,qux).value", (feature_t)vespalib::hash_code("qux")).
            addScore("attribute(wsstr,qux).weight", 11).
            addScore("attribute(wsstr,qux).contains", 1).
            addScore("attribute(wsstr,quux).value", (feature_t)vespalib::hash_code("quux")).
            addScore("attribute(wsstr,quux).weight", 12).
            addScore("attribute(wsstr,quux).contains", 1).
            addScore("attribute(wsint).count", 2).
            addScore("attribute(wsint,40).count", 0).
            addScore("attribute(wsfloat).count", 2).
            addScore("attribute(wsfloat,90.5).count", 0).
            addScore("attribute(wsstr).count", 2).
            addScore("attribute(wsstr,qux).count", 0);

        FtFeatureTest ft(_factory, exp.getKeys());
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint").
            addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsfloat").
            addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsstr");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(exp));
    }
    { // unique only attribute
        RankResult exp;
        exp.addScore("attribute(unique).value", 0).
            addScore("attribute(unique).weight", 0).
            addScore("attribute(unique).contains", 0).
            addScore("attribute(unique).count", 0);

        FtFeatureTest ft(_factory, exp.getKeys());
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.setup());
        //ASSERT_TRUE(ft.execute(exp));
    }
}


void
Test::setupForAttributeTest(FtFeatureTest &ft, bool setup_env)
{
    // setup an original attribute manager with attributes
    std::vector<AttributePtr> avs;
    avs.push_back(AttributeFactory::createAttribute("sint",   AVC(AVBT::INT32,  AVCT::SINGLE))); // 0
    avs.push_back(AttributeFactory::createAttribute("aint",   AVC(AVBT::INT32,  AVCT::ARRAY)));  // 1
    avs.push_back(AttributeFactory::createAttribute("wsint",  AVC(AVBT::INT32,  AVCT::WSET)));    // 2
    avs.push_back(AttributeFactory::createAttribute("sfloat", AVC(AVBT::FLOAT,  AVCT::SINGLE))); // 3
    avs.push_back(AttributeFactory::createAttribute("afloat", AVC(AVBT::FLOAT,  AVCT::ARRAY)));  // 4
    avs.push_back(AttributeFactory::createAttribute("wsfloat",AVC(AVBT::FLOAT,  AVCT::WSET)));    // 5
    avs.push_back(AttributeFactory::createAttribute("sstr",   AVC(AVBT::STRING, AVCT::SINGLE))); // 6
    avs.push_back(AttributeFactory::createAttribute("astr",   AVC(AVBT::STRING, AVCT::ARRAY)));  // 7
    avs.push_back(AttributeFactory::createAttribute("wsstr",  AVC(AVBT::STRING, AVCT::WSET)));    // 8
    avs.push_back(AttributeFactory::createAttribute("udefint", AVC(AVBT::INT32, AVCT::SINGLE)));    // 9
    avs.push_back(AttributeFactory::createAttribute("udeffloat", AVC(AVBT::FLOAT, AVCT::SINGLE)));    // 10
    avs.push_back(AttributeFactory::createAttribute("udefstr", AVC(AVBT::STRING, AVCT::SINGLE)));    // 11

    // simulate a unique only attribute as specified in sd
    AVC cfg(AVBT::INT32, AVCT::SINGLE);
    cfg.setFastSearch(true);
    avs.push_back(AttributeFactory::createAttribute("unique", cfg)); // 9

    if (setup_env) {
        // register attributes in index environment
        ft.getIndexEnv().getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sfloat")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "afloat")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsfloat")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sstr")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "astr")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsstr")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udefint")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udeffloat")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "udefstr")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "unique");
    }

    for (uint32_t i = 0; i < avs.size(); ++i) {
        avs[i]->addReservedDoc();
        avs[i]->addDocs(1);
        ft.getIndexEnv().getAttributeMap().add(avs[i]);
    }

    // integer attributes
    (static_cast<IntegerAttribute *>(avs[0].get()))->update(1, 10);
    (static_cast<IntegerAttribute *>(avs[1].get()))->append(1, 20, 0);
    (static_cast<IntegerAttribute *>(avs[1].get()))->append(1, 30, 0);
    (static_cast<IntegerAttribute *>(avs[2].get()))->append(1, 40, 10);
    (static_cast<IntegerAttribute *>(avs[2].get()))->append(1, 50, 20);
    (static_cast<IntegerAttribute *>(avs[9].get()))->update(1, search::attribute::getUndefined<int32_t>());
    // feature_t attributes
    (static_cast<FloatingPointAttribute *>(avs[3].get()))->update(1, 60.5f);
    (static_cast<FloatingPointAttribute *>(avs[4].get()))->append(1, 70.5f, 0);
    (static_cast<FloatingPointAttribute *>(avs[4].get()))->append(1, 80.5f, 0);
    (static_cast<FloatingPointAttribute *>(avs[5].get()))->append(1, 90.5f, -30);
    (static_cast<FloatingPointAttribute *>(avs[5].get()))->append(1, 100.5f, -40);
    (static_cast<FloatingPointAttribute *>(avs[10].get()))->update(1, search::attribute::getUndefined<float>());
    // string attributes
    (static_cast<StringAttribute *>(avs[6].get()))->update(1, "foo");
    (static_cast<StringAttribute *>(avs[7].get()))->append(1, "bar", 0);
    (static_cast<StringAttribute *>(avs[7].get()))->append(1, "baz", 0);
    (static_cast<StringAttribute *>(avs[8].get()))->append(1, "qux", 11);
    (static_cast<StringAttribute *>(avs[8].get()))->append(1, "quux", 12);
    (static_cast<StringAttribute *>(avs[11].get()))->update(1, "");

    for (uint32_t i = 0; i < avs.size() - 1; ++i) { // do not commit the noupdate attribute
        avs[i]->commit();
    }

    // save 'sint' and load it into 'unique' (only way to set a noupdate attribute)
    ASSERT_TRUE(avs[0]->saveAs(avs[9]->getBaseFileName()));
    ASSERT_TRUE(avs[9]->load());
}

void
Test::testCloseness()
{
    { // Test blueprint.
        ClosenessBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "closeness"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);
        FT_SETUP_OK(pt, params.add("name"), in.add("distance(name)"), out.add("out").add("logscale"));

        FT_DUMP_EMPTY(_factory, "closeness");
    }

    { // Test executor.
        assertCloseness(1,   "pos", 0);
        assertCloseness(0.8, "pos", 1802661);
        assertCloseness(0,   "pos", 9013306);
        // use non-existing attribute -> default distance
        assertCloseness(0, "no", 0);

        // use non-default maxDistance
        assertCloseness(1,   "pos", 0,   100);
        assertCloseness(0.5, "pos", 50,  100);
        assertCloseness(0,   "pos", 100, 100);
        assertCloseness(0,   "pos", 101, 100);

        // test logscale using halfResponse (define that x = 10 should give 0.5 -> s = -10^2/(2*10 - 100) = 1.25 (scale distance))
        assertCloseness(1,   "pos", 0,   100, 10);
        assertCloseness(0.5, "pos", 10,  100, 10);
        assertCloseness(0,   "pos", 100, 100, 10);
        assertCloseness(0,   "pos", 101, 100, 10);
    }
}

void
Test::assertCloseness(feature_t exp, const vespalib::string & attr, double distance, double maxDistance, double halfResponse)
{
    vespalib::string feature = "closeness(" + attr + ")";
    FtFeatureTest ft(_factory, feature);
    std::vector<std::pair<int32_t, int32_t> > positions;
    int32_t x = 0;
    positions.push_back(std::make_pair(x, x));
    setupForDistanceTest(ft, "pos", positions, false);
    ft.getQueryEnv().getLocation().setXPosition((int)distance);
    ft.getQueryEnv().getLocation().setValid(true);
    if (maxDistance > 0) {
        ft.getIndexEnv().getProperties().add(feature + ".maxDistance",
                                             vespalib::make_string("%u", (unsigned int)maxDistance));
    }
    if (halfResponse > 0) {
        ft.getIndexEnv().getProperties().add(feature + ".halfResponse",
                                             vespalib::make_string("%f", halfResponse));
        feature.append(".logscale");
    }
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(RankResult().addScore(feature, exp)));
}

void
Test::testFieldLength()
{
    FieldLengthBlueprint pt;

    { // Test blueprint.
        EXPECT_TRUE(assertCreateInstance(pt, "fieldLength"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);
        FtIndexEnvironment ie;
        ie.getBuilder()
            .addField(FieldType::INDEX, CollectionType::SINGLE, "foo")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar")
            .addField(FieldType::INDEX, CollectionType::ARRAY, "afoo")
            .addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wfoo");
        FT_SETUP_FAIL(pt, params.add("qux")); // does not exists
        FT_SETUP_FAIL(pt, params.clear().add("bar")); // not an index
        FT_SETUP_FAIL(pt, params.clear().add("afoo")); // wrong collection type
        FT_SETUP_FAIL(pt, params.clear().add("wfoo")); // wrong collection type
        FT_SETUP_OK(pt, ie, params.clear().add("foo"), in, out.add("out"));

        FT_DUMP_EMPTY(_factory, "fieldLength");
        FT_DUMP_EMPTY(_factory, "fieldLength", ie);
    }

    { // Test executor.
        for (uint32_t i = 0; i < 10; ++i) {
            StringList features;
            features.add("fieldLength(foo)").add("fieldLength(baz)");
            FtFeatureTest ft(_factory, features);
            ASSERT_TRUE(!ft.setup());

            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo").
                addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar").addField(FieldType::INDEX, CollectionType::SINGLE, "baz");
            ft.getQueryEnv().getBuilder().addAllFields();
            ASSERT_TRUE(ft.setup());

            search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
            ASSERT_TRUE(mdb->addOccurence("foo", 0, i));
            ASSERT_TRUE(mdb->setFieldLength("foo", i + 10));
            ASSERT_TRUE(mdb->addOccurence("baz", 0, i));
            ASSERT_TRUE(mdb->setFieldLength("baz", i + 20));
            ASSERT_TRUE(mdb->apply(1));
            ASSERT_TRUE(ft.execute(RankResult()
                                   .addScore("fieldLength(foo)", (feature_t)i + 10)
                                   .addScore("fieldLength(baz)", (feature_t)i + 20)));
        }
    }
}


void
Test::assertFieldMatch(const vespalib::string & spec,
                       const vespalib::string & query,
                       const vespalib::string & field,
                       const fieldmatch::Params * params,
                       uint32_t totalTermWeight,
                       feature_t totalSignificance)
{
    LOG(info, "assertFieldMatch('%s', '%s', '%s', (%u))", spec.c_str(), query.c_str(), field.c_str(), totalTermWeight);

    // Setup feature test.
    vespalib::string feature = "fieldMatch(foo)";
    FtFeatureTest ft(_factory, feature);

    setupFieldMatch(ft, "foo", query, field, params, totalTermWeight, totalSignificance, 1);

    // Execute and compare results.
    RankResult rr = toRankResult(feature, spec);
    rr.setEpsilon(1e-4); // same as java tests
    ASSERT_TRUE(ft.execute(rr));
}

void
Test::assertFieldMatch(const vespalib::string & spec,
                       const vespalib::string & query,
                       const vespalib::string & field,
                       uint32_t totalTermWeight)
{
    assertFieldMatch(spec, query, field, NULL, totalTermWeight);
}

void
Test::assertFieldMatchTS(const vespalib::string & spec,
                         const vespalib::string & query,
                         const vespalib::string & field,
                         feature_t totalSignificance)
{
    assertFieldMatch(spec, query, field, NULL, 0, totalSignificance);
}


void
Test::testFirstPhase()
{
    { // Test blueprint.
        FirstPhaseBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "firstPhase"));

        FtIndexEnvironment ie;
        ie.getProperties().add(indexproperties::rank::FirstPhase::NAME, "random"); // override nativeRank dependency

        StringList params, in, out;
        FT_SETUP_OK(pt, ie, params, in.add("random"), out.add("score"));
        FT_SETUP_FAIL(pt, params.add("foo"));
        params.clear();

        FT_DUMP(_factory, "firstPhase", ie, StringList().add("firstPhase"));
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "firstPhase");
        ft.getIndexEnv().getProperties().add(indexproperties::rank::FirstPhase::NAME, "value(10)");
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(10.0f));
    }
}

void
Test::testForeach()
{
    { // Test blueprint.
        ForeachBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "foreach"));

        StringList params, in, out;
        out.add("value");
        FT_SETUP_FAIL(pt, params);
        // illegal dimension
        FT_SETUP_FAIL(pt, params.add("squares").add("N").add("foo").add("true").add("sum"));
        // illegal condition
        FT_SETUP_FAIL(pt, params.clear().add("fields").add("N").add("foo").add("false").add("sum"));
        // illegal operation
        FT_SETUP_FAIL(pt, params.clear().add("fields").add("N").add("foo").add("true").add("dotproduct"));

        FtIndexEnvironment ie;
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "baz");

        // various dimensions
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo(N)").add("true").add("sum"),
                    in.clear().add("foo(0)").add("foo(1)").add("foo(2)").add("foo(3)").add("foo(4)").
                               add("foo(5)").add("foo(6)").add("foo(7)").add("foo(8)").add("foo(9)").
                               add("foo(10)").add("foo(11)").add("foo(12)").add("foo(13)").add("foo(14)").add("foo(15)"), out);
        ie.getProperties().add("foreach.maxTerms", "1");
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("sum"),
                    in.clear().add("foo"), out);
        FT_SETUP_OK(pt, ie, params.clear().add("fields").add("N").add("foo(N)").add("true").add("sum"),
                    in.clear().add("foo(foo)").add("foo(bar)"), out);
        FT_SETUP_OK(pt, ie, params.clear().add("attributes").add("N").add("foo(N)").add("true").add("sum"),
                    in.clear().add("foo(baz)"), out);

        // various conditions
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("sum"), in.clear().add("foo"), out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("<4").add("sum"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add(">4").add("sum"), in, out);
        // various operations
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("sum"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("product"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("average"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("max"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("min"), in, out);
        FT_SETUP_OK(pt, ie, params.clear().add("terms").add("N").add("foo").add("true").add("count"), in, out);

        FT_DUMP_EMPTY(_factory, "foreach");
    }
    { // Test executor
        // single loop
        assertForeachOperation( 16.5, "true", "sum");
        assertForeachOperation(-2106, "true", "product");
        assertForeachOperation(  3.3, "true", "average");
        assertForeachOperation(    8, "true", "max");
        assertForeachOperation( -4.5, "true", "min");
        assertForeachOperation(    5, "true", "count");

        assertForeachOperation(3,    "\">4\"", "count");
        assertForeachOperation(2,  "\">4.5\"", "count");
        assertForeachOperation(2,    "\"<4\"", "count");
        assertForeachOperation(2,  "\"<4.5\"", "count");
        assertForeachOperation(4,    "\">0\"", "count");
        assertForeachOperation(1,    "\"<0\"", "count");
        assertForeachOperation(4, "\">-4.5\"", "count");
        assertForeachOperation(1, "\"<-4.4\"", "count");

        { // average without any values
            FtFeatureTest ft(_factory, "foreach(fields,N,value(N),true,average)");
            ASSERT_TRUE(ft.setup());
            ASSERT_TRUE(ft.execute(0));
        }

        { // double loop
            vespalib::string feature =
                "foreach(fields,N,foreach(attributes,M,rankingExpression(\"value(N)+value(M)\"),true,product),true,sum)";
            LOG(info, "double loop feature: '%s'", feature.c_str());
            FtFeatureTest ft(_factory, feature);
            ft.getIndexEnv().getProperties().add("foreach.maxTerms", "1");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "1");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "2");
            ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "3");
            ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "4");
            // ((1 + 3) * (1 + 4)) + ((2 + 3) * (2 + 4)) = 4 * 5 + 5 * 6 = 20 + 30 = 50
            ASSERT_TRUE(ft.setup());
            ASSERT_TRUE(ft.execute(50));
            ASSERT_TRUE(ft.execute(50)); // check that reset works
        }
    }
}

void
Test::assertForeachOperation(feature_t exp, const vespalib::string & cond, const vespalib::string & op)
{
    vespalib::string feature = "foreach(fields,N,value(N)," + cond + "," + op + ")";
    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "4.5");
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "2");
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "8");
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "6.5");
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "-4.5");
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(exp));
    ASSERT_TRUE(ft.execute(exp)); // check that reset works
}


void
Test::testFreshness()
{
    { // Test blueprint.
        FtIndexEnvironment idx_env;
        idx_env.getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "name");

        FreshnessBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "freshness"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, idx_env, params);
        FT_SETUP_OK(pt, idx_env, params.add("name"), in.add("age(name)"), out.add("out").add("logscale"));

        FT_DUMP_EMPTY(_factory, "freshness");
    }

    { // Test executor.
        assertFreshness(1,   "doctime", 0);
        assertFreshness(0.5, "doctime", 3*15*24*60*60);
        assertFreshness(0,   "doctime", 3*30*24*60*60);
        // use non-default maxAge
        assertFreshness(1,    "doctime", 0,   120);
        assertFreshness(0.75, "doctime", 30,  120);
        assertFreshness(0.5,  "doctime", 60,  120);
        assertFreshness(0,    "doctime", 120, 120);
        assertFreshness(0,    "doctime", 121, 120);

        // test logscale
        assertFreshness(1,   "doctime", 0, 0, 0, true);
        assertFreshness(0.5, "doctime", 7*24*60*60, 0, 0, true);
        assertFreshness(0,   "doctime", 3*30*24*60*60, 0, 0, true);
        // use non-default maxAge & halfResponse
        assertFreshness(1,   "doctime", 0,   120, 30, true);
        assertFreshness(0.5, "doctime", 30,  120, 30, true); // half response after 30 secs
        assertFreshness(0,   "doctime", 120, 120, 30, true);
        assertFreshness(0,   "doctime", 121, 120, 30, true);
        // test invalid half response
        assertFreshness(0.5, "doctime", 1, 120, 0.5, true); // half response is set to 1
        assertFreshness(0.5, "doctime", 59, 120, 70, true); // half response is set to 120/2 - 1
    }
}

void
Test::assertFreshness(feature_t expFreshness, const vespalib::string & attr, uint32_t age, uint32_t maxAge, double halfResponse, bool logScale)
{
    vespalib::string feature = "freshness(" + attr + ")";
    FtFeatureTest ft(_factory, feature);
    setupForAgeTest(ft, 60); // time = 60
    if (maxAge > 0) {
        ft.getIndexEnv().getProperties().add("freshness(" + attr + ").maxAge",
                                             vespalib::make_string("%u", maxAge));
    }
    if (halfResponse > 0) {
        ft.getIndexEnv().getProperties().add("freshness(" + attr + ").halfResponse",
                                             vespalib::make_string("%f", halfResponse));
    }
    if (logScale) {
        feature.append(".logscale");
    }
    ft.getQueryEnv().getProperties().add(queryproperties::now::SystemTime::NAME,
                                         vespalib::make_string("%u", age + 60)); // now = age + 60
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(RankResult().addScore(feature, expFreshness).setEpsilon(EPS)));
}

void
Test::testDistance()
{
    { // Test blueprint.
        DistanceBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "distance"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);
        FT_SETUP_OK(pt, params.add("pos"), in, out.add("out"));

        FT_DUMP_EMPTY(_factory, "distance");
    }

    { // Test executor.

        { // test 2D single location (zcurve)
            assert2DZDistance(static_cast<feature_t>(std::sqrt(650.0f)), "5:-5",  10,  20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(250.0f)), "5:-5",  10, -20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(450.0f)), "5:-5", -10, -20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(850.0f)), "5:-5", -10,  20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(250.0f)), "5:-5",  15, -20, 0x80000000); // 2^31
        }

        { // test 2D multi location (zcurve)
            vespalib::string positions = "5:-5,35:0,5:40,35:-40";
            assert2DZDistance(static_cast<feature_t>(std::sqrt(425.0f)), positions,  10,  20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(250.0f)), positions,  10, -20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(450.0f)), positions, -10, -20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(625.0f)), positions, -10,  20);
            assert2DZDistance(static_cast<feature_t>(std::sqrt(250.0f)), positions,  15, -20, 0x80000000); // 2^31
            assert2DZDistance(static_cast<feature_t>(std::sqrt(425.0f)), positions,  45, -20, 0x80000000); // 2^31
        }

        { // test default distance
            { // non-existing attribute
                FtFeatureTest ft(_factory, "distance(pos)");
                ft.getQueryEnv().getLocation().setValid(true);
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(RankResult().addScore("distance(pos)", 6400000000.0)));
            }
            { // wrong attribute type (float)
                FtFeatureTest ft(_factory, "distance(pos)");
                AttributePtr pos = AttributeFactory::createAttribute("pos", AVC(AVBT::FLOAT,  AVCT::SINGLE));
                pos->commit();
                ft.getIndexEnv().getAttributeMap().add(pos);
                ft.getQueryEnv().getLocation().setValid(true);
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(RankResult().addScore("distance(pos)", 6400000000.0)));
            }
            { // wrong attribute type (string)
                FtFeatureTest ft(_factory, "distance(pos)");
                AttributePtr pos = AttributeFactory::createAttribute("pos", AVC(AVBT::STRING,  AVCT::SINGLE));
                pos->commit();
                ft.getIndexEnv().getAttributeMap().add(pos);
                ft.getQueryEnv().getLocation().setValid(true);
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(RankResult().addScore("distance(pos)", 6400000000.0)));
            }
            { // wrong attribute collection type (weighted set)
                FtFeatureTest ft(_factory, "distance(pos)");
                AttributePtr pos = AttributeFactory::createAttribute("pos", AVC(AVBT::INT64,  AVCT::WSET));
                pos->commit();
                ft.getIndexEnv().getAttributeMap().add(pos);
                ft.getQueryEnv().getLocation().setValid(true);
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(RankResult().addScore("distance(pos)", 6400000000.0)));
            }
        }
    }
}

void
Test::setupForDistanceTest(FtFeatureTest &ft, const vespalib::string & attrName,
                           const std::vector<std::pair<int32_t, int32_t> > & positions, bool zcurve)
{
    AttributePtr pos = AttributeFactory::createAttribute(attrName, AVC(AVBT::INT64,  AVCT::ARRAY));

    pos->addReservedDoc();
    pos->addDocs(1);
    ft.getIndexEnv().getAttributeMap().add(pos);

    IntegerAttribute * ia = static_cast<IntegerAttribute *>(pos.get());
    for (uint32_t i = 0; i < positions.size(); ++i) {
        if (zcurve) {
            ia->append(1, vespalib::geo::ZCurve::encode(positions[i].first, positions[i].second), 0);
        } else {
            ia->append(1, positions[i].first, 0);
        }
    }

    pos->commit();
}

void
Test::assert2DZDistance(feature_t exp, const vespalib::string & positions,
                        int32_t xquery, int32_t yquery, uint32_t xAspect)
{
    LOG(info, "assert2DZDistance(%g, %s, %d, %d, %u)", exp, positions.c_str(), xquery, yquery, xAspect);
    FtFeatureTest ft(_factory, "distance(pos)");
    std::vector<vespalib::string> ta = FtUtil::tokenize(positions, ",");
    std::vector<std::pair<int32_t, int32_t> > pos;
    for (uint32_t i = 0; i < ta.size(); ++i) {
        std::vector<vespalib::string> tb = FtUtil::tokenize(ta[i], ":");
        int32_t x = util::strToNum<int32_t>(tb[0]);
        int32_t y = util::strToNum<int32_t>(tb[1]);
        pos.push_back(std::make_pair(x, y));
    }
    setupForDistanceTest(ft, "pos", pos, true);
    ft.getQueryEnv().getLocation().setXPosition(xquery);
    ft.getQueryEnv().getLocation().setYPosition(yquery);
    ft.getQueryEnv().getLocation().setXAspect(xAspect);
    ft.getQueryEnv().getLocation().setValid(true);
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(RankResult().setEpsilon(1e-4).
                           addScore("distance(pos)", exp)));
}

void
Test::testDistanceToPath()
{
    {
        // Test blueprint.
        DistanceToPathBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "distanceToPath"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);
        FT_SETUP_OK(pt, params.add("pos"), in, out.add("distance").add("traveled").add("product"));
        FT_SETUP_FAIL(pt, params.add("foo"));

        FT_DUMP_EMPTY(_factory, "distanceToPath");
    }

    {
        // Test executor.
        std::vector<std::pair<int32_t, int32_t> > pos;
        pos.push_back(std::make_pair(0, 0));

        // invalid path
        assertDistanceToPath(pos, "a");
        assertDistanceToPath(pos, "(");
        assertDistanceToPath(pos, "(a");
        assertDistanceToPath(pos, "(a)");
        assertDistanceToPath(pos, "(-1)");
        assertDistanceToPath(pos, "(-1,1)");
        assertDistanceToPath(pos, "(-1,1,1)");
        assertDistanceToPath(pos, "(-1 1 1 1)");

        // path on either side of document
        assertDistanceToPath(pos, "(-1,1,1,1)", 1, 0.5, 2);
        assertDistanceToPath(pos, "(-1,-1,1,-1)", 1, 0.5, -2);

        // zero length path
        assertDistanceToPath(pos, "(0,0,0,0)", 0, 0);
        assertDistanceToPath(pos, "(0,0,0,0,0,0)", 0, 0);
        assertDistanceToPath(pos, "(0,1,0,1)", 1, 0);
        assertDistanceToPath(pos, "(0,1,0,1,0,1)", 1, 0);

        // path crosses document
        assertDistanceToPath(pos, "(-1,1,1,-1)", 0, 0.5);
        assertDistanceToPath(pos, "(-2,2,2,-2)", 0, 0.5);
        assertDistanceToPath(pos, "(-1,1,3,-3)", 0, 0.25);

        // intersection outside segments
        assertDistanceToPath(pos, "(1,0,2,0)", 1, 0); // before
        assertDistanceToPath(pos, "(0,1,0,2)", 1, 0);
        assertDistanceToPath(pos, "(-2,0,-1,0)", 1, 1); // after
        assertDistanceToPath(pos, "(0,-2,0,-1)", 1, 1);

        // various paths
        assertDistanceToPath(pos, "(-3,1,2,1,2,-2,-2,-2)", 1, 0.25, 5);
        assertDistanceToPath(pos, "(-3,2,2,2,2,-1,0,-1)", 1, 1, 2);

        // multiple document locations
        pos.push_back(std::make_pair(0, 1));
        assertDistanceToPath(pos, "(-1,1,1,1)", 0, 0.5);
        assertDistanceToPath(pos, "(-2,-1,-1,1)", 1, 1, 2);
        assertDistanceToPath(pos, "(-1,0.25,1,0.25)", 0.25, 0.5, 0.5);

        {
            // Test defaults.
            RankResult res;
            res.addScore("distanceToPath(pos).distance", DistanceExecutor::DEFAULT_DISTANCE);
            res.addScore("distanceToPath(pos).traveled", 1);
            {
                // Non-existing attribute.
                FtFeatureTest ft(_factory, "distanceToPath(pos)");
                ft.getQueryEnv().getProperties().add("distanceToPath(pos).path", "0 0 1 1");
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(res));
            }
            {
                // Wrong attribute type (float).
                FtFeatureTest ft(_factory, "distanceToPath(pos)");
                AttributePtr att = AttributeFactory::createAttribute("pos", AVC(AVBT::FLOAT, AVCT::SINGLE));
                att->commit();
                ft.getIndexEnv().getAttributeMap().add(att);
                ft.getQueryEnv().getProperties().add("distanceToPath(pos).path", "0 0 1 1");
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(res));
            }
            {
                // Wrong attribute type (string).
                FtFeatureTest ft(_factory, "distanceToPath(pos)");
                AttributePtr att = AttributeFactory::createAttribute("pos", AVC(AVBT::STRING, AVCT::SINGLE));
                att->commit();
                ft.getIndexEnv().getAttributeMap().add(att);
                ft.getQueryEnv().getProperties().add("distanceToPath(pos).path", "0 0 1 1");
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(res));
            }
            {
                // Wrong attribute collection type (weighted set).
                FtFeatureTest ft(_factory, "distanceToPath(pos)");
                AttributePtr att = AttributeFactory::createAttribute("pos", AVC(AVBT::INT64, AVCT::WSET));
                att->commit();
                ft.getIndexEnv().getAttributeMap().add(att);
                ft.getQueryEnv().getProperties().add("distanceToPath(pos).path", "0 0 1 1");
                ASSERT_TRUE(ft.setup());
                ASSERT_TRUE(ft.execute(res));
            }
        }
    }
}

void
Test::assertDistanceToPath(const std::vector<std::pair<int32_t, int32_t> > pos,
                           const vespalib::string &path, feature_t distance, feature_t traveled, feature_t product)
{
    LOG(info, "Testing distance to path '%s' with %zd document locations.", path.c_str(), pos.size());

    FtFeatureTest ft(_factory, "distanceToPath(pos)");
    setupForDistanceTest(ft, "pos", pos, true);

    ft.getQueryEnv().getProperties().add("distanceToPath(pos).path", path);
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(RankResult()
                           .addScore("distanceToPath(pos).distance", distance)
                           .addScore("distanceToPath(pos).traveled", traveled)
                           .addScore("distanceToPath(pos).product", product)));
}

void
Test::setupForDocumentTest(FtFeatureTest &ft, const vespalib::string & attrName, const vespalib::string & docType)
{
    AttributePtr type = AttributeFactory::createAttribute(attrName, AVC(AVBT::STRING, AVCT::SINGLE));

    type->addReservedDoc();
    type->addDocs(1);
    ft.getIndexEnv().getAttributeMap().add(type);

    (static_cast<StringAttribute *>(type.get()))->update(1, docType);
    type->commit();
}

void
Test::testDotProduct()
{
    { // Test blueprint.
        FtIndexEnvironment idx_env;
        idx_env.getBuilder()
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "attribute");

        DotProductBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "dotProduct"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, idx_env, params);
        FT_SETUP_OK(pt, idx_env, params.add("attribute").add("vector"), in, out.add("scalar"));

        FT_DUMP_EMPTY(_factory, "dotProduct");
    }

    { // Test vector parser
        { // string enum vector
            FtFeatureTest ft(_factory, "value(0)");
            setupForDotProductTest(ft);
            const search::attribute::IAttributeVector * sv(ft.getIndexEnv().getAttributeMap().getAttribute("wsstr"));
            ASSERT_TRUE(sv != nullptr);
            EXPECT_TRUE(sv->hasEnum());
            search::attribute::EnumHandle e;
            {
                dotproduct::wset::EnumVector out(sv);
                WeightedSetParser::parse("", out);
                EXPECT_EQUAL(out.getVector().size(), 0u);
                WeightedSetParser::parse("()", out);
                EXPECT_EQUAL(out.getVector().size(), 0u);
                WeightedSetParser::parse("(a;1)", out);
                EXPECT_EQUAL(out.getVector().size(), 0u);
                WeightedSetParser::parse("(a:1)", out);
                EXPECT_EQUAL(out.getVector().size(), 1u);
                EXPECT_TRUE(sv->findEnum("a", e));
                EXPECT_EQUAL(out.getVector()[0].first, e);
                EXPECT_EQUAL(out.getVector()[0].second, 1.0);
            }
            std::vector<vespalib::string> v = {"(b:2.5,c:-3.5)", "{b:2.5,c:-3.5}"};
            for(const vespalib::string & s : v) {
                dotproduct::wset::EnumVector out(sv);
                WeightedSetParser::parse(s, out);
                EXPECT_EQUAL(out.getVector().size(), 2u);
                EXPECT_TRUE(sv->findEnum("b", e));
                EXPECT_EQUAL(out.getVector()[0].first, e);
                EXPECT_EQUAL(out.getVector()[0].second, 2.5);
                EXPECT_TRUE(sv->findEnum("c", e));
                EXPECT_EQUAL(out.getVector()[1].first, e);
                EXPECT_EQUAL(out.getVector()[1].second, -3.5);
            }
            { // test funky syntax
                dotproduct::wset::EnumVector out(sv);
                WeightedSetParser::parse("( a: 1,  b:2 ,c: , :3)", out);
                EXPECT_EQUAL(out.getVector().size(), 3u);
                EXPECT_TRUE(sv->findEnum("a", e));
                EXPECT_EQUAL(out.getVector()[0].first, e);
                EXPECT_EQUAL(out.getVector()[0].second, 1);
                EXPECT_TRUE(sv->findEnum("b", e));
                EXPECT_EQUAL(out.getVector()[1].first, e);
                EXPECT_EQUAL(out.getVector()[1].second, 2);
                EXPECT_TRUE(sv->findEnum("c", e));
                EXPECT_EQUAL(out.getVector()[2].first, e);
                EXPECT_EQUAL(out.getVector()[2].second, 0);
            }
            { // strings not in attribute vector
                dotproduct::wset::EnumVector out(sv);
                WeightedSetParser::parse("(not:1)", out);
                EXPECT_EQUAL(out.getVector().size(), 0u);
            }
        }
        { // string vector
            dotproduct::wset::StringVector out;
            WeightedSetParser::parse("(b:2.5,c:-3.5)", out);
            EXPECT_EQUAL(out.getVector().size(), 2u);
            EXPECT_EQUAL(out.getVector()[0].first, "b");
            EXPECT_EQUAL(out.getVector()[0].second, 2.5);
            EXPECT_EQUAL(out.getVector()[1].first, "c");
            EXPECT_EQUAL(out.getVector()[1].second, -3.5);
        }
        { // integer vector
            dotproduct::wset::IntegerVector out;
            WeightedSetParser::parse("(20:2.5,30:-3.5)", out);
            EXPECT_EQUAL(out.getVector().size(), 2u);
            EXPECT_EQUAL(out.getVector()[0].first, 20);
            EXPECT_EQUAL(out.getVector()[0].second, 2.5);
            EXPECT_EQUAL(out.getVector()[1].first, 30);
            EXPECT_EQUAL(out.getVector()[1].second, -3.5);
        }
    }
    { // Array parser
        std::vector<vespalib::string> v = {"(0:2,7:-3,1:-3)", "{0:2,7:-3,1:-3}", "[2 -3 0 0 0 0 0 -3]"};
        for(const vespalib::string & s : v) {
            std::vector<int32_t> out;
            ArrayParser::parse(s, out);
            EXPECT_EQUAL(8u, out.size());
            EXPECT_EQUAL(2,  out[0]);
            EXPECT_EQUAL(-3, out[1]);
            EXPECT_EQUAL(0,  out[2]);
            EXPECT_EQUAL(0,  out[3]);
            EXPECT_EQUAL(0,  out[4]);
            EXPECT_EQUAL(0,  out[5]);
            EXPECT_EQUAL(0,  out[6]);
            EXPECT_EQUAL(-3, out[7]);
        }
    }
    {
        vespalib::string s = "[[1:3]]";
        std::vector<int32_t> out;
        ArrayParser::parse(s, out);
        EXPECT_EQUAL(0u, out.size());
    }

    { // Test executor.
        { // string enum attribute
            // docId = 1
            assertDotProduct(0,    "()");
            assertDotProduct(0,    "(f:5)");
            assertDotProduct(0,    "(f:5,g:5)");
            assertDotProduct(-5,   "(a:-5)");
            assertDotProduct(25,   "(e:5)");
            assertDotProduct(-5.5, "(a:-5.5)");
            assertDotProduct(27.5, "(e:5.5)");
            assertDotProduct(55,   "(a:1,b:2,c:3,d:4,e:5)");
            assertDotProduct(20,   "(b:10,b:15)");
            // docId = 2
            assertDotProduct(0, "()", 2);
            assertDotProduct(0, "(a:1,b:2,c:3,d:4,e:5)", 2);
        }
        { // string attribute
            assertDotProduct(0,   "(f:5,g:5)",             1, "wsextstr");
            assertDotProduct(550, "(a:1,b:2,c:3,d:4,e:5)", 1, "wsextstr");
        }
        { // integer attribute
            assertDotProduct(0,  "()",                    1, "wsint");
            assertDotProduct(0,  "(6:5,7:5)",             1, "wsint");
            assertDotProduct(55, "(1:1,2:2,3:3,4:4,5:5)", 1, "wsint");
        }
        std::vector<const char *> attributes = {"arrint", "arrfloat", "arrint_fast", "arrfloat_fast"};
        for (const char * name : attributes) {
            assertDotProduct(0,  "()",                    1, name);
            assertDotProduct(0,  "(6:5,7:5)",             1, name);
            assertDotProduct(55,  "(0:1,1:2,2:3,3:4,4:5)", 1, name);
            assertDotProduct(55,  "[1 2 3 4 5]", 1, name);
            assertDotProduct(41,  "{3:4,4:5}", 1, name);
        }
        { // float array attribute
            assertDotProduct(55,  "[1.0 2.0 3.0 4.0 5.0]", 1, "arrfloat");
            assertDotProduct(41,  "{3:4,4:5.0}", 1, "arrfloat");
        }
        { // Sparse float array attribute.
            assertDotProduct(17, "(0:1,3:4,50:97)", 1, "arrfloat");
        }

        assertDotProduct(0, "(0:1,3:4,50:97)", 1, "sint"); // attribute of the wrong type
        assertDotProduct(17, "(0:1,3:4,50:97)", 1, "sint", "arrfloat"); // attribute override
        assertDotProduct(0, "(0:1,3:4,50:97)", 1, "sint", "arrfloat_non_existing"); // incorrect attribute override
    }
    { // Test that correct executor is created
        FtFeatureTest ft(_factory, "value(0)");
        setupForDotProductTest(ft);
        ft.getQueryEnv().getProperties().add("dotProduct.vector", "(a:1)");
        ParameterList params;
        params.push_back(Parameter(ParameterType::ATTRIBUTE, "wsstr"));
        params.push_back(Parameter(ParameterType::STRING, "vector"));
        DotProductBlueprint bp;
        DummyDependencyHandler deps(bp);
        EXPECT_TRUE(bp.setup(ft.getIndexEnv(), params));
        vespalib::Stash stash;
        FeatureExecutor &exc = bp.createExecutor(ft.getQueryEnv(), stash);
        // check that we have the optimized enum version
        dotproduct::wset::DotProductExecutor<dotproduct::wset::EnumVector, WeightedEnumContent> * myExc =
            dynamic_cast<dotproduct::wset::DotProductExecutor<dotproduct::wset::EnumVector, WeightedEnumContent> *>(&exc);
        EXPECT_TRUE(myExc != nullptr);
        EXPECT_EQUAL(1u, deps.output.size());
    }
}

void
Test::assertDotProduct(feature_t exp, const vespalib::string & vector, uint32_t docId,
                       const vespalib::string & attribute, const vespalib::string & attributeOverride)
{
    RankResult rr;
    rr.addScore("dotProduct(" + attribute + ",vector)", exp);
    FtFeatureTest ft(_factory, rr.getKeys());
    setupForDotProductTest(ft);
    ft.getQueryEnv().getProperties().add("dotProduct.vector", vector);
    if ( ! attributeOverride.empty() ) {
        ft.getQueryEnv().getProperties().add("dotProduct." + attribute + ".override.name", attributeOverride);
    }
    ASSERT_TRUE(ft.setup());
    ASSERT_TRUE(ft.execute(rr, docId));
}

void
Test::setupForDotProductTest(FtFeatureTest & ft)
{
    struct Config {
        const char * name;
        AVBT dataType;
        AVCT collectionType;
        bool fastSearch;
    };
    std::vector<Config> cfgList = { {"wsint", AVBT::INT32, AVCT::WSET, false},
                                    {"arrint", AVBT::INT32, AVCT::ARRAY, false},
                                    {"arrfloat", AVBT::FLOAT, AVCT::ARRAY, false},
                                    {"arrint_fast", AVBT::INT32, AVCT::ARRAY, true},
                                    {"arrfloat_fast", AVBT::FLOAT, AVCT::ARRAY, true}
                                  };
    AttributePtr a = AttributeFactory::createAttribute("wsstr", AVC(AVBT::STRING, AVCT::WSET));
    AttributePtr c = AttributeFactory::createAttribute("sint", AVC(AVBT::INT32, AVCT::SINGLE));
    AttributePtr d(new search::WeightedSetStringExtAttribute("wsextstr"));
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsstr");
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint");
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsextstr");
    for (const Config & cfg : cfgList) {
        AttributePtr baf = AttributeFactory::createAttribute(cfg.name, AVC(cfg.dataType,
                                                                           cfg.collectionType,
                                                                           cfg.fastSearch));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE,
                                               cfg.collectionType==AVCT::ARRAY
                                                   ? CollectionType::ARRAY
                                                   : CollectionType::WEIGHTEDSET,
                                               cfg.name);
        baf->addReservedDoc();
        baf->addDocs(2);
        ft.getIndexEnv().getAttributeMap().add(baf);
        for (size_t i(1); i < 6; i++) {
            IntegerAttribute * ia = dynamic_cast<IntegerAttribute *>(baf.get());
            if (ia) {
                ia->append(1, i, i);
            } else {
                FloatingPointAttribute * fa = dynamic_cast<FloatingPointAttribute *>(baf.get());
                fa->append(1, i, i);
            }
        }
        baf->commit();
    }

    a->addReservedDoc();
    c->addReservedDoc();
    a->addDocs(2);
    c->addDocs(2);
    ft.getIndexEnv().getAttributeMap().add(a);
    ft.getIndexEnv().getAttributeMap().add(c);
    ft.getIndexEnv().getAttributeMap().add(d);

    StringAttribute * sa = static_cast<StringAttribute *>(a.get());
    sa->append(1, "a", 1);
    sa->append(1, "b", 2);
    sa->append(1, "c", 3);
    sa->append(1, "d", 4);
    sa->append(1, "e", 5);

    WeightedSetStringExtAttribute * ea = static_cast<WeightedSetStringExtAttribute *>(d.get());
    EXPECT_TRUE(!ea->hasEnum());
    uint32_t docId;
    ea->addDoc(docId);  // reserved doc
    ea->addDoc(docId);
    ea->add("a", 10);
    ea->add("b", 20);
    ea->add("c", 30);
    ea->add("d", 40);
    ea->add("e", 50);
    ea->addDoc(docId);

    a->commit();
    c->commit();
}

void
Test::testNow()
{
    {
        // Test blueprint.
        NowBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "now"));

        StringList params, in, out;
        FT_SETUP_OK  (pt, params, in, out.add("out"));
        FT_SETUP_FAIL(pt, params.add("foo"));

        FT_DUMP(_factory, "now", StringList().add("now"));
    }

    {
        // Test executor.
        FtFeatureTest ft(_factory, "now");
        ASSERT_TRUE(ft.setup());

        RankResult res;
        res.addScore("now", 0.0f);
        for (uint32_t i = 1; i <= 10; ++i) {
            feature_t last = res.getScore("now");
            res.clear();
            ASSERT_TRUE(ft.executeOnly(res, i));
            ASSERT_TRUE(last <= res.getScore("now"));
        }
    }

    {
        // Test executor with ms resolution
        FtFeatureTest ft(_factory, "now");
        ft.getQueryEnv().getProperties().add("vespa.now", "15000000000");
        ASSERT_TRUE(ft.setup());

        RankResult res;
        ASSERT_TRUE(ft.executeOnly(res, 1));
        feature_t now = 15000000000;
        ASSERT_EQUAL(now, res.getScore("now"));
    }
}


void
Test::testMatch()
{
    { // Test blueprint.
        MatchBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "match"));

        FtFeatureTest ft(_factory, "");
        setupForAttributeTest(ft);

         ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
         ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "bar");
         ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "baz");
         ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint");
         ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint");
         ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint");

        FtIndexEnvironment idx_env;
        idx_env.getBuilder()
            .addField(FieldType::INDEX, CollectionType::SINGLE, "foo")
            .addField(FieldType::INDEX, CollectionType::ARRAY, "bar")
            .addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "baz")
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint")
            .addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint");

        StringList params, in, out;
        FT_SETUP_OK(pt, params, in, out.add("score").add("totalWeight"));
        FT_SETUP_OK(pt, idx_env, params, in
                    .add("fieldMatch(foo)")
                    .add("elementCompleteness(bar)")
                    .add("elementCompleteness(baz)")
                    .add("attributeMatch(sint)")
                    .add("attributeMatch(aint)")
                    .add("attributeMatch(wsint)"), out
                    .add("weight.foo")
                    .add("weight.bar")
                    .add("weight.baz")
                    .add("weight.sint")
                    .add("weight.aint")
                    .add("weight.wsint"));
        FT_SETUP_FAIL(pt, idx_env, params.add("1")); // expects 0 parameters

        FT_DUMP_EMPTY(_factory, "match");
    }

    { // Test executor
        FtFeatureTest ft(_factory, "match");

        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::ARRAY, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "baz");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint");

        ft.getIndexEnv().getProperties().add("vespa.fieldweight.foo", "100"); // assign weight to all fields, simulate sd behaviour
        ft.getIndexEnv().getProperties().add("vespa.fieldweight.bar", "200");
        ft.getIndexEnv().getProperties().add("vespa.fieldweight.sint", "300");
        ft.getIndexEnv().getProperties().add("vespa.fieldweight.aint", "400");

        // search in field 'foo'
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // term id 0

        // search in field 'sint'
        ft.getQueryEnv().getBuilder().addAttributeNode("sint"); // term id 1
        setupForAttributeTest(ft, false);

        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

        // add hit for field 'foo' for search term 0
        ASSERT_TRUE(mdb->setFieldLength("foo", 1));
        ASSERT_TRUE(mdb->addOccurence("foo", 0, 0));
        ASSERT_TRUE(mdb->setWeight("sint", 1, 0));
        ASSERT_TRUE(mdb->apply(1));

        RankResult rr = toRankResult("match", "score:1 totalWeight:400 weight.foo:100 weight.bar:200 weight.baz:100 weight.sint:300 weight.aint:400 weight.wsint:100");
        rr.setEpsilon(1e-4); // same as java tests
        ASSERT_TRUE(ft.execute(rr));
    }

    { // Test executor
        FtFeatureTest ft(_factory, "match");

        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        // search in field 'foo'
        ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")); // term id 0
        ASSERT_TRUE(ft.setup());

        // must create this so that term match data is configured with the term data object
        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();

        // no hits on docId 1
        RankResult rr = toRankResult("match", "score:0 totalWeight:0 weight.foo:100");
        ASSERT_TRUE(ft.execute(rr, 1));
    }
}

void
Test::testMatchCount()
{
    { // Test blueprint.
        MatchCountBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "matchCount"));

        FtFeatureTest ft(_factory, "");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");

        StringList params, in, out;
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // expects 1 parameter
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params.add("baz")); // cannot find the field
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("foo"), in, out.add("out"));
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("bar"), in, out);

        FT_DUMP_EMPTY(_factory, "matchCount");
    }
    { // Test executor for index fields
        EXPECT_TRUE(assertMatches(0, "x", "a", "matchCount(foo)"));
        EXPECT_TRUE(assertMatches(1, "a", "a", "matchCount(foo)"));
        EXPECT_TRUE(assertMatches(2, "a b", "a b", "matchCount(foo)"));
        // change docId to indicate no matches in the field
        EXPECT_TRUE(assertMatches(0, "a", "a", "matchCount(foo)", 2));
    }
    { // Test executor for attribute fields
        FtFeatureTest ft(_factory, StringList().add("matchCount(foo)").
                                                add("matchCount(baz)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "baz");
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("foo") != NULL);   // query term 0, hit in foo
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("bar") != NULL);   // query term 1, hit in bar
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("foo") != NULL);   // query term 2, hit in foo
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        mdb->setWeight("foo", 0, 0);
        mdb->setWeight("bar", 1, 0);
        mdb->setWeight("foo", 2, 0);
        mdb->apply(1);
        EXPECT_TRUE(ft.execute(RankResult().addScore("matchCount(foo)", 2)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matchCount(baz)", 0)));
    }
}

void
Test::testMatches()
{
    { // Test blueprint.
        MatchesBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "matches"));

        FtFeatureTest ft(_factory, "");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");

        StringList params, in, out;
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // expects 1-2 parameters
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params.add("baz")); // cannot find the field
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("foo"), in, out.add("out"));
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("1"), in, out);
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("bar"), in, out);
        FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("1"), in, out);

        FT_DUMP_EMPTY(_factory, "matches");
    }
    { // Test executor for index fields
        EXPECT_TRUE(assertMatches(0, "x", "a", "matches(foo)"));
        EXPECT_TRUE(assertMatches(1, "a", "a", "matches(foo)"));
        EXPECT_TRUE(assertMatches(1, "a b", "a b", "matches(foo)"));
        // change docId to indicate no matches in the field
        EXPECT_TRUE(assertMatches(0, "a", "a", "matches(foo)", 2));
        // specify termIdx as second parameter
        EXPECT_TRUE(assertMatches(0, "x", "a", "matches(foo,0)"));
        EXPECT_TRUE(assertMatches(1, "a", "a", "matches(foo,0)"));
        EXPECT_TRUE(assertMatches(0, "a", "a", "matches(foo,1)"));
        EXPECT_TRUE(assertMatches(0, "x b", "a b", "matches(foo,0)"));
        EXPECT_TRUE(assertMatches(1, "x b", "a b", "matches(foo,1)"));
    }
    { // Test executor for attribute fields
        FtFeatureTest ft(_factory, StringList().add("matches(foo)").
                                                add("matches(baz)").
                                                add("matches(foo,0)").
                                                add("matches(foo,1)").
                                                add("matches(foo,2)").
                                                add("matches(foo,3)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "baz");
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("foo") != NULL);   // query term 0, hit in foo
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("bar") != NULL);   // query term 1, hit in bar
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("foo") != NULL);   // query term 2, hit in foo
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        mdb->setWeight("foo", 0, 0);
        mdb->setWeight("bar", 1, 0);
        mdb->apply(1);
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(foo)", 1)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(baz)", 0)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(foo,0)", 1)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(foo,1)", 0)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(foo,2)", 0)));
        EXPECT_TRUE(ft.execute(RankResult().addScore("matches(foo,3)", 0)));
    }
}

bool
Test::assertMatches(uint32_t output,
                    const vespalib::string & query,
                    const vespalib::string & field,
                    const vespalib::string & feature,
                    uint32_t docId)
{
    LOG(info, "assertMatches(%u, '%s', '%s', '%s')", output, query.c_str(), field.c_str(), feature.c_str());

    // Setup feature test.
    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    std::map<vespalib::string, std::vector<vespalib::string> > index;
    index["foo"] = FtUtil::tokenize(field);
    FT_SETUP(ft, FtUtil::toQuery(query), index, 1);

    ASSERT_TRUE(ft.execute(output, EPS, docId));
    // Execute and compare results.
    if (!EXPECT_TRUE(ft.execute(output, EPS, docId))) return false;
    return true;
}


void
Test::testQuery()
{
    { // Test blueprint.
        QueryBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "query"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);
        FT_SETUP_OK(pt, params.add("foo"), in, out.add("out"));

        FT_DUMP_EMPTY(_factory, "query");
    }

    { // Test executor.
        RankResult exp;
        exp.addScore("query(def1)", 1.0).
            addScore("query(def2)", 2.0).
            addScore("query(def3)", 0.0).
            addScore("query(val1)", 1.1).
            addScore("query(val2)", 2.2).
            addScore("query(hash1)", vespalib::hash_code("foo")).
            addScore("query(hash2)", vespalib::hash_code("2")).
            addScore("query(hash3)", vespalib::hash_code("foo")).
            addScore("query(hash4)", vespalib::hash_code("'foo"));
        FtFeatureTest ft(_factory, exp.getKeys());
        ft.getIndexEnv().getProperties()
            .add("query(def1)", "1.0")
            .add("$def2", "2.0");
        ft.getQueryEnv().getProperties()
            .add("val1", "1.1")
            .add("$val2", "2.2")
            .add("hash1", "foo")
            .add("hash2", "'2")
            .add("hash3", "'foo")
            .add("hash4", "''foo");
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(exp));
    }
}

void
Test::testQueryTermCount()
{
    { // Test blueprint.
        QueryTermCountBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "queryTermCount"));

        StringList params, in, out;
        FT_SETUP_OK(pt, params, in, out.add("out"));
        FT_SETUP_FAIL(pt, params.add("foo"));

        StringList dump;
        FT_DUMP(_factory, "queryTermCount", dump.add("queryTermCount"));
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "queryTermCount");
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(RankResult().addScore("queryTermCount", 0)));
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "queryTermCount");
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(RankResult().addScore("queryTermCount", 1)));
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "queryTermCount");
        ft.getQueryEnv().getBuilder().addAllFields();
        ft.getQueryEnv().getBuilder().addAllFields();
        ASSERT_TRUE(ft.setup());
        ASSERT_TRUE(ft.execute(RankResult().addScore("queryTermCount", 2)));
    }
}

void
Test::testRandom()
{
    { // Test blueprint.
        RandomBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "random"));

        StringList params, in, out;
        FT_SETUP_OK  (pt, params, in, out.add("out").add("match"));
        FT_SETUP_OK  (pt, params.add("1"), in, out);
        FT_SETUP_FAIL(pt, params.add("2"));

        FT_DUMP_EMPTY(_factory, "random");
    }

    { // Test executor (seed specified through config)
        FtFeatureTest ft(_factory, "random");
        ft.getIndexEnv().getProperties().add("random.seed", "100");
        ASSERT_TRUE(ft.setup());
        search::Rand48 rnd;
        rnd.srand48(100);
        for (uint32_t i = 0; i < 5; ++i) {
            feature_t exp = rnd.lrand48() / (feature_t)0x80000000u;
            ASSERT_TRUE(ft.execute(exp, EPS, i + 1));
        }
    }
    { // Test executor (current time used as seed)
        FtFeatureTest ft(_factory, "random");
        ASSERT_TRUE(ft.setup());
        RankResult rr;
        rr.addScore("random", 1.0f);
        for (uint32_t i = 0; i < 5; ++i) {
            feature_t last = rr.getScore("random");
            rr.clear();
            ASSERT_TRUE(ft.executeOnly(rr, i + 1));
            ASSERT_TRUE(last != rr.getScore("random"));
        }
    }
    { // Test executor (random.match)
        FtFeatureTest ft(_factory, "random.match");
        ft.getQueryEnv().getProperties().add("random.match.seed", "100");
        ASSERT_TRUE(ft.setup());
        search::Rand48 rnd;
        for (uint32_t i = 1; i <= 5; ++i) {
            rnd.srand48(100 + i); // seed + lid
            feature_t exp = rnd.lrand48() / (feature_t)0x80000000u;
            ASSERT_TRUE(ft.execute(exp, EPS, i));
        }
    }
}

void
Test::testRandomNormal()
{
    { // Test blueprint.
        RandomNormalBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "randomNormal"));

        StringList params, in, out;
        FT_SETUP_OK (pt, params, in, out.add("out").add("match"));
        FT_SETUP_OK (pt, params.add("0.5").add("1.0"), in, out);
        FT_SETUP_OK (pt, params.add("val1"), in, out);

        FT_DUMP_EMPTY(_factory, "randomNormal");
    }

    { // Test executor (current time used as seed)
        FtFeatureTest ft(_factory, "randomNormal");
        ASSERT_TRUE(ft.setup());
        RankResult rr;
        rr.addScore("randomNormal", 1000.0);
        for (uint32_t i = 0; i < 5; ++i) {
            feature_t last = rr.getScore("randomNormal");
            rr.clear();
            ASSERT_TRUE(ft.executeOnly(rr, i + 1));
            ASSERT_TRUE(last != rr.getScore("randomNormal"));
        }
    }

    { // Test setting of mean and stddev values, and seed
        FtFeatureTest ft1(_factory, "randomNormal(0.0,0.1)");
        FtFeatureTest ft2(_factory, "randomNormal(1.0,0.2)");
        ft1.getIndexEnv().getProperties().add("randomNormal(0.0,0.1).seed", "100");
        ft2.getIndexEnv().getProperties().add("randomNormal(1.0,0.2).seed", "100");
        ASSERT_TRUE(ft1.setup());
        ASSERT_TRUE(ft2.setup());
        RankResult rr;
        for (uint32_t i = 0; i < 5; ++i) {
            rr.clear();
            ASSERT_TRUE(ft1.executeOnly(rr, i + 1));
            ASSERT_TRUE(ft2.execute(((rr.getScore("randomNormal(0.0,0.1)")-0.0)/0.1) * 0.2 + 1.0, EPS, i + 1));
        }
    }
    { // Test executor (randomNormal.match)
        FtFeatureTest ft1(_factory, "randomNormal.match");
        FtFeatureTest ft2(_factory, "randomNormal.match");
        ASSERT_TRUE(ft1.setup());
        ASSERT_TRUE(ft2.setup());
        RankResult rr1;
        RankResult rr2;
        for (uint32_t i = 0; i < 5; ++i) {
            rr1.clear();
            rr2.clear();
            ASSERT_TRUE(ft1.executeOnly(rr1, i + 1));
            ASSERT_TRUE(ft2.executeOnly(rr2, i + 1));

            feature_t rn1 = rr1.getScore("randomNormal");
            feature_t rn2 = rr2.getScore("randomNormal");
            ASSERT_NOT_EQUAL(rn1, rn2);

            feature_t rnm1 = rr1.getScore("randomNormal.match");
            feature_t rnm2 = rr2.getScore("randomNormal.match");
            ASSERT_EQUAL(rnm1, rnm2);
        }
    }

}

void
Test::testRankingExpression()
{
    { // Test blueprint.
        RankingExpressionBlueprint prototype;

        EXPECT_TRUE(assertCreateInstance(prototype, "rankingExpression"));

        StringList params, in, out;
        FT_SETUP_FAIL(prototype, params); // requires config to run without params
        FT_SETUP_OK  (prototype, params.add("foo.out"), in.add("foo.out"), out.add("out"));
        FT_SETUP_FAIL(prototype, params.add("bar.out"));
        FT_SETUP_OK  (prototype, params.clear().add("log((1 + 2)- 3 * 4 / 5 )"), in.clear(), out);
        FT_SETUP_OK  (prototype,
                      params.clear().add("if(if(f1.out<1,0,1)<if(f2.out<2,0,1),f3.out,3)"),
                      in.clear().add("f1.out").add("f2.out").add("f3.out"), out);

        FT_DUMP_EMPTY(_factory, "rankingExpression");
    }

    { // Test executor.
        {
            FtFeatureTest ft(_factory, getExpression("if(1<2,3,4)"));
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(3.0f));
        }
        {
            FtFeatureTest ft(_factory, getExpression("sqrt(100)"));
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(10.0f));
        }
        {
            FtFeatureTest ft(_factory, getExpression("mysum(value(4),value(4))"));
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(8.0f));
        }
        {
            FtFeatureTest ft(_factory, getExpression("if(mysum(value(4),value(4))>3+4,1,0)"));
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(1.0f));
        }
        {
            FtFeatureTest ft(_factory, "rankingExpression");
            ft.getIndexEnv().getProperties().add("rankingExpression.rankingScript", "if(1<2,3,4)");
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(3.0f));
        }
        {
            FtFeatureTest ft(_factory, "rankingExpression(foo)");
            ft.getIndexEnv().getProperties().add("rankingExpression(foo).rankingScript", "if(1<2,3,4)");
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(3.0f));
        }
        {
            FtFeatureTest ft(_factory, "rankingExpression");
            ft.getIndexEnv().getProperties()
                .add("rankingExpression.rankingScript", "if(")
                .add("rankingExpression.rankingScript", "1<")
                .add("rankingExpression.rankingScript", "2,")
                .add("rankingExpression.rankingScript", "3,")
                .add("rankingExpression.rankingScript", "4)");
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(3.0f));
        }
        {
            // test interpreted expression
            vespalib::string my_expr("3.0 + value(4.0) + reduce(tensorFromWeightedSet(query(my_tensor)),sum)");
            FtFeatureTest ft(_factory, getExpression(my_expr));
            ft.getQueryEnv().getProperties().add("my_tensor", "{a:1,b:2,c:3}");
            ASSERT_TRUE(ft.setup());
            EXPECT_TRUE(ft.execute(13.0));
        }
    }
}

vespalib::string
Test::getExpression(const vespalib::string &parameter) const
{
    typedef search::fef::FeatureNameBuilder FNB;
    return FNB().baseName("rankingExpression").parameter(parameter).buildName();
}

void
Test::testTerm()
{
    {
        // Test blueprint.
        TermBlueprint pt;
        {
            EXPECT_TRUE(assertCreateInstance(pt, "term"));

            StringList params, in, out;
            FT_SETUP_OK  (pt, params.add("0"), in, out.add("connectedness").add("significance").add("weight"));
            FT_SETUP_FAIL(pt, params.add("1"));
        }
        {
            StringList dump;
            for (uint32_t term = 0; term < 3; ++term) {
                vespalib::string bn = vespalib::make_string("term(%u)", term);
                dump.add(bn + ".connectedness").add(bn + ".significance").add(bn + ".weight");
            }
            FtIndexEnvironment ie;
            ie.getProperties().add("term.numTerms", "3");
            FT_DUMP(_factory, "term", ie, dump); // check override

            for (uint32_t term = 3; term < 5; ++term) {
                vespalib::string bn = vespalib::make_string("term(%u)", term);
                dump.add(bn + ".connectedness").add(bn + ".significance").add(bn + ".weight");
            }
            FT_DUMP(_factory, "term", dump); // check default
        }
    }

    {
        // Test executor.
        FtFeatureTest ft(_factory, "term(0)");
        ASSERT_TRUE(ft.setup());

        RankResult exp;
        exp .addScore("term(0).connectedness", 0)
            .addScore("term(0).significance",  0)
            .addScore("term(0).weight",        0);
        ASSERT_TRUE(ft.execute(exp));
    }
    {
        // Test executor.
        FtFeatureTest ft(_factory, StringList().add("term(1)").add("term(2)"));
        ft.getIndexEnv().getBuilder()
            .addField(FieldType::INDEX, CollectionType::SINGLE,     "idx1")  // field 0
            .addField(FieldType::INDEX, CollectionType::SINGLE,     "idx2")  // field 1
            .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "attr"); // field 2
        ft.getQueryEnv().getBuilder().addAllFields().setUniqueId(0);
        ft.getQueryEnv().getBuilder().addAllFields().setUniqueId(1).setWeight(search::query::Weight(200)).lookupField(0)->setDocFreq(0.5);
        ft.getQueryEnv().getBuilder().addAttributeNode("attr")->setUniqueId(2).setWeight(search::query::Weight(400)).lookupField(2)->setDocFreq(0.25);
        // setup connectedness between term 1 and term 0
        ft.getQueryEnv().getProperties().add("vespa.term.1.connexity", "0");
        ft.getQueryEnv().getProperties().add("vespa.term.1.connexity", "0.7");
        ASSERT_TRUE(ft.setup());

        RankResult exp;
        exp.addScore("term(1).significance",  util::getSignificance(0.50)).
            addScore("term(1).weight",        200.0f).
            addScore("term(1).connectedness", 0.7f).
            addScore("term(2).significance",  util::getSignificance(0.25)).
            addScore("term(2).weight",        400.0f).
            addScore("term(2).connectedness", 0.1f). // default connectedness
            setEpsilon(10e-6);
        ASSERT_TRUE(ft.execute(exp));
    }
    {
        // Test executor.
        FtFeatureTest ft(_factory, "term(0)");
        ft.getQueryEnv().getBuilder().addAllFields().setUniqueId(0);
        // setup significance for term 0
        ft.getQueryEnv().getProperties().add("vespa.term.0.significance", "0.3");
        ASSERT_TRUE(ft.setup());

        ASSERT_TRUE(ft.execute(RankResult().addScore("term(0).significance", 0.3f).setEpsilon(10e-6)));
    }
}

void
Test::testTermDistance()
{
    { // test blueprint
        TermDistanceBlueprint pt;
        {
            EXPECT_TRUE(assertCreateInstance(pt, "termDistance"));

            StringList params, in, out;
            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar");
            FT_SETUP_FAIL(pt, params);
            FT_SETUP_FAIL(pt, ie, params.add("baz").add("0").add("0"));
            FT_SETUP_FAIL(pt, ie, params.clear().add("bar").add("0").add("0"));

            FT_SETUP_OK(pt, ie, params.clear().add("foo").add("0").add("0"),
                        in, out.add("forward").add("forwardTermPosition")
                               .add("reverse").add("reverseTermPosition"));
        }
        {
            FT_DUMP_EMPTY(_factory, "termDistance");
        }
    }

    { // test executor
        typedef TermDistanceCalculator::Result Result;
        const uint32_t UV = TermDistanceCalculator::UNDEFINED_VALUE;

        EXPECT_TRUE(assertTermDistance(Result(), "a b", "x x"));
        EXPECT_TRUE(assertTermDistance(Result(), "a b", "a x"));
        EXPECT_TRUE(assertTermDistance(Result(), "a b", "x b"));
        EXPECT_TRUE(assertTermDistance(Result(), "a",   "a b"));
        EXPECT_TRUE(assertTermDistance(Result(), "a",   "a a"));
        EXPECT_TRUE(assertTermDistance(Result(1,0,UV,UV), "a b", "a b"));
        EXPECT_TRUE(assertTermDistance(Result(2,0,UV,UV), "a b", "a x b"));
        EXPECT_TRUE(assertTermDistance(Result(UV,UV,1,0), "a b", "b a"));
        EXPECT_TRUE(assertTermDistance(Result(UV,UV,2,0), "a b", "b x a"));
        EXPECT_TRUE(assertTermDistance(Result(2,18,1,20), "a b", "a x x x x x b x x x x a x x x b x x a x b a"));
        EXPECT_TRUE(assertTermDistance(Result(1,0,2,1),   "a b", "a b x a x x b x x x a x x x x b x x x x x a"));
        EXPECT_TRUE(assertTermDistance(Result(1,0,1,1),   "a b", "a b a b a")); // first best is kept
        EXPECT_TRUE(assertTermDistance(Result(1,0,1,0), "a a", "a a"));
        EXPECT_TRUE(assertTermDistance(Result(2,0,2,0), "a a", "a x a"));
    }
}

bool
Test::assertTermDistance(const TermDistanceCalculator::Result & exp,
                         const vespalib::string & query,
                         const vespalib::string & field,
                         uint32_t docId)
{
    LOG(info, "assertTermDistance('%s', '%s')", query.c_str(), field.c_str());

    vespalib::string feature = "termDistance(foo,0,1)";
    FtFeatureTest ft(_factory, feature);

    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    StringVectorMap index;
    index["foo"] = FtUtil::tokenize(field);
    FT_SETUP(ft, FtUtil::toQuery(query), index, 1);

    RankResult rr;
    rr.addScore(feature + ".forward",             exp.forwardDist);
    rr.addScore(feature + ".forwardTermPosition", exp.forwardTermPos);
    rr.addScore(feature + ".reverse",             exp.reverseDist);
    rr.addScore(feature + ".reverseTermPosition", exp.reverseTermPos);
    if (!EXPECT_TRUE(ft.execute(rr, docId))) {
        return false;
    }
    return true;
}

void
Test::testUtils()
{
    { // getSignificance
        EXPECT_APPROX(util::getSignificance(0.0), 1, EPS);
        EXPECT_APPROX(util::getSignificance(0.0 + 1.0e-7), 1, EPS);
        EXPECT_APPROX(util::getSignificance(1.0), 0.5, EPS);
        EXPECT_APPROX(util::getSignificance(1.0 + 1.0e-7), 0.5, EPS);
        feature_t last = 1;
        for (uint32_t i = 2; i <= 100; i = i + 1) {
            feature_t s = util::getSignificance(i * 1.0e-6);
            EXPECT_GREATER(s, 0);
            EXPECT_LESS(s, 1);
            EXPECT_LESS(s, last);
            last = s;
        }
        for (uint32_t i = 999900; i <= 1000000; i = i + 1) {
            feature_t s = util::getSignificance(i * 1.0e-6);
            EXPECT_GREATER(s, 0);
            EXPECT_LESS(s, 1);
            EXPECT_LESS(s, last);
            last = s;
        }
    }
}

