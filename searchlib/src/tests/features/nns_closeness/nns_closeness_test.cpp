// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/searchlib/features/closenessfeature.h>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/labels.h>
#include <vespa/searchlib/test/features/distance_closeness_fixture.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using search::feature_t;
using namespace search::features::test;
using namespace search::features;
using namespace search::fef::test;
using namespace search::fef;

using vespalib::eval::TensorSpec;

const vespalib::string labelFeatureName("closeness(label,nns)");
const vespalib::string fieldFeatureName("closeness(bar)");

using RankFixture = DistanceClosenessFixture;

TEST(NnsClosenessTest, require_that_blueprint_can_be_created_from_factory)
{
    BlueprintFactoryFixture f;
    Blueprint::SP bp = f.factory.createBlueprint("closeness");
    EXPECT_TRUE(bp.get() != 0);
    EXPECT_TRUE(dynamic_cast<ClosenessBlueprint*>(bp.get()) != 0);
}

TEST(NnsClosenessTest, require_that_no_features_are_dumped)
{
    ClosenessBlueprint f1;
    IndexEnvironmentFixture f2;
    FeatureDumpFixture f3;
    f1.visitDumpFeatures(f2.indexEnv, f3);
}

TEST(NnsClosenessTest, require_that_setup_can_be_done_on_random_label)
{
    ClosenessBlueprint f1;
    IndexEnvironmentFixture f2;
    DummyDependencyHandler deps(f1);
    f1.setName(vespalib::make_string("%s(label,random_label)", f1.getBaseName().c_str()));
    EXPECT_TRUE(static_cast<Blueprint&>(f1).setup(f2.indexEnv, std::vector<vespalib::string>{"label", "random_label"}));
}

TEST(NnsClosenessTest, require_that_no_label_gives_0_closeness)
{
    NoLabel f1;
    RankFixture f2(2, 2, f1, labelFeatureName);
    ASSERT_FALSE(f2.failed());
    EXPECT_EQ(0.0, f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_unrelated_label_gives_0_closeness)
{
    SingleLabel f1("unrelated", 1);
    RankFixture f2(2, 2, f1, labelFeatureName);
    ASSERT_FALSE(f2.failed());
    EXPECT_EQ(0.0, f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_labeled_item_raw_score_can_be_obtained)
{
    SingleLabel f1("nns", 1);
    RankFixture f2(2, 2, f1, labelFeatureName);
    ASSERT_FALSE(f2.failed());
    f2.setFooScore(0, 10, 5.0);
    EXPECT_EQ(1/(1+5.0), f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_field_raw_score_can_be_obtained)
{
    NoLabel f1;
    RankFixture f2(2, 2, f1, fieldFeatureName);
    ASSERT_FALSE(f2.failed());
    f2.setBarScore(0, 10, 5.0);
    EXPECT_EQ(1/(1+5.0), f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_other_raw_scores_are_ignored)
{
    SingleLabel f1("nns", 2);
    RankFixture f2(2, 2, f1, labelFeatureName);
    ASSERT_FALSE(f2.failed());
    f2.setFooScore(0, 10, 1.0);
    f2.setFooScore(1, 10, 2.0);
    f2.setBarScore(0, 10, 5.0);
    f2.setBarScore(1, 10, 6.0);
    EXPECT_EQ(1/(1+2.0), f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_the_correct_raw_score_is_used)
{
    NoLabel f1;
    RankFixture f2(2, 2, f1, fieldFeatureName);
    ASSERT_FALSE(f2.failed());
    f2.setFooScore(0, 10, 3.0);
    f2.setFooScore(1, 10, 4.0);
    f2.setBarScore(0, 10, 8.0);
    f2.setBarScore(1, 10, 7.0);
    EXPECT_EQ(1/(1+7.0), f2.getScore(10));
}

TEST(NnsClosenessTest, require_that_stale_data_is_ignored)
{
    SingleLabel f1("nns", 2);
    RankFixture f2(2, 2, f1, labelFeatureName);
    ASSERT_FALSE(f2.failed());
    f2.setFooScore(0, 10, 1.0);
    f2.setFooScore(1, 5, 2.0);
    EXPECT_EQ(0, f2.getScore(10));
}

void
expect_raw_score_calculated_on_the_fly(RankFixture& f)
{
    f.setBarScore(0, 8, 13.0);
    f.set_attribute_tensor(9, TensorSpec::from_expr("tensor(x[2]):[5,11]"));
    f.set_attribute_tensor(10, TensorSpec::from_expr("tensor(x[2]):[7,11]"));

    // For docids 9 and 10 the raw score is calculated on the fly
    // using a distance calculator over the attribute and query tensors.
    EXPECT_EQ(1/(1+13.0), f.getScore(8));
    EXPECT_EQ(1/(1+(5.0-3.0)), f.getScore(9));
    EXPECT_EQ(1/(1+(7.0-3.0)), f.getScore(10));
}

TEST(NnsClosenessTest, raw_score_is_calculated_on_the_fly_using_field_setup)
{
    NoLabel f1;
    RankFixture f2(0, 1, f1, fieldFeatureName, "tensor(x[2]):[3,11]");
    ASSERT_FALSE(f2.failed());
    expect_raw_score_calculated_on_the_fly(f2);
}

TEST(NnsClosenessTest, raw_score_is_calculated_on_the_fly_using_label_setup)
{
    SingleLabel f1("nns", 1);
    RankFixture f2(0, 1, f1, labelFeatureName, "tensor(x[2]):[3,11]");
    ASSERT_FALSE(f2.failed());
    expect_raw_score_calculated_on_the_fly(f2);
}

GTEST_MAIN_RUN_ALL_TESTS()
