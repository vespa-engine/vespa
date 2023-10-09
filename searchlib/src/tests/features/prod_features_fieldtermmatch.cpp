// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prod_features_test.h"
#include <vespa/searchlib/features/fieldtermmatchfeature.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>
LOG_SETUP(".prod_features_fieldtermmatch");

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using CollectionType = FieldInfo::CollectionType;

void
Test::testFieldTermMatch()
{
    {
        // Test blueprint.
        FieldTermMatchBlueprint pt;
        {
            EXPECT_TRUE(assertCreateInstance(pt, "fieldTermMatch"));

            StringList params, in, out;
            FT_SETUP_FAIL(pt, params);
            FT_SETUP_FAIL(pt, params.add("foo"));
            FT_SETUP_FAIL(pt, params.add("0"));
            FT_SETUP_FAIL(pt, params.add("1"));
            params.clear();

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
            FT_SETUP_FAIL(pt, ie, params.add("foo"));
            FT_SETUP_OK  (pt, ie, params.add("0"), in,
                          out.add("firstPosition")
                             .add("lastPosition")
                             .add("occurrences").add("weight").add("exactness"));
            FT_SETUP_FAIL(pt, ie, params.add("1"));
        }
        {
            FT_DUMP_EMPTY(_factory, "fieldTermMatch");

            FtIndexEnvironment ie;
            ie.getBuilder().addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "foo");
            FT_DUMP_EMPTY(_factory, "fieldTermMatch", ie); // must be an index field

            StringList dump;
            ie.getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "bar");
            for (uint32_t term = 0; term < 5; ++term) {
                vespalib::string bn = vespalib::make_string("fieldTermMatch(bar,%u)", term);
                dump.add(bn + ".firstPosition").add(bn + ".occurrences").add(bn + ".weight");
            }
            FT_DUMP(_factory, "fieldTermMatch", ie, dump);

            ie.getProperties().add("fieldTermMatch.numTerms", "0");
            FT_DUMP_EMPTY(_factory, "fieldTermMatch", ie);

            ie.getProperties().add("fieldTermMatch.numTerms.bar", "5");
            FT_DUMP(_factory, "fieldTermMatch", ie, dump);
        }
    }

    { // Test executor.
        FtFeatureTest ft(_factory, "fieldTermMatch(foo,0)");
        ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
        ASSERT_TRUE(ft.setup());
        RankResult exp;
        exp .addScore("fieldTermMatch(foo,0).firstPosition", 1000000)
            .addScore("fieldTermMatch(foo,0).lastPosition",  1000000)
            .addScore("fieldTermMatch(foo,0).occurrences",   0)
            .addScore("fieldTermMatch(foo,0).weight",        0)
            .addScore("fieldTermMatch(foo,0).exactness",     0);
        ASSERT_TRUE(ft.execute(exp));
    }
    {
        // Test executor.
         FtFeatureTest ft(_factory, "fieldTermMatch(foo,0)");
         ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
         ft.getQueryEnv().getBuilder().addAllFields();
         ASSERT_TRUE(ft.setup());

         search::fef::test::MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
         ASSERT_TRUE(mdb->setFieldLength("foo", 100));
         ASSERT_TRUE(mdb->addOccurence("foo", 0, 10));
         ASSERT_TRUE(mdb->addOccurence("foo", 0, 20));
         ASSERT_TRUE(mdb->apply(1));

         search::fef::test::RankResult exp;
         exp .addScore("fieldTermMatch(foo,0).firstPosition", 10)
             .addScore("fieldTermMatch(foo,0).lastPosition",  20)
             .addScore("fieldTermMatch(foo,0).occurrences",   2)
             .addScore("fieldTermMatch(foo,0).weight",        2)
             .addScore("fieldTermMatch(foo,0).exactness",     1);
         ASSERT_TRUE(ft.execute(exp));
    }
    {
        // Test executor (match without position information)
         FtFeatureTest ft(_factory, "fieldTermMatch(foo,0)");
         ft.getIndexEnv().getBuilder().addField(FieldType::INDEX, CollectionType::SINGLE, "foo");
         ft.getQueryEnv().getBuilder().addIndexNode(StringList().add("foo"));
         ASSERT_TRUE(ft.setup());

         // make sure the term match data is initialized with the term data
         MatchDataBuilder::UP mdb = ft.createMatchDataBuilder();
         mdb->getTermFieldMatchData(0, 0)->reset(1);

         search::fef::test::RankResult exp;
         exp .addScore("fieldTermMatch(foo,0).firstPosition", 1000000)
             .addScore("fieldTermMatch(foo,0).lastPosition",  1000000)
             .addScore("fieldTermMatch(foo,0).occurrences",   1)
             .addScore("fieldTermMatch(foo,0).weight",        0)
             .addScore("fieldTermMatch(foo,0).exactness",     0);
         ASSERT_TRUE(ft.execute(exp));
    }
}
