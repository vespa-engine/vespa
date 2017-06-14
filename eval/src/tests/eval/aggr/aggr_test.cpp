// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/eval/eval/aggr.h>

using vespalib::Stash;
using namespace vespalib::eval;

TEST("require that AVG aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::AVG, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 15.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 20.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 150.0);
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
}

TEST("require that PROD aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::PROD, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 200.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 6000.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 20000.0);
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
}

TEST("require that MAX aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MAX, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 20.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 30.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 200.0);
}

TEST("require that MIN aggregator works as expected") {
    Stash stash;
    Aggregator &aggr = Aggregator::create(Aggr::MIN, stash);
    EXPECT_EQUAL(aggr.result(), 0.0);
    aggr.first(10.0),  EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(20.0),   EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.next(30.0),   EXPECT_EQUAL(aggr.result(), 10.0);
    aggr.first(100.0), EXPECT_EQUAL(aggr.result(), 100.0);
    aggr.next(200.0),  EXPECT_EQUAL(aggr.result(), 100.0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
