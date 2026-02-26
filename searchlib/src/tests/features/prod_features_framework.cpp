// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prod_features_test.h"
#include <vespa/searchlib/features/valuefeature.h>

using namespace search::features;
using namespace search::fef;
using namespace search::fef::test;
using CollectionType = FieldInfo::CollectionType;

TEST_F(ProdFeaturesTest, test_framework)
{
    IndexEnvironment indexEnv;
    { // test index environment builder
        IndexEnvironmentBuilder ieb(indexEnv);
        ieb.addField(FieldType::INDEX, CollectionType::SINGLE, "foo")
            .addField(FieldType::ATTRIBUTE, CollectionType::WEIGHTEDSET, "bar")
            .addField(FieldType::INDEX, CollectionType::ARRAY, "baz");
        {
            const FieldInfo * info = indexEnv.getFieldByName("foo");
            ASSERT_TRUE(info != nullptr);
            EXPECT_EQ(info->id(), 0u);
            EXPECT_TRUE(info->type() == FieldType::INDEX);
            EXPECT_TRUE(info->collection() == CollectionType::SINGLE);
        }
        {
            const FieldInfo * info = indexEnv.getFieldByName("bar");
            ASSERT_TRUE(info != nullptr);
            EXPECT_EQ(info->id(), 1u);
            EXPECT_TRUE(info->type() == FieldType::ATTRIBUTE);
            EXPECT_TRUE(info->collection() == CollectionType::WEIGHTEDSET);
        }
        {
            const FieldInfo * info = indexEnv.getFieldByName("baz");
            ASSERT_TRUE(info != nullptr);
            EXPECT_EQ(info->id(), 2u);
            EXPECT_TRUE(info->type() == FieldType::INDEX);
            EXPECT_TRUE(info->collection() == CollectionType::ARRAY);
        }
        ASSERT_TRUE(indexEnv.getFieldByName("qux") == nullptr);
    }

    QueryEnvironment queryEnv(&indexEnv);
    MatchDataLayout layout;
    { // test query environment builder
        QueryEnvironmentBuilder qeb(queryEnv, layout);
        {
            SimpleTermData &tr = qeb.addAllFields();
            ASSERT_TRUE(tr.lookupField(0) != nullptr);
            ASSERT_TRUE(tr.lookupField(1) != nullptr);
            ASSERT_TRUE(tr.lookupField(2) != nullptr);
            EXPECT_TRUE(tr.lookupField(3) == nullptr);
            EXPECT_TRUE(tr.lookupField(0)->getHandle() == 0u);
            EXPECT_TRUE(tr.lookupField(1)->getHandle() == 1u);
            EXPECT_TRUE(tr.lookupField(2)->getHandle() == 2u);
            const ITermData *tp = queryEnv.getTerm(0);
            ASSERT_TRUE(tp != nullptr);
            EXPECT_EQ(tp, &tr);
        }
        {
            SimpleTermData *tr = qeb.addAttributeNode("bar");
            ASSERT_TRUE(tr != nullptr);
            ASSERT_TRUE(tr->lookupField(1) != nullptr);
            EXPECT_TRUE(tr->lookupField(0) == nullptr);
            EXPECT_TRUE(tr->lookupField(2) == nullptr);
            EXPECT_TRUE(tr->lookupField(3) == nullptr);
            EXPECT_TRUE(tr->lookupField(1)->getHandle() == 3u);
            const ITermData *tp = queryEnv.getTerm(1);
            ASSERT_TRUE(tp != nullptr);
            EXPECT_EQ(tp, tr);
        }
    }

    MatchData::UP data = layout.createMatchData();
    EXPECT_EQ(data->getNumTermFields(), 4u);

    { // check match data access
        MatchDataBuilder mdb(queryEnv, *data);

        // setup some occurence lists
        ASSERT_TRUE(mdb.addOccurence("foo", 0, 20));
        ASSERT_TRUE(mdb.addOccurence("foo", 0, 10));
        ASSERT_TRUE(mdb.setFieldLength("foo", 50));
        ASSERT_TRUE(mdb.addOccurence("baz", 0, 15));
        ASSERT_TRUE(mdb.addOccurence("baz", 0, 5));
        ASSERT_TRUE(mdb.setFieldLength("baz", 100));
        ASSERT_TRUE(mdb.apply(100));

        {
            {
                TermFieldMatchData *tfmd = mdb.getTermFieldMatchData(0, 0);
                ASSERT_TRUE(tfmd != nullptr);

                FieldPositionsIterator itr = tfmd->getIterator(); // foo (index)
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(itr.getFieldLength(), 50u);
                EXPECT_EQ(itr.getPosition(), 10u);
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(itr.getPosition(), 20u);
                itr.next();
                ASSERT_TRUE(!itr.valid());
            }
            {
                TermFieldMatchData *tfmd = mdb.getTermFieldMatchData(0, 1);
                ASSERT_TRUE(tfmd != nullptr);

                FieldPositionsIterator itr = tfmd->getIterator(); // bar (attribute)
                ASSERT_TRUE(!itr.valid());
            }
            {
                TermFieldMatchData *tfmd = mdb.getTermFieldMatchData(0, 2);
                ASSERT_TRUE(tfmd != nullptr);

                FieldPositionsIterator itr = tfmd->getIterator(); // baz (index)
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(itr.getFieldLength(), 100u);
                EXPECT_EQ(itr.getPosition(), 5u);
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(itr.getPosition(), 15u);
                itr.next();
                ASSERT_TRUE(!itr.valid());
            }
        }
        {
            TermFieldMatchData *tfmd = mdb.getTermFieldMatchData(1, 1);
            ASSERT_TRUE(tfmd != nullptr);

            FieldPositionsIterator itr = tfmd->getIterator(); // bar (attribute)
            ASSERT_TRUE(!itr.valid());
        }
    }
    { // check that data is cleared
        MatchDataBuilder mdb(queryEnv, *data);
        EXPECT_TRUE(mdb.getTermFieldMatchData(0, 0)->has_invalid_docid());
        EXPECT_TRUE(mdb.getTermFieldMatchData(0, 1)->has_invalid_docid());
        EXPECT_TRUE(mdb.getTermFieldMatchData(0, 2)->has_invalid_docid());
        EXPECT_TRUE(mdb.getTermFieldMatchData(1, 1)->has_invalid_docid());

        // test illegal things
        ASSERT_TRUE(!mdb.addOccurence("foo", 1, 10)); // invalid term/field combination
    }

    BlueprintFactory factory;
    factory.addPrototype(Blueprint::SP(new ValueBlueprint()));
    Properties overrides;

    { // test feature test runner
        FeatureTest ft(factory, indexEnv, queryEnv, layout,
                       StringList().add("value(10)").add("value(20)").add("value(30)"), overrides);
        MatchDataBuilder::UP mdb1 = ft.createMatchDataBuilder();
        EXPECT_TRUE(mdb1.get() == nullptr);
        EXPECT_TRUE(!ft.execute(RankResult().addScore("value(10)", 10.0f)));
        ASSERT_TRUE(ft.setup());
        MatchDataBuilder::UP mdb2 = ft.createMatchDataBuilder();
        EXPECT_TRUE(mdb2.get() != nullptr);

        EXPECT_TRUE(ft.execute(RankResult().addScore("value(10)", 10.0f).addScore("value(20)", 20.0f)));
        EXPECT_TRUE(!ft.execute(RankResult().addScore("value(10)", 20.0f)));
        EXPECT_TRUE(!ft.execute(RankResult().addScore("value(5)", 5.0f)));
    }
    { // test simple constructor
        MatchDataLayout mdl; // match data layout cannot be reused
        FeatureTest ft(factory, indexEnv, queryEnv, mdl, "value(10)", overrides);
        ASSERT_TRUE(ft.setup());
        EXPECT_TRUE(ft.execute(10.0f));
    }
}
