// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prod_features.h"
#include <vespa/searchlib/features/attributematchfeature.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".prod_features_attributematch");

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;

using search::AttributeVector;
using search::AttributeFactory;
using AttributePtr = AttributeVector::SP;

using AVC = search::attribute::Config;
using AVBT = search::attribute::BasicType;
using AVCT = search::attribute::CollectionType;
using CollectionType = FieldInfo::CollectionType;
using DataType = FieldInfo::DataType;

void
Test::testAttributeMatch()
{
    AttributeMatchBlueprint pt;
    {
        EXPECT_TRUE(assertCreateInstance(pt, "attributeMatch"));

        StringList params, in, out;
        FT_SETUP_FAIL(pt, params);            // expects 1 param
        FT_SETUP_FAIL(pt, params.add("foo")); // field must exists

        FtIndexEnvironment idx_env;
        idx_env.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        FT_SETUP_FAIL(pt, idx_env, params);        // field must be an attribute
        idx_env.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint");

        FT_SETUP_OK(pt, idx_env, params.clear().add("sint"), in, out
                    .add("completeness")
                    .add("queryCompleteness")
                    .add("fieldCompleteness")
                    .add("normalizedWeight")
                    .add("normalizedWeightedWeight")
                    .add("weight")
                    .add("significance")
                    .add("importance")
                    .add("matches")
                    .add("totalWeight")
                    .add("averageWeight")
                    .add("maxWeight"));

        FT_DUMP_EMPTY(_factory, "attributeMatch");

        FT_DUMP(_factory, "attributeMatch", idx_env, out.clear()
                .add("attributeMatch(sint)")
                .add("attributeMatch(sint).completeness")
                .add("attributeMatch(sint).queryCompleteness")
                .add("attributeMatch(sint).fieldCompleteness")
                .add("attributeMatch(sint).normalizedWeight")
                .add("attributeMatch(sint).normalizedWeightedWeight")
                .add("attributeMatch(sint).weight")
                .add("attributeMatch(sint).significance")
                .add("attributeMatch(sint).importance")
                .add("attributeMatch(sint).matches")
                .add("attributeMatch(sint).totalWeight")
                .add("attributeMatch(sint).averageWeight")
                .add("attributeMatch(sint).maxWeight"));
    }

    { // single attributes
        FtFeatureTest ft(_factory, StringList().
                         add("attributeMatch(sint)").add("attributeMatch(sfloat)").add("attributeMatch(sstr)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sint");   // 2 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sfloat"); // 1 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "sstr");   // 0 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("sint") != NULL);   // query term 0, hit in sint
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("sint") != NULL);   // query term 1, ..
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("sint") != NULL);   // query term 2, ..
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("sint") != NULL);   // query term 3, ..
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("sfloat") != NULL); // query term 4, hit in sfloat
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")) != NULL);
        ft.getQueryEnv().getTerms()[0].setWeight(search::query::Weight(20));
        ft.getQueryEnv().getTerms()[0].setUniqueId(0);
        ft.getQueryEnv().getTerms()[1].setWeight(search::query::Weight(20));
        ft.getQueryEnv().getTerms()[1].setUniqueId(1);
        ft.getQueryEnv().getTerms()[2].setWeight(search::query::Weight(10));
        ft.getQueryEnv().getTerms()[2].setUniqueId(1);
        ft.getQueryEnv().getTerms()[3].setWeight(search::query::Weight(10));
        ft.getQueryEnv().getTerms()[3].setUniqueId(1);
        ft.getQueryEnv().getTerms()[4].setWeight(search::query::Weight(20));
        ft.getQueryEnv().getTerms()[4].setUniqueId(1);
        ft.getQueryEnv().getTerms()[5].setWeight(search::query::Weight(20));
        ft.getQueryEnv().getTerms()[5].setUniqueId(1);
        ft.getQueryEnv().getProperties().add("vespa.term.0.significance", "0.5"); // change significance for term 0
        ft.getQueryEnv().getProperties().add("vespa.term.1.significance", "0.1"); // change significance for all other terms
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        mdb->setWeight("sint", 0, 0);
        mdb->setWeight("sint", 1, 0);
        mdb->setWeight("sfloat", 4, 0);
        mdb->apply(1);
        RankResult exp;
        exp.addScore("attributeMatch(sint)", 0.5f). // same as completeness
            addScore("attributeMatch(sint).matches", 2).
            addScore("attributeMatch(sint).totalWeight", 0).
            addScore("attributeMatch(sint).averageWeight", 0).
            addScore("attributeMatch(sint).maxWeight", 0).
            addScore("attributeMatch(sint).completeness", 0.5f).
            addScore("attributeMatch(sint).queryCompleteness", 0.5f).
            addScore("attributeMatch(sint).fieldCompleteness", 1).
            addScore("attributeMatch(sint).normalizedWeight", 0).
            addScore("attributeMatch(sint).normalizedWeightedWeight", 0).
            addScore("attributeMatch(sint).weight", 0.4).
            addScore("attributeMatch(sint).significance", 0.6).
            addScore("attributeMatch(sint).importance", 0.5).
            addScore("attributeMatch(sfloat)", 1). // same as completeness
            addScore("attributeMatch(sfloat).matches", 1).
            addScore("attributeMatch(sfloat).totalWeight", 0).
            addScore("attributeMatch(sfloat).averageWeight", 0).
            addScore("attributeMatch(sfloat).maxWeight", 0).
            addScore("attributeMatch(sfloat).completeness", 1).
            addScore("attributeMatch(sfloat).queryCompleteness", 1).
            addScore("attributeMatch(sfloat).fieldCompleteness", 1).
            addScore("attributeMatch(sfloat).normalizedWeight", 0).
            addScore("attributeMatch(sfloat).normalizedWeightedWeight", 0).
            addScore("attributeMatch(sfloat).weight", 0.2).
            addScore("attributeMatch(sfloat).significance", 0.1).
            addScore("attributeMatch(sfloat).importance", 0.15).
            addScore("attributeMatch(sstr)", 0). // same as completeness
            addScore("attributeMatch(sstr).matches", 0).
            addScore("attributeMatch(sstr).totalWeight", 0).
            addScore("attributeMatch(sstr).averageWeight", 0).
            addScore("attributeMatch(sstr).maxWeight", 0).
            addScore("attributeMatch(sstr).completeness", 0).
            addScore("attributeMatch(sstr).queryCompleteness", 0).
            addScore("attributeMatch(sstr).fieldCompleteness", 0).
            addScore("attributeMatch(sstr).normalizedWeight", 0).
            addScore("attributeMatch(sstr).normalizedWeightedWeight", 0).
            addScore("attributeMatch(sstr).weight", 0).
            addScore("attributeMatch(sstr).significance", 0).
            addScore("attributeMatch(sstr).importance", 0).
            setEpsilon(10e-6);
        ASSERT_TRUE(ft.execute(exp));
        ASSERT_TRUE(ft.execute(exp));
    }

    { // array attributes

        FtFeatureTest ft(_factory, StringList().add("attributeMatch(aint)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint");   // 1 matches
        ft.getIndexEnv().getProperties().add("attributeMatch(aint).fieldCompletenessImportance", "0.5");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("aint") != NULL);   // 0
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        mdb->setWeight("aint", 0, 0);
        mdb->apply(1);
        RankResult exp;
        exp.addScore("attributeMatch(aint)", 0.75f) // same as completeness
            .addScore("attributeMatch(aint).matches", 1)
            .addScore("attributeMatch(aint).totalWeight", 0)
            .addScore("attributeMatch(aint).averageWeight", 0)
            .addScore("attributeMatch(aint).maxWeight", 0)
            .addScore("attributeMatch(aint).completeness", 0.75f)
            .addScore("attributeMatch(aint).queryCompleteness", 1)
            .addScore("attributeMatch(aint).fieldCompleteness", 0.5f)
            .addScore("attributeMatch(aint).normalizedWeight", 0)
            .addScore("attributeMatch(aint).normalizedWeightedWeight", 0);
        ASSERT_TRUE(ft.execute(exp));
        ASSERT_TRUE(ft.execute(exp));
    }

    { // weighted set attributes
        FtFeatureTest ft(_factory, StringList().
                         add("attributeMatch(wsint)").add("attributeMatch(wsfloat)").add("attributeMatch(wsstr)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsint");   // 2 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsfloat"); // 1 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wsstr");   // 0 matches
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ft.getIndexEnv().getProperties().add("attributeMatch(wsint).maxWeight", "100");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("wsint") != NULL);   // 0
        ft.getQueryEnv().getTerms()[0].setWeight(search::query::Weight(2));
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("wsint") != NULL);   // 1
        ft.getQueryEnv().getTerms()[1].setWeight(search::query::Weight(3));
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("wsfloat") != NULL); // 2
        ft.getQueryEnv().getTerms()[2].setWeight(search::query::Weight(0));
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo")) != NULL);
        ft.getQueryEnv().getTerms()[3].setWeight(search::query::Weight(0));
        ASSERT_TRUE(ft.setup());

        MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
        mdb->setWeight("wsint", 0, 10);
        mdb->setWeight("wsint", 1, 20);
        mdb->setWeight("wsfloat", 2, -30);
        mdb->apply(1);
        RankResult exp;

        // test all three attributes
        exp.addScore("attributeMatch(wsint)", 1). // same as completeness
            addScore("attributeMatch(wsint).matches", 2).
            addScore("attributeMatch(wsint).totalWeight", 30).
            addScore("attributeMatch(wsint).averageWeight", 15).
            addScore("attributeMatch(wsint).maxWeight", 20).
            addScore("attributeMatch(wsint).completeness", 1).
            addScore("attributeMatch(wsint).queryCompleteness", 1).
            addScore("attributeMatch(wsint).fieldCompleteness", 1).
            addScore("attributeMatch(wsint).normalizedWeight", 0.1f).
            addScore("attributeMatch(wsint).normalizedWeightedWeight", 0.16f).
            addScore("attributeMatch(wsfloat)", 0.95). // same as completeness
            addScore("attributeMatch(wsfloat).matches", 1).
            addScore("attributeMatch(wsfloat).totalWeight", -30).
            addScore("attributeMatch(wsfloat).averageWeight", -30).
            addScore("attributeMatch(wsfloat).maxWeight", -30).
            addScore("attributeMatch(wsfloat).completeness", 0.95).
            addScore("attributeMatch(wsfloat).queryCompleteness", 1).
            addScore("attributeMatch(wsfloat).fieldCompleteness", 0).
            addScore("attributeMatch(wsfloat).normalizedWeight", 0).
            addScore("attributeMatch(wsfloat).normalizedWeightedWeight", 0).
            addScore("attributeMatch(wsstr)", 0). // same as completeness
            addScore("attributeMatch(wsstr).matches", 0).
            addScore("attributeMatch(wsstr).totalWeight", 0).
            addScore("attributeMatch(wsstr).averageWeight", 0).
            addScore("attributeMatch(wsstr).maxWeight", 0).
            addScore("attributeMatch(wsstr).completeness", 0).
            addScore("attributeMatch(wsstr).queryCompleteness", 0).
            addScore("attributeMatch(wsstr).fieldCompleteness", 0).
            addScore("attributeMatch(wsstr).normalizedWeight", 0).
            addScore("attributeMatch(wsstr).normalizedWeightedWeight", 0).
            setEpsilon(10e-6);
        ASSERT_TRUE(ft.execute(exp));
        ASSERT_TRUE(ft.execute(exp));

        // test fieldCompleteness
        mdb->setWeight("wsint", 0, 0);
        mdb->setWeight("wsint", 1, 15);
        mdb->apply(1);
        exp.clear().
            addScore("attributeMatch(wsint).fieldCompleteness", 0.5f);
        { // reset lazy evaluation
            RankResult dummy;
            ft.executeOnly(dummy, 0);
        }
        ASSERT_TRUE(ft.execute(exp));

        // test that normalized values lies in the interval [0,1].
        mdb->setWeight("wsfloat", 2, 1000);
        mdb->apply(1);
        ft.getQueryEnv().getTerms()[2].setWeight(search::query::Weight(100));
        exp.clear().
            addScore("attributeMatch(wsfloat).normalizedWeight", 1).
            addScore("attributeMatch(wsfloat).normalizedWeightedWeight", 1);
        { // reset lazy evaluation
            RankResult dummy;
            ft.executeOnly(dummy, 0);
        }
        ASSERT_TRUE(ft.execute(exp));
    }

    { // unique only attribute
        FtFeatureTest ft(_factory, "attributeMatch(unique)");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "unique");
        setupForAttributeTest(ft);
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("unique") != NULL);
        ASSERT_TRUE(ft.setup());

        RankResult exp;
        exp.addScore("attributeMatch(unique)", 0). // same as completeness
            addScore("attributeMatch(unique).matches", 0).
            addScore("attributeMatch(unique).totalWeight", 0).
            addScore("attributeMatch(unique).averageWeight", 0).
            addScore("attributeMatch(unique).maxWeight", 0).
            addScore("attributeMatch(unique).completeness", 0).
            addScore("attributeMatch(unique).queryCompleteness", 0).
            addScore("attributeMatch(unique).fieldCompleteness", 0).
            addScore("attributeMatch(unique).normalizedWeight", 0).
            addScore("attributeMatch(unique).normalizedWeightedWeight", 0);
        ASSERT_TRUE(ft.execute(exp));
    }
    {
        FtFeatureTest ft(_factory, StringList().add("attributeMatch(aint)").add("attributeMatch(wint)"));
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::ARRAY, "aint");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "wint");

        // setup an array and wset attributes with 0 elements
        AttributePtr aint = AttributeFactory::createAttribute("aint", AVC (AVBT::INT32, AVCT::ARRAY));
        AttributePtr wint = AttributeFactory::createAttribute("wint", AVC(AVBT::INT32, AVCT::WSET));
        aint->addReservedDoc();
        wint->addReservedDoc();
        ft.getIndexEnv().getAttributeMap().add(aint);
        ft.getIndexEnv().getAttributeMap().add(wint);
        aint->addDocs(1);
        aint->commit();
        ASSERT_TRUE(aint->getValueCount(0) == 0);
        wint->addDocs(1);
        wint->commit();
        ASSERT_TRUE(wint->getValueCount(0) == 0);

        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("aint") != NULL);
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("wint") != NULL);
        ASSERT_TRUE(ft.setup());

        RankResult exp;
        exp.addScore("attributeMatch(aint)", 0). // same as completeness
            addScore("attributeMatch(aint).completeness", 0).
            addScore("attributeMatch(aint).fieldCompleteness", 0).
            addScore("attributeMatch(wint)", 0). // same as completeness
            addScore("attributeMatch(wint).completeness", 0).
            addScore("attributeMatch(wint).fieldCompleteness", 0);
        ASSERT_TRUE(ft.execute(exp));
    }
    { // tensor attribute is not allowed
        FtFeatureTest ft(_factory, "attributeMatch(tensor)");
        ft.getIndexEnv().getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, DataType::TENSOR, "tensor");
        ASSERT_TRUE(ft.getQueryEnv().getBuilder().addAttributeNode("tensor") != nullptr);
        ASSERT_TRUE(!ft.setup());
    }
}
