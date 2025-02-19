// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/aggr.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>

using vespalib::Stash;
using namespace vespalib::eval;
using namespace vespalib::eval::aggr;

TEST(AggrTest, require_that_aggregator_list_returns_appropriate_entries)
{
    auto list = Aggregator::list();
    ASSERT_EQ(list.size(), 7u);
    EXPECT_EQ(int(list[0]), int(Aggr::AVG));
    EXPECT_EQ(int(list[1]), int(Aggr::COUNT));
    EXPECT_EQ(int(list[2]), int(Aggr::PROD));
    EXPECT_EQ(int(list[3]), int(Aggr::SUM));
    EXPECT_EQ(int(list[4]), int(Aggr::MAX));
    EXPECT_EQ(int(list[5]), int(Aggr::MEDIAN));
    EXPECT_EQ(int(list[6]), int(Aggr::MIN));
}

TEST(AggrTest, require_that_aggr_is_simple_works_as_expected)
{
    EXPECT_FALSE(aggr::is_simple(Aggr::AVG));
    EXPECT_FALSE(aggr::is_simple(Aggr::COUNT));
    EXPECT_TRUE (aggr::is_simple(Aggr::PROD));
    EXPECT_TRUE (aggr::is_simple(Aggr::SUM));
    EXPECT_TRUE (aggr::is_simple(Aggr::MAX));
    EXPECT_FALSE(aggr::is_simple(Aggr::MEDIAN));
    EXPECT_TRUE (aggr::is_simple(Aggr::MIN));
}

TEST(AggrTest, require_that_aggr_is_ident_works_as_expected)
{
    EXPECT_TRUE (aggr::is_ident(Aggr::AVG));
    EXPECT_FALSE(aggr::is_ident(Aggr::COUNT));
    EXPECT_TRUE (aggr::is_ident(Aggr::PROD));
    EXPECT_TRUE (aggr::is_ident(Aggr::SUM));
    EXPECT_TRUE (aggr::is_ident(Aggr::MAX));
    EXPECT_TRUE (aggr::is_ident(Aggr::MEDIAN));
    EXPECT_TRUE (aggr::is_ident(Aggr::MIN));
}

TEST(AggrTest, require_that_aggr_is_complex_works_as_expected)
{
    EXPECT_FALSE(aggr::is_complex(Aggr::AVG));
    EXPECT_FALSE(aggr::is_complex(Aggr::COUNT));
    EXPECT_FALSE(aggr::is_complex(Aggr::PROD));
    EXPECT_FALSE(aggr::is_complex(Aggr::SUM));
    EXPECT_FALSE(aggr::is_complex(Aggr::MAX));
    EXPECT_TRUE (aggr::is_complex(Aggr::MEDIAN));
    EXPECT_FALSE(aggr::is_complex(Aggr::MIN));
}

TEST(AggrTest, require_that_AVG_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::AVG, stash);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 15.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 20.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 150.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::AVG);
}

TEST(AggrTest, require_that_COUNT_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::COUNT, stash);
    EXPECT_EQ(aggr.result(), 0.0);
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 1.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 2.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 3.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 1.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 2.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::COUNT);
}

TEST(AggrTest, require_that_PROD_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::PROD, stash);
    EXPECT_EQ(aggr.result(), 1.0);
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 200.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 6000.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 20000.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::PROD);
}

TEST(AggrTest, require_that_Prod_static_API_works_as_expected)
{
    using Type = Prod<double>;
    EXPECT_EQ(Type::null_value(), 1.0);
    EXPECT_EQ(Type::combine(3,7), 21.0);
    EXPECT_EQ(Type::combine(5,4), 20.0);
}

TEST(AggrTest, require_that_SUM_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::SUM, stash);
    EXPECT_EQ(aggr.result(), 0.0);
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 30.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 60.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 300.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::SUM);
}

TEST(AggrTest, require_that_Sum_static_API_works_as_expected)
{
    using Type = Sum<double>;
    EXPECT_EQ(Type::null_value(), 0.0);
    EXPECT_EQ(Type::combine(3,7), 10.0);
    EXPECT_EQ(Type::combine(5,4), 9.0);
}

TEST(AggrTest, require_that_MAX_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MAX, stash);
    EXPECT_EQ(aggr.result(), -std::numeric_limits<double>::infinity());
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 20.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 30.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 200.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MAX);
}

TEST(AggrTest, require_that_Max_static_API_works_as_expected)
{
    using Type = Max<double>;
    EXPECT_EQ(Max<double>::null_value(), -std::numeric_limits<double>::infinity());
    EXPECT_EQ(Max<float>::null_value(), -std::numeric_limits<float>::infinity());
    EXPECT_EQ(Type::combine(3,7), 7.0);
    EXPECT_EQ(Type::combine(5,4), 5.0);
}

TEST(AggrTest, require_that_MEDIAN_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MEDIAN, stash);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 15.0);
    aggr.next(7.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(40.0);
    EXPECT_EQ(aggr.result(), 15.0);
    aggr.next(16.0);
    EXPECT_EQ(aggr.result(), 16.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 150.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MEDIAN);
}

TEST(AggrTest, require_that_MEDIAN_aggregator_handles_NaN_values)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MEDIAN, stash);
    double my_nan = std::numeric_limits<double>::quiet_NaN();
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(my_nan);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.next(20.0);
    EXPECT_TRUE(std::isnan(aggr.result()));
}

TEST(AggrTest, require_that_MIN_aggregator_works_as_expected)
{
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MIN, stash);
    EXPECT_EQ(aggr.result(), std::numeric_limits<double>::infinity());
    aggr.first(10.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(20.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.next(30.0);
    EXPECT_EQ(aggr.result(), 10.0);
    aggr.first(100.0);
    EXPECT_EQ(aggr.result(), 100.0);
    aggr.next(200.0);
    EXPECT_EQ(aggr.result(), 100.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MIN);
}

TEST(AggrTest, require_that_Min_static_API_works_as_expected)
{
    using Type = Min<double>;
    EXPECT_EQ(Min<double>::null_value(), std::numeric_limits<double>::infinity());
    EXPECT_EQ(Min<float>::null_value(), std::numeric_limits<float>::infinity());
    EXPECT_EQ(Type::combine(3,7), 3.0);
    EXPECT_EQ(Type::combine(5,4), 4.0);
}

template <template <typename T> typename A>
float aggr_merge(const std::vector<float> &a, const std::vector<float> &b) {
    A<float> aggr0;
    A<float> aggr1;
    A<float> aggr2;
    A<float> aggr3;
    for (float v: a) {
        aggr1.sample(v);
    }
    for (float v: b) {
        aggr2.sample(v);
    }
    aggr0.merge(aggr1);
    aggr2.merge(aggr3);
    aggr0.merge(aggr2);
    return aggr0.result();
}

TEST(AggrTest, require_that_aggregator_merge_works)
{
    float my_nan = std::numeric_limits<float>::quiet_NaN();
    EXPECT_EQ(aggr_merge<Avg>({1,2},{3,4}), 2.5f);
    EXPECT_EQ(aggr_merge<Count>({1,2},{3,4}), 4.0f);
    EXPECT_EQ(aggr_merge<Prod>({1,2},{3,4}), 24.0f);
    EXPECT_EQ(aggr_merge<Sum>({1,2},{3,4}), 10.0f);
    EXPECT_EQ(aggr_merge<Max>({1,2},{3,4}), 4.0f);
    EXPECT_EQ(aggr_merge<Median>({1,2},{3,4}), 2.5f);
    EXPECT_EQ(aggr_merge<Median>({1,2},{3,4,5}), 3.0f);
    EXPECT_EQ(aggr_merge<Median>({0,1,2},{3,4}), 2.0f);
    EXPECT_TRUE(std::isnan(aggr_merge<Median>({1,2,my_nan,3},{4,5})));
    EXPECT_TRUE(std::isnan(aggr_merge<Median>({1,2,3},{4,my_nan,5})));
    EXPECT_EQ(aggr_merge<Min>({1,2},{3,4}), 1.0f);
}

GTEST_MAIN_RUN_ALL_TESTS()
