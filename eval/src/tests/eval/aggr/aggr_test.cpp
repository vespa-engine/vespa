// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/eval/eval/aggr.h>

using vespalib::Stash;
using namespace vespalib::eval;
using namespace vespalib::eval::aggr;

TEST("require that aggregator list returns appropriate entries") {
    auto list = Aggregator::list();
    ASSERT_EQUAL(list.size(), 7u);
    EXPECT_EQUAL(int(list[0]), int(Aggr::AVG));
    EXPECT_EQUAL(int(list[1]), int(Aggr::COUNT));
    EXPECT_EQUAL(int(list[2]), int(Aggr::PROD));
    EXPECT_EQUAL(int(list[3]), int(Aggr::SUM));
    EXPECT_EQUAL(int(list[4]), int(Aggr::MAX));
    EXPECT_EQUAL(int(list[5]), int(Aggr::MEDIAN));
    EXPECT_EQUAL(int(list[6]), int(Aggr::MIN));
}

TEST("require that aggr::is_simple works as expected") {
    EXPECT_FALSE(aggr::is_simple(Aggr::AVG));
    EXPECT_FALSE(aggr::is_simple(Aggr::COUNT));
    EXPECT_TRUE (aggr::is_simple(Aggr::PROD));
    EXPECT_TRUE (aggr::is_simple(Aggr::SUM));
    EXPECT_TRUE (aggr::is_simple(Aggr::MAX));
    EXPECT_FALSE(aggr::is_simple(Aggr::MEDIAN));
    EXPECT_TRUE (aggr::is_simple(Aggr::MIN));
}

TEST("require that aggr::is_ident works as expected") {
    EXPECT_TRUE (aggr::is_ident(Aggr::AVG));
    EXPECT_FALSE(aggr::is_ident(Aggr::COUNT));
    EXPECT_TRUE (aggr::is_ident(Aggr::PROD));
    EXPECT_TRUE (aggr::is_ident(Aggr::SUM));
    EXPECT_TRUE (aggr::is_ident(Aggr::MAX));
    EXPECT_TRUE (aggr::is_ident(Aggr::MEDIAN));
    EXPECT_TRUE (aggr::is_ident(Aggr::MIN));
}

TEST("require that aggr::is_complex works as expected") {
    EXPECT_FALSE(aggr::is_complex(Aggr::AVG));
    EXPECT_FALSE(aggr::is_complex(Aggr::COUNT));
    EXPECT_FALSE(aggr::is_complex(Aggr::PROD));
    EXPECT_FALSE(aggr::is_complex(Aggr::SUM));
    EXPECT_FALSE(aggr::is_complex(Aggr::MAX));
    EXPECT_TRUE (aggr::is_complex(Aggr::MEDIAN));
    EXPECT_FALSE(aggr::is_complex(Aggr::MIN));
}

TEST("require that AVG aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::AVG, stash);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 15.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 20.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 150.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::AVG);
}

TEST("require that COUNT aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::COUNT, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 1.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 2.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 3.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 1.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 2.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::COUNT);
}

TEST("require that PROD aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::PROD, stash);
    EXPECT_EQUAL(aggr.result(), 1.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 200.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 6000.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 20000.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::PROD);
}

TEST("require that Prod static API works as expected") {
    using Type = Prod<double>;
    EXPECT_EQUAL(Type::null_value(), 1.0);
    EXPECT_EQUAL(Type::combine(3,7), 21.0);
    EXPECT_EQUAL(Type::combine(5,4), 20.0);
}

TEST("require that SUM aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::SUM, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 30.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 60.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 300.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::SUM);
}

TEST("require that Sum static API works as expected") {
    using Type = Sum<double>;
    EXPECT_EQUAL(Type::null_value(), 0.0);
    EXPECT_EQUAL(Type::combine(3,7), 10.0);
    EXPECT_EQUAL(Type::combine(5,4), 9.0);
}

TEST("require that MAX aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MAX, stash);
    EXPECT_EQUAL(aggr.result(), -std::numeric_limits<double>::infinity());
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 20.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 30.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 200.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MAX);
}

TEST("require that Max static API works as expected") {
    using Type = Max<double>;
    EXPECT_EQUAL(Max<double>::null_value(), -std::numeric_limits<double>::infinity());
    EXPECT_EQUAL(Max<float>::null_value(), -std::numeric_limits<float>::infinity());
    EXPECT_EQUAL(Type::combine(3,7), 7.0);
    EXPECT_EQUAL(Type::combine(5,4), 5.0);
}

TEST("require that MEDIAN aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MEDIAN, stash);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 15.0);
    aggr.next(7.0),   EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(40.0),   EXPECT_EQUAL(aggr.result(), 15.0);
    aggr.next(16.0),   EXPECT_EQUAL(aggr.result(), 16.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 150.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MEDIAN);
}

TEST("require that MEDIAN aggregator handles NaN values") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MEDIAN, stash);
    double my_nan = std::numeric_limits<double>::quiet_NaN();
    aggr.first(10.0);
    EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(my_nan);
    EXPECT_TRUE(std::isnan(aggr.result()));
    aggr.next(20.0);
    EXPECT_TRUE(std::isnan(aggr.result()));
}

TEST("require that MIN aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MIN, stash);
    EXPECT_EQUAL(aggr.result(), std::numeric_limits<double>::infinity());
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 100.0);
    EXPECT_TRUE(aggr.enum_value() == Aggr::MIN);
}

TEST("require that Min static API works as expected") {
    using Type = Min<double>;
    EXPECT_EQUAL(Min<double>::null_value(), std::numeric_limits<double>::infinity());
    EXPECT_EQUAL(Min<float>::null_value(), std::numeric_limits<float>::infinity());
    EXPECT_EQUAL(Type::combine(3,7), 3.0);
    EXPECT_EQUAL(Type::combine(5,4), 4.0);
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

TEST("require that aggregator merge works") {
    float my_nan = std::numeric_limits<float>::quiet_NaN();
    EXPECT_EQUAL(aggr_merge<Avg>({1,2},{3,4}), 2.5);
    EXPECT_EQUAL(aggr_merge<Count>({1,2},{3,4}), 4.0);
    EXPECT_EQUAL(aggr_merge<Prod>({1,2},{3,4}), 24.0);
    EXPECT_EQUAL(aggr_merge<Sum>({1,2},{3,4}), 10.0);
    EXPECT_EQUAL(aggr_merge<Max>({1,2},{3,4}), 4.0);
    EXPECT_EQUAL(aggr_merge<Median>({1,2},{3,4}), 2.5);
    EXPECT_EQUAL(aggr_merge<Median>({1,2},{3,4,5}), 3);
    EXPECT_EQUAL(aggr_merge<Median>({0,1,2},{3,4}), 2);
    EXPECT_TRUE(std::isnan(aggr_merge<Median>({1,2,my_nan,3},{4,5})));
    EXPECT_TRUE(std::isnan(aggr_merge<Median>({1,2,3},{4,my_nan,5})));
    EXPECT_EQUAL(aggr_merge<Min>({1,2},{3,4}), 1.0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
