// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/nativeattributematchfeature.h>
#include <vespa/searchlib/features/nativefieldmatchfeature.h>
#include <vespa/searchlib/features/nativeproximityfeature.h>
#include <vespa/searchlib/features/nativerankfeature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/features/utils.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/searchlib/fef/test/plugin/setup.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>

#include <vespa/log/log.h>
LOG_SETUP("nativerank_test");

using namespace search::fef;
using namespace search::fef::test;
using CollectionType = FieldInfo::CollectionType;

const double EPS = 10e-4;

namespace search {
namespace features {

class Test : public FtTestApp {
private:
    BlueprintFactory _factory;

    struct ANAM {
        int32_t  attributeWeight;
        search::query::Weight termWeight;
        uint32_t fieldWeight;
        uint32_t docId;
        ANAM(int32_t aw, uint32_t tw = 100, uint32_t fw = 100, uint32_t id = 1) :
            attributeWeight(aw), termWeight(tw), fieldWeight(fw), docId(id) {}
        vespalib::string toString() const {
            return vespalib::make_string("aw(%d), tw(%u), fw(%u), id(%u)",
                                         attributeWeight, termWeight.percent(), fieldWeight, docId);
        }
    };

    bool assertNativeFieldMatch(feature_t score, const vespalib::string & query, const vespalib::string & field,
                                const Properties & props = Properties(), uint32_t docId = 1);
    bool assertNativeAttributeMatch(feature_t score, const ANAM & t1, const ANAM & t2,
                                    const Properties & props = Properties());
    bool assertNativeProximity(feature_t score, const vespalib::string & query, const vespalib::string & field,
                               const Properties & props = Properties(), uint32_t docId = 1);
    bool assertNativeRank(feature_t score, feature_t fieldMatchWeight, feature_t attributeMatchWeight, feature_t proximityWeight);

    void testNativeFieldMatch();
    void testNativeAttributeMatch();
    void testNativeProximity();
    void testNativeRank();

public:
    ~Test();
    int Main() override;
};

Test::~Test() {}

void
Test::testNativeFieldMatch()
{
    { // test blueprint
        NativeFieldMatchBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "nativeFieldMatch"));

        FtFeatureTest ft(_factory, "");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "qux");
        ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(16)));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params.add("baz")); // field 'baz' not found
        params.clear();

        Properties & p = ft.getIndexEnv().getProperties();
        p.add("nativeFieldMatch.firstOccurrenceTable", "a");
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // table 'a' not found
        p.clear().add("nativeFieldMatch.occurrenceCountTable", "b");
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // table 'b' not found

        const TableManager & tm = ft.getIndexEnv().getTableManager();
        {
            p.clear();
            p.add("nativeRank.useTableNormalization", "false");
            FT_SETUP_OK(pt, params, in, out.add("score"));
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeFieldMatchParams & pas = (dynamic_cast<NativeFieldMatchBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 3);
            EXPECT_TRUE(pas.vector[0].firstOccTable == tm.getTable("expdecay(8000,12.50)"));
            EXPECT_TRUE(pas.vector[1].firstOccTable == tm.getTable("expdecay(8000,12.50)"));
            EXPECT_TRUE(pas.vector[0].numOccTable == tm.getTable("loggrowth(1500,4000,19)"));
            EXPECT_TRUE(pas.vector[1].numOccTable == tm.getTable("loggrowth(1500,4000,19)"));
            EXPECT_EQUAL(pas.vector[0].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[1].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, true);
            EXPECT_EQUAL(pas.vector[2].field, false);
            EXPECT_EQUAL(pas.vector[0].averageFieldLength, NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH);
            EXPECT_EQUAL(pas.vector[1].averageFieldLength, NativeFieldMatchParam::NOT_DEF_FIELD_LENGTH);
            EXPECT_EQUAL(pas.minFieldLength, 6u);
            EXPECT_EQUAL(pas.vector[0].firstOccImportance, 0.5);
            EXPECT_EQUAL(pas.vector[1].firstOccImportance, 0.5);
        }
        {
            p.clear();
            p.add("nativeFieldMatch.firstOccurrenceTable",     "linear(0,1)");
            p.add("nativeFieldMatch.firstOccurrenceTable.foo", "linear(0,2)");
            p.add("nativeFieldMatch.occurrenceCountTable",     "linear(0,3)");
            p.add("nativeFieldMatch.occurrenceCountTable.baz", "linear(0,4)");
            p.add("vespa.fieldweight.foo", "200");
            p.add("vespa.fieldweight.baz", "0");
            p.add("nativeFieldMatch.averageFieldLength.foo", "400");
            p.add("nativeFieldMatch.averageFieldLength.baz", "500");
            p.add("nativeFieldMatch.minFieldLength",  "12");
            p.add("nativeFieldMatch.firstOccurrenceImportance", "0.8");
            p.add("nativeFieldMatch.firstOccurrenceImportance.foo", "0.6");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "baz");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "quux");
            ft.getIndexEnv().getFields()[4].setFilter(true);
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("foo").add("baz").add("quux"), in, out);
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeFieldMatchParams & pas = (dynamic_cast<NativeFieldMatchBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 5);
            EXPECT_TRUE(pas.vector[0].firstOccTable == tm.getTable("linear(0,2)"));
            EXPECT_TRUE(pas.vector[3].firstOccTable == tm.getTable("linear(0,1)"));
            EXPECT_TRUE(pas.vector[0].numOccTable == tm.getTable("linear(0,3)"));
            EXPECT_TRUE(pas.vector[3].numOccTable == tm.getTable("linear(0,4)"));
            EXPECT_APPROX(pas.vector[0].maxTableSum, 2.4, 10e-6);
            EXPECT_APPROX(pas.vector[3].maxTableSum, 1.6, 10e-6);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 200u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[3].fieldWeight, 0u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, false); // only 'foo' and 'baz' are specified explicit
            EXPECT_EQUAL(pas.vector[2].field, false); // 'qux' is an attribute
            EXPECT_EQUAL(pas.vector[3].field, false); // fieldWeight == 0 -> do not consider this field
            EXPECT_EQUAL(pas.vector[4].field, false);  // filter field
            EXPECT_EQUAL(pas.vector[0].averageFieldLength, 400u);
            EXPECT_EQUAL(pas.vector[3].averageFieldLength, 500u);
            EXPECT_EQUAL(pas.minFieldLength, 12u);
            EXPECT_EQUAL(pas.vector[0].firstOccImportance, 0.6);
            EXPECT_EQUAL(pas.vector[3].firstOccImportance, 0.8);
        }
        {
            FtIndexEnvironment ie;
            FT_DUMP(_factory, "nativeFieldMatch", ie, StringList().add("nativeFieldMatch"));
        }
    }

    { // test helper functions
        FtFeatureTest ft(_factory, "");
        NativeFieldMatchParams p;
        NativeFieldMatchParam f;
        Table t;
        t.add(0).add(1).add(2).add(3).add(4).add(5).add(6).add(7);
        f.firstOccTable = &t;
        f.numOccTable = &t;
        p.vector.push_back(f);
        NativeFieldMatchExecutorSharedState nfmess(ft.getQueryEnv(), p);
        NativeFieldMatchExecutor nfme(nfmess);
        EXPECT_EQUAL(p.minFieldLength, 6u);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 0, 4), 0);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 1, 4), 1);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 2, 4), 2);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 3, 4), 4);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 3, 6), 4);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 4, 6), 5);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 5, 6), 7);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 0, 12), 0);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 4, 12), 2);
        EXPECT_EQUAL(nfme.getFirstOccBoost(0, 11, 12), 7);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 0, 4), 0);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 2, 4), 2);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 4, 4), 4);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 4, 6), 4);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 5, 6), 5);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 6, 6), 7);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 0, 12), 0);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 6, 12), 3);
        EXPECT_EQUAL(nfme.getNumOccBoost(0, 12, 12), 7);
    }
    { // test params object
        NativeFieldMatchParams p;
        p.resize(1);
        p.setMaxTableSums(0, 0); // test reset to 1
        EXPECT_EQUAL(p.vector[0].maxTableSum, 1);
    }

    { // test executor
        // 1 term
        EXPECT_TRUE(assertNativeFieldMatch(55, "a", "a"));
        EXPECT_TRUE(assertNativeFieldMatch(40, "a", "x x x a"));
        EXPECT_TRUE(assertNativeFieldMatch(70, "a", "a a a a"));

        // 2 terms
        EXPECT_TRUE(assertNativeFieldMatch(27.5, "a b", "a"));
        EXPECT_TRUE(assertNativeFieldMatch(52.5, "a b", "a b"));
        EXPECT_TRUE(assertNativeFieldMatch(67.5, "a b", "a b a b a b a b"));

        // 3 terms
        EXPECT_TRUE(assertNativeFieldMatch(50, "a b c", "a b c"));

        // 4 terms
        EXPECT_TRUE(assertNativeFieldMatch(47.5, "a b c d", "a b c d"));

        // change term weight
        EXPECT_TRUE(assertNativeFieldMatch(45, "a b", "a x x x b"));
        EXPECT_TRUE(assertNativeFieldMatch(50, "a!600 b!200", "a x x x b"));
        EXPECT_TRUE(assertNativeFieldMatch(40, "a!200 b!600", "a x x x b"));
        EXPECT_TRUE(assertNativeFieldMatch(55, "a!200 b!0",   "a x x x b"));

        // change significance
        EXPECT_TRUE(assertNativeFieldMatch(46, "a%0.4 b%0.1", "x a x x x b"));
        EXPECT_TRUE(assertNativeFieldMatch(34, "a%0.1 b%0.4", "x a x x x b"));

        // change firstOccImportance
        Properties p;
        p.add("nativeFieldMatch.firstOccurrenceImportance", "1");
        EXPECT_TRUE(assertNativeFieldMatch(100, "a", "a", p));
        p.clear().add("nativeFieldMatch.firstOccurrenceImportance", "0");
        EXPECT_TRUE(assertNativeFieldMatch(10, "a", "a", p));

        // use table normalization
        p.clear().add("nativeRank.useTableNormalization", "true");
        // norm factor = (100*0.5 + 60*0.5) = 80
        EXPECT_TRUE(assertNativeFieldMatch(0.6875, "a", "a", p));           // (55/80)
        EXPECT_TRUE(assertNativeFieldMatch(1,      "a", "a a a a a a", p)); // (80/80)
        p.add("nativeFieldMatch.firstOccurrenceTable", "linear(0,0)");
        p.add("nativeFieldMatch.occurrenceCountTable", "linear(0,0)");
        EXPECT_TRUE(assertNativeFieldMatch(0, "a", "a", p));

        // use average field length
        p.clear().add("nativeFieldMatch.averageFieldLength.foo", "12");
        EXPECT_TRUE(assertNativeFieldMatch(50, "a", "a", p));         // firstOccBoost: 100, numOccBoost: 0
        EXPECT_TRUE(assertNativeFieldMatch(45, "a", "x x x a", p));   // firstOccBoost: 90,  numOccBoost: 0
        EXPECT_TRUE(assertNativeFieldMatch(50, "a", "x x x a a", p)); // firstOccBoost: 90,  numOccBoost: 10

        // change field weight
        p.clear().add("vespa.fieldweight.foo", "0");
        EXPECT_TRUE(assertNativeFieldMatch(0, "a", "a", p));

        // change docId to give 0 hits
        EXPECT_TRUE(assertNativeFieldMatch(0, "a", "a", p.clear(), 2));
    }
}

bool
Test::assertNativeFieldMatch(feature_t score,
                             const vespalib::string & query,
                             const vespalib::string & field,
                             const Properties & props,
                             uint32_t docId)
{
    LOG(info, "assertNativeFieldMatch(%f, '%s', '%s')", score, query.c_str(), field.c_str());

    // Setup feature test.
    vespalib::string feature = "nativeFieldMatch";
    FtFeatureTest ft(_factory, feature);

    StringVectorMap index;
    index["foo"] = FtUtil::tokenize(field);
    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(256)));
    ft.getIndexEnv().getProperties().add("nativeFieldMatch.firstOccurrenceTable",
                                         vespalib::make_string("linear(-10,100,%zu)", std::max((size_t)6, index["foo"].size())));
    ft.getIndexEnv().getProperties().add("nativeFieldMatch.occurrenceCountTable",
                                         vespalib::make_string("linear(10,0,%zu)", std::max((size_t)6, index["foo"].size()) + 1));
    ft.getIndexEnv().getProperties().add("nativeRank.useTableNormalization", "false"); // make it easier to test
    ft.getIndexEnv().getProperties().import(props);
    FT_SETUP(ft, FtUtil::toQuery(query), index, 1);

    // Execute and compare results.
    if (!EXPECT_TRUE(ft.execute(score, EPS, docId))) {
        return false;
    }
    return true;
}

void
Test::testNativeAttributeMatch()
{
    { // test blueprint
        NativeAttributeMatchBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "nativeAttributeMatch"));

        FtFeatureTest ft(_factory, "");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "qux");
        ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(16)));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params.add("baz")); // field 'baz' not found
        params.clear();

        Properties & p = ft.getIndexEnv().getProperties();
        p.add("nativeAttributeMatch.weightTable", "a");
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // table 'a' not found

//        const TableManager & tm = ft.getIndexEnv().getTableManager();
        {
            p.clear();
            p.add("nativeRank.useTableNormalization", "false");
            FT_SETUP_OK(pt, params, in, out.add("score"));
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeAttributeMatchParams & pas = (dynamic_cast<NativeAttributeMatchBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 3);
//            EXPECT_TRUE(pas.vector[0].weightBoostTable == tm.getTable("linear(1,0)"));
//            EXPECT_TRUE(pas.vector[1].weightBoostTable == tm.getTable("linear(1,0)"));
            EXPECT_EQUAL(pas.vector[0].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[1].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, true);
            EXPECT_EQUAL(pas.vector[2].field, false);
        }
        {
            p.clear();
            p.add("nativeAttributeMatch.weightTable",     "linear(0,3)");
            p.add("nativeAttributeMatch.weightTable.foo", "linear(0,2)");
            p.add("vespa.fieldweight.foo", "200");
            p.add("vespa.fieldweight.baz", "0");
            ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "baz");
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("foo").add("baz"), in, out);
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeAttributeMatchParams & pas = (dynamic_cast<NativeAttributeMatchBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 4);
//            EXPECT_TRUE(pas.vector[0].weightBoostTable == tm.getTable("linear(0,2)"));
//            EXPECT_TRUE(pas.vector[3].weightBoostTable == tm.getTable("linear(0,3)"));
            EXPECT_EQUAL(pas.vector[0].maxTableSum, 2);
            EXPECT_EQUAL(pas.vector[3].maxTableSum, 3);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 200u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[3].fieldWeight, 0u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, false); // only 'foo' and 'baz' are specified explicit
            EXPECT_EQUAL(pas.vector[2].field, false); // 'qux' is an index
            EXPECT_EQUAL(pas.vector[3].field, false); // fieldWeight == 0 -> do not consider this field
        }

        {
            FtIndexEnvironment ie;
            FT_DUMP(_factory, "nativeAttributeMatch", ie, StringList().add("nativeAttributeMatch"));
        }
    }
    { // test executor

        EXPECT_TRUE(assertNativeAttributeMatch(15, ANAM(10), ANAM(10)));  // basic
        EXPECT_TRUE(assertNativeAttributeMatch(5, ANAM(-10), ANAM(10))); // negative weight
        EXPECT_TRUE(assertNativeAttributeMatch(12.5, ANAM(10, 600), ANAM(10, 200))); // change term weights
        EXPECT_TRUE(assertNativeAttributeMatch(10,   ANAM(10, 600), ANAM(10, 0)));   // change term weights
        EXPECT_TRUE(assertNativeAttributeMatch(18, ANAM(10, 100, 200), ANAM(10, 100, 800))); // change field weights
        EXPECT_TRUE(assertNativeAttributeMatch(0,  ANAM(10, 100, 0), ANAM(10, 100, 0)));   // change field weights
        EXPECT_TRUE(assertNativeAttributeMatch(10, ANAM(10, 100, 100, 2), ANAM(10, 100, 100))); // change docId to give 1 hit
        EXPECT_TRUE(assertNativeAttributeMatch(0, ANAM(10, 100, 100, 2), ANAM(10, 100, 100, 2))); // change docId to give 0 hits
        { // use table normalization
            // foo: max table value: 255
            // bar: max table value: 510
            Properties p;
            p.add("nativeRank.useTableNormalization", "true");
            EXPECT_TRUE(assertNativeAttributeMatch(0.2941, ANAM(100), ANAM(50), p)); // (100/255 + 100/510)*0.5
            EXPECT_TRUE(assertNativeAttributeMatch(1, ANAM(255), ANAM(255), p));     // (255/255 + 510/510)*0.5
            p.add("nativeAttributeMatch.weightTable.foo", "linear(0,0)");
            p.add("nativeAttributeMatch.weightTable.bar", "linear(0,0)");
            EXPECT_TRUE(assertNativeAttributeMatch(0, ANAM(100), ANAM(50), p));
        }
    }
}

bool
Test::assertNativeAttributeMatch(feature_t score, const ANAM & t1, const ANAM & t2, const Properties & props)
{
    LOG(info, "assertNativeAttributeMatch(%f, '%s', '%s')", score, t1.toString().c_str(), t2.toString().c_str());
    vespalib::string feature = "nativeAttributeMatch";
    FtFeatureTest ft(_factory, feature);
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "foo");
    ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(256)));
    ft.getIndexEnv().getProperties().add("nativeAttributeMatch.weightTable.foo", "linear(1,0)");
    ft.getIndexEnv().getProperties().add("nativeAttributeMatch.weightTable.bar", "linear(2,0)");
    ft.getIndexEnv().getProperties().add("vespa.fieldweight.foo", vespalib::make_string("%u", t1.fieldWeight));
    ft.getIndexEnv().getProperties().add("vespa.fieldweight.bar", vespalib::make_string("%u", t2.fieldWeight));
    ft.getIndexEnv().getProperties().add("nativeRank.useTableNormalization", "false"); // make it easier to test
    ft.getIndexEnv().getProperties().import(props);
    if (!EXPECT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("foo") != NULL)) { // t1
        return false;
    }
    if (!EXPECT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("bar") != NULL)) { // t2
        return false;
    }
    ft.getQueryEnv().getTerms()[0].setWeight(t1.termWeight);
    ft.getQueryEnv().getTerms()[1].setWeight(t2.termWeight);
    ASSERT_TRUE(ft.setup());

    MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
    {
        TermFieldMatchData *tfmd = mdb->getTermFieldMatchData(0, 0);
        tfmd->reset(t1.docId);
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(t1.attributeWeight);
        tfmd->appendPosition(pos);
    }
    {
        TermFieldMatchData *tfmd = mdb->getTermFieldMatchData(1, 1);
        tfmd->reset(t2.docId);
        TermFieldMatchDataPosition pos;
        pos.setElementWeight(t2.attributeWeight);
        tfmd->appendPosition(pos);
    }
    if (!EXPECT_TRUE(ft.execute(score, EPS))) {
        return false;
    }
    return true;
}

void
Test::testNativeProximity()
{
    { // test blueprint
        NativeProximityBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "nativeProximity"));

        FtFeatureTest ft(_factory, "");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "qux");
        ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(16)));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params.add("baz")); // field 'baz' not found
        params.clear();

        Properties & p = ft.getIndexEnv().getProperties();
        p.add("nativeProximity.proximityTable", "a");
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // table 'a' not found
        p.clear().add("nativeProximity.reverseProximityTable", "b");
        FT_SETUP_FAIL(pt, ft.getIndexEnv(), params); // table 'b' not found

        const TableManager & tm = ft.getIndexEnv().getTableManager();
        {
            p.clear();
            p.add("nativeRank.useTableNormalization", "false");
            FT_SETUP_OK(pt, params, in, out.add("score"));
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeProximityParams & pas = (dynamic_cast<NativeProximityBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 3);
            EXPECT_TRUE(pas.vector[0].proximityTable == tm.getTable("expdecay(500,3)"));
            EXPECT_TRUE(pas.vector[1].proximityTable == tm.getTable("expdecay(500,3)"));
            EXPECT_TRUE(pas.vector[0].revProximityTable == tm.getTable("expdecay(400,3)"));
            EXPECT_TRUE(pas.vector[1].revProximityTable == tm.getTable("expdecay(400,3)"));
            EXPECT_EQUAL(pas.vector[0].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[1].maxTableSum, 1);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, true);
            EXPECT_EQUAL(pas.vector[2].field, false);
            EXPECT_EQUAL(pas.slidingWindow, 4u);
            EXPECT_EQUAL(pas.vector[0].proximityImportance, 0.5);
            EXPECT_EQUAL(pas.vector[1].proximityImportance, 0.5);
        }
        {
            p.clear();
            p.add("nativeProximity.proximityTable",     "linear(0,1)");
            p.add("nativeProximity.proximityTable.foo", "linear(0,2)");
            p.add("nativeProximity.reverseProximityTable",     "linear(0,3)");
            p.add("nativeProximity.reverseProximityTable.baz", "linear(0,4)");
            p.add("vespa.fieldweight.foo", "200");
            p.add("vespa.fieldweight.baz", "0");
            p.add("nativeProximity.slidingWindowSize", "2");
            p.add("nativeProximity.proximityImportance", "0.8");
            p.add("nativeProximity.proximityImportance.foo", "0.6");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "baz");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "quux");
            ft.getIndexEnv().getFields()[4].setFilter(true);
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("foo").add("baz"), in, out);
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeProximityParams & pas = (dynamic_cast<NativeProximityBlueprint *>(bp.get()))->getParams();
            ASSERT_TRUE(pas.vector.size() == 5);
            EXPECT_TRUE(pas.vector[0].proximityTable == tm.getTable("linear(0,2)"));
            EXPECT_TRUE(pas.vector[3].proximityTable == tm.getTable("linear(0,1)"));
            EXPECT_TRUE(pas.vector[0].revProximityTable == tm.getTable("linear(0,3)"));
            EXPECT_TRUE(pas.vector[3].revProximityTable == tm.getTable("linear(0,4)"));
            EXPECT_APPROX(pas.vector[0].maxTableSum, 2.4, 10e-6);
            EXPECT_APPROX(pas.vector[3].maxTableSum, 1.6, 10e-6);
            EXPECT_EQUAL(pas.vector[0].fieldWeight, 200u);
            EXPECT_EQUAL(pas.vector[1].fieldWeight, 100u);
            EXPECT_EQUAL(pas.vector[3].fieldWeight, 0u);
            EXPECT_EQUAL(pas.vector[0].field, true);
            EXPECT_EQUAL(pas.vector[1].field, false); // only 'foo' and 'baz' are specified explicit
            EXPECT_EQUAL(pas.vector[2].field, false); // 'qux' is an attribute
            EXPECT_EQUAL(pas.vector[3].field, false); // fieldWeight == 0 -> do not consider this field
            EXPECT_EQUAL(pas.vector[4].field, false); // filter field
            EXPECT_EQUAL(pas.slidingWindow, 2u);
            EXPECT_EQUAL(pas.vector[0].proximityImportance, 0.6);
            EXPECT_EQUAL(pas.vector[3].proximityImportance, 0.8);
        }

        {
            FtIndexEnvironment ie;
            FT_DUMP(_factory, "nativeProximity", ie, StringList().add("nativeProximity"));
        }
    }

    { // test NativeProximityExecutor::generateTermPairs()
        QueryTermVector terms;
        SimpleTermData a, b, c;
        a.setWeight(search::query::Weight(100));
        a.setUniqueId(0);
        b.setWeight(search::query::Weight(200));
        b.setUniqueId(1);
        c.setWeight(search::query::Weight(300));
        c.setUniqueId(2);
        terms.push_back(QueryTerm(&a, 0.1));
        terms.push_back(QueryTerm(&b, 0.2));
        terms.push_back(QueryTerm(&c, 0.3));
        FtFeatureTest ft(_factory, "nativeProximity");
        FtQueryEnvironment & env = ft.getQueryEnv();
        env.getProperties().add("vespa.term.1.connexity", "0");
        env.getProperties().add("vespa.term.1.connexity", "0.8");
        env.getProperties().add("vespa.term.2.connexity", "1");
        env.getProperties().add("vespa.term.2.connexity", "0.6");
        {
            NativeProximityExecutor::FieldSetup setup(0);
            NativeProximityExecutorSharedState::TermPairVector & pairs = setup.pairs;
            NativeProximityExecutorSharedState::generateTermPairs(env, terms, 0, setup);
            EXPECT_EQUAL(pairs.size(), 0u);
            NativeProximityExecutorSharedState::generateTermPairs(env, terms, 1, setup);
            EXPECT_EQUAL(pairs.size(), 0u);
            NativeProximityExecutorSharedState::generateTermPairs(env, terms, 2, setup);
            EXPECT_EQUAL(pairs.size(), 2u);
            EXPECT_TRUE(pairs[0].first.termData() == &a);
            EXPECT_TRUE(pairs[0].second.termData() == &b);
            EXPECT_EQUAL(pairs[0].connectedness, 0.8);
            EXPECT_TRUE(pairs[1].first.termData() == &b);
            EXPECT_TRUE(pairs[1].second.termData() == &c);
            EXPECT_EQUAL(pairs[1].connectedness, 0.6);
            EXPECT_EQUAL(setup.divisor, 118); // (10 + 40)*0.8 + (40 + 90)*0.6

            pairs.clear();
            setup.divisor = 0;

            NativeProximityExecutorSharedState::generateTermPairs(env, terms, 3, setup);
            EXPECT_EQUAL(pairs.size(), 3u);
            EXPECT_TRUE(pairs[0].first.termData() == &a);
            EXPECT_TRUE(pairs[0].second.termData() == &b);
            EXPECT_EQUAL(pairs[0].connectedness, 0.8);
            EXPECT_TRUE(pairs[1].first.termData() == &a);
            EXPECT_TRUE(pairs[1].second.termData() == &c);
            EXPECT_EQUAL(pairs[1].connectedness, 0.3);
            EXPECT_TRUE(pairs[2].first.termData() == &b);
            EXPECT_TRUE(pairs[2].second.termData() == &c);
            EXPECT_EQUAL(pairs[2].connectedness, 0.6);
            EXPECT_EQUAL(setup.divisor, 148); // (10 + 40)*0.8 + (10 + 90)*0.3 + (40 + 90)*0.6

            pairs.clear();
            setup.divisor = 0;
            a.setWeight(search::query::Weight(0));
            b.setWeight(search::query::Weight(0));

            // test that (ab) is filtered away
            NativeProximityExecutorSharedState::generateTermPairs(env, terms, 2, setup);
            EXPECT_EQUAL(pairs.size(), 1u);
            EXPECT_TRUE(pairs[0].first.termData() == &b);
            EXPECT_TRUE(pairs[0].second.termData() == &c);
            EXPECT_EQUAL(pairs[0].connectedness, 0.6);
        }
    }

    { // test executor
        // 1 pair (only forward)
        EXPECT_TRUE(assertNativeProximity(0, "a",   "a"));
        EXPECT_TRUE(assertNativeProximity(0, "a b", "a"));
        EXPECT_TRUE(assertNativeProximity(5, "a b", "a b"));
        EXPECT_TRUE(assertNativeProximity(1, "a b", "a x x x x b"));
        EXPECT_TRUE(assertNativeProximity(0, "a b", "a x x x x x b"));
        EXPECT_TRUE(assertNativeProximity(0, "a b", "a x x x x x x b"));
        EXPECT_TRUE(assertNativeProximity(5, "a b", "a x x a x a a b"));
        EXPECT_TRUE(assertNativeProximity(5, "b a", "a x x a x a a b"));

        // 1 pair (both forward and backward)
        EXPECT_TRUE(assertNativeProximity(10, "a b", "a b a"));
        EXPECT_TRUE(assertNativeProximity(10, "b a", "a b a"));
        EXPECT_TRUE(assertNativeProximity(10, "a a", "a a"));     // term distance 1
        EXPECT_TRUE(assertNativeProximity(6,  "a a", "a x x a")); // term distance 3
        EXPECT_TRUE(assertNativeProximity(9,  "a b", "a x x x x x b x x x x a x x x b x x a x b a"));
        EXPECT_TRUE(assertNativeProximity(9,  "b a", "a x x x x x b x x x x a x x x b x x a x b a"));

        // 2 pairs ((ab),(bc))
        EXPECT_TRUE(assertNativeProximity(5,  "a b c", "a b c"));
        EXPECT_TRUE(assertNativeProximity(10, "a b c", "a b c b a"));

        // change weight
        EXPECT_TRUE(assertNativeProximity(4,   "a b c",     "a b x x c"));
        EXPECT_TRUE(assertNativeProximity(4.2, "a!200 b c", "a b x x c"));
        EXPECT_TRUE(assertNativeProximity(3.8, "a b c!200", "a b x x c"));
        EXPECT_TRUE(assertNativeProximity(4.333, "a b c!0",   "a b x x c")); // ((100+100)*5 + (100+0)*3) / 300
        EXPECT_TRUE(assertNativeProximity(5, "a b!0 c!0",   "a b x x c")); // ((100+0)*5 + (0+0)*3) / 100
        EXPECT_TRUE(assertNativeProximity(0, "a!0 b!0",   "a b"));

        // change significance
        EXPECT_TRUE(assertNativeProximity(4.692, "a%1 b%0.1 c%0.1", "a b x x c"));
        EXPECT_TRUE(assertNativeProximity(3.308, "a%0.1 b%0.1 c%1", "a b x x c"));

        // change connectedness
        EXPECT_TRUE(assertNativeProximity(4,     "a 1:b 1:c",   "a b x x c"));
        EXPECT_TRUE(assertNativeProximity(3.667, "a 0.5:b 1:c", "a b x x c")); // (5*0.5 + 3*1) / (0.5 + 1)

        // change proximityImportance
        Properties p;
        p.add("nativeProximity.proximityImportance", "1");
        EXPECT_TRUE(assertNativeProximity(10, "a b", "a b x x x a", p));
        p.clear().add("nativeProximity.proximityImportance", "0");
        EXPECT_TRUE(assertNativeProximity(4, "a b", "a b x x x a", p));

        // use table normalization
        p.clear().add("nativeRank.useTableNormalization", "true");
        // norm factor = (10*0.5 + 10*0.5) = 10
        EXPECT_TRUE(assertNativeProximity(0.5, "a b",   "a b", p));
        EXPECT_TRUE(assertNativeProximity(0.5, "a b c", "a b c", p));
        EXPECT_TRUE(assertNativeProximity(1,   "a b",   "a b a", p));
        EXPECT_TRUE(assertNativeProximity(1,   "a b c", "a b c b a", p));
        p.add("nativeProximity.proximityTable", "linear(0,0)");
        p.add("nativeProximity.reverseProximityTable", "linear(0,0)");
        EXPECT_TRUE(assertNativeProximity(0, "a b",   "a b", p));

        // change field weight
        p.clear().add("vespa.fieldweight.foo", "0");
        EXPECT_TRUE(assertNativeProximity(0, "a b", "a b", p));

        // change docId to give 0 hits
        EXPECT_TRUE(assertNativeProximity(0, "a b", "a b", p.clear(), 2));
    }
}

bool
Test::assertNativeProximity(feature_t score,
                            const vespalib::string & query,
                            const vespalib::string & field,
                            const Properties & props,
                            uint32_t docId)
{
    LOG(info, "assertNativeProximity(%f, '%s', '%s')", score, query.c_str(), field.c_str());

    // Setup feature test.
    vespalib::string feature = "nativeProximity";
    FtFeatureTest ft(_factory, feature);

    ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
    ft.getIndexEnv().getTableManager().addFactory(ITableFactory::SP(new FunctionTableFactory(6)));
    ft.getIndexEnv().getProperties().add("nativeProximity.proximityTable", "linear(-2,10)");
    ft.getIndexEnv().getProperties().add("nativeProximity.reverseProximityTable", "linear(-2,10)");
    ft.getIndexEnv().getProperties().add("nativeProximity.slidingWindowSize", "2");
    ft.getIndexEnv().getProperties().add("nativeRank.useTableNormalization", "false"); // make it easier to test
    ft.getIndexEnv().getProperties().import(props);
    StringVectorMap index;
    index["foo"] = FtUtil::tokenize(field);
    FT_SETUP(ft, FtUtil::toQuery(query), index, 1);

    // Execute and compare results.
    if (!EXPECT_TRUE(ft.execute(score, EPS, docId))) {
        return false;
    }
    return true;
}

void
Test::testNativeRank()
{
    { // test blueprint
        NativeRankBlueprint pt;

        EXPECT_TRUE(assertCreateInstance(pt, "nativeRank"));

        FtFeatureTest ft(_factory, "");

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params.add("foo")); // field 'foo' not found
        params.clear();

        {
            FT_SETUP_OK(pt, params, in.add("nativeFieldMatch").add("nativeProximity").add("nativeAttributeMatch"),
                        out.add("score"));
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeRankParams & pas = (dynamic_cast<NativeRankBlueprint *>(bp.get()))->getParams();
            EXPECT_EQUAL(pas.fieldMatchWeight,     100u);
            EXPECT_EQUAL(pas.attributeMatchWeight, 100u);
            EXPECT_EQUAL(pas.proximityWeight,      25u);
        }
        {
            Properties & p = ft.getIndexEnv().getProperties();
            p.add("nativeRank.useTableNormalization", "false");
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeRankParams & pas = (dynamic_cast<NativeRankBlueprint *>(bp.get()))->getParams();
            EXPECT_EQUAL(pas.proximityWeight, 100u);
            p.clear();
        }
        {
            Properties & p = ft.getIndexEnv().getProperties();
            p.add("nativeRank.fieldMatchWeight",     "200");
            p.add("nativeRank.attributeMatchWeight", "300");
            p.add("nativeRank.proximityWeight",      "400");
            FT_SETUP_OK(pt, params, in, out);
            Blueprint::UP bp = pt.createInstance();
            DummyDependencyHandler deps(*bp);
            bp->setup(ft.getIndexEnv(), params);
            const NativeRankParams & pas = (dynamic_cast<NativeRankBlueprint *>(bp.get()))->getParams();
            EXPECT_EQUAL(pas.fieldMatchWeight,     200u);
            EXPECT_EQUAL(pas.attributeMatchWeight, 300u);
            EXPECT_EQUAL(pas.proximityWeight,      400u);
        }

        FT_DUMP(_factory, "nativeRank", ft.getIndexEnv(), StringList().add("nativeRank"));

        { // test optimizations when weight == 0
            Properties & p = ft.getIndexEnv().getProperties();
            p.clear();
            p.add("nativeRank.fieldMatchWeight", "0");
            FT_SETUP_OK(pt, ft.getIndexEnv(), params,
                        in.clear().add("value(0)").add("nativeProximity").add("nativeAttributeMatch"), out);
            p.add("nativeRank.proximityWeight", "0");
            FT_SETUP_OK(pt, ft.getIndexEnv(), params,
                        in.clear().add("value(0)").add("value(0)").add("nativeAttributeMatch"), out);
            p.add("nativeRank.attributeMatchWeight", "0");
            FT_SETUP_OK(pt, ft.getIndexEnv(), params, in.clear().add("value(0)").add("value(0)").add("value(0)"), out);
        }
        { // nativeRank for a subset of fields
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar");
            ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "baz");
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.add("foo").add("bar"), in, out);
            ft.getIndexEnv().getProperties().clear();
            FT_SETUP_OK(pt, ft.getIndexEnv(), params,
                        in.clear().add("nativeFieldMatch(foo)").add("nativeProximity(foo)").add("nativeAttributeMatch(bar)"), out);
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("foo").add("baz"),
                        in.clear().add("nativeFieldMatch(foo,baz)").add("nativeProximity(foo,baz)").add("value(0)"), out);
            FT_SETUP_OK(pt, ft.getIndexEnv(), params.clear().add("bar"),
                        in.clear().add("value(0)").add("value(0)").add("nativeAttributeMatch(bar)"), out);
        }
    }

    { // test executor
        assertNativeRank(60,   1, 1, 1);
        assertNativeRank(72,   3, 1, 1);
        assertNativeRank(37.5, 0, 1, 3);
    }
}

bool
Test::assertNativeRank(feature_t score,
                       feature_t fieldMatchWeight,
                       feature_t attributeMatchWeight,
                       feature_t proximityWeight)
{
    LOG(info, "assertNativeRank(%f, %f, %f, %f)", score, fieldMatchWeight, attributeMatchWeight, proximityWeight);

    // Setup feature test.
    vespalib::string feature = "nativeRank";
    FtFeatureTest ft(_factory, feature);

    ft.getIndexEnv().getProperties().add("nativeRank.fieldMatchWeight",
                                         vespalib::make_string("%f", fieldMatchWeight));
    ft.getIndexEnv().getProperties().add("nativeRank.attributeMatchWeight",
                                         vespalib::make_string("%f", attributeMatchWeight));
    ft.getIndexEnv().getProperties().add("nativeRank.proximityWeight",
                                         vespalib::make_string("%f", proximityWeight));

    ft.getOverrides().add("nativeFieldMatch",     "90");
    ft.getOverrides().add("nativeAttributeMatch", "60");
    ft.getOverrides().add("nativeProximity",      "30");

    if (!EXPECT_TRUE(ft.setup())) {
        return false;
    }

    // Execute and compare results.
    if (!EXPECT_TRUE(ft.execute(score, EPS))) {
        return false;
    }
    return true;
}



int
Test::Main()
{
    TEST_INIT("nativerank_test");

    // Configure factory with all known blueprints.
    setup_fef_test_plugin(_factory);
    setup_search_features(_factory);

    testNativeFieldMatch();
    testNativeAttributeMatch();
    testNativeProximity();
    testNativeRank();

    TEST_DONE();
    return 0;
}

}
}

TEST_APPHOOK(search::features::Test);

